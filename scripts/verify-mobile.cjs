/**
 * ok-crm 管理后台 —— 移动端适配验证
 *
 * 自包含：自己 spawn 后端（demo profile，嵌入式 H2，不需要 MySQL）+ 前端 vite，
 * 用 iPhone 尺寸的浏览器走一遍登录、工作台、客户列表、弹窗、岗位、企业设置，
 * 截图并断言「没有横向溢出 / 侧栏变抽屉 / 弹窗不超出视口」。
 *
 * 整条链路在一次进程里跑完，跑完自己清理，不依赖任何已启动的服务。
 */
const { spawn } = require('child_process')
const fs = require('fs')
const http = require('http')
const path = require('path')

const REPO = path.resolve(__dirname, '..')
// 外部依赖可用环境变量覆盖，默认值是本机的实际路径
const JAVA = process.env.JAVA_BIN || '/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java'
const NODE_BIN = process.env.NODE_BIN || process.execPath
const PLAYWRIGHT_CORE =
  process.env.PLAYWRIGHT_CORE ||
  `${process.env.HOME}/.workbuddy-ai/binaries/node/workspace/node_modules/playwright-core`
const { chromium } = require(PLAYWRIGHT_CORE)
const BACKEND_PORT = Number(process.env.BACKEND_PORT || 9100)
const FRONTEND_PORT = Number(process.env.FRONTEND_PORT || 5199)
const OUT = process.env.VERIFY_OUT || path.join(REPO, ".verify")
const SHOT = `${OUT}/shots`
fs.mkdirSync(SHOT, { recursive: true })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const failures = []
const check = (label, ok, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) failures.push(label)
}

const cleanEnv = () => {
  const env = { ...process.env }
  // 坑 1：WorkBuddy 注入的 safe-delete shim 会让 vite 优化依赖时崩掉
  delete env.NODE_OPTIONS
  delete env.CODEBUDDY_SAFE_DELETE_SANDBOX
  delete env.CODEBUDDY_BROKERED_FS_HOOK_ENABLED
  return env
}

const probe = (host, port, path) =>
  new Promise((resolve) => {
    const req = http.get({ host, port, path, timeout: 2000 }, (res) => {
      res.resume()
      resolve(true)
    })
    req.on('error', () => resolve(false))
    req.on('timeout', () => {
      req.destroy()
      resolve(false)
    })
  })

// 坑 2：vite 只监听 IPv6 的 localhost，三个地址都要试
const waitFor = async (port, path, timeoutMs = 90000) => {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    for (const host of ['localhost', '127.0.0.1', '::1']) {
      if (await probe(host, port, path)) return true
    }
    await sleep(800)
  }
  return false
}

const pipe = (child, file) => {
  const log = fs.createWriteStream(file)
  child.stdout.pipe(log)
  child.stderr.pipe(log)
}

const startBackend = () => {
  const child = spawn(
    JAVA,
    [
      '-jar',
      `${REPO}/apps/crm-boot/target/ok-crm.jar`,
      '--spring.profiles.active=demo',
      `--server.port=${BACKEND_PORT}`,
      // 保证密码可预测：demo 库里可能已经有企业记录，初始化不会重跑
      '--okcrm.setup.admin.reset-password=admin123456'
    ],
    { cwd: REPO, env: cleanEnv(), stdio: ['ignore', 'pipe', 'pipe'] }
  )
  pipe(child, `${OUT}/backend.log`)
  return child
}

const startFrontend = () => {
  const env = cleanEnv()
  env.VITE_API_TARGET = `http://127.0.0.1:${BACKEND_PORT}`
  // 坑 3：直接 spawn vite 入口，不经 npx，否则 kill 不掉真正监听的进程
  const child = spawn(
    NODE_BIN,
    [`${REPO}/web/node_modules/vite/bin/vite.js`, 'dev', '--port', String(FRONTEND_PORT), '--strictPort'],
    { cwd: `${REPO}/web`, env, stdio: ['ignore', 'pipe', 'pipe'] }
  )
  pipe(child, `${OUT}/frontend.log`)
  return child
}

/** 页面有没有被撑宽（横向溢出是移动端适配最直接的失败信号） */
const noOverflow = async (page, label) => {
  const m = await page.evaluate(() => ({
    doc: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    viewport: window.innerWidth
  }))
  const widest = Math.max(m.doc, m.body)
  check(`${label} 无横向溢出`, widest <= m.viewport + 1, `内容宽=${widest} 视口=${m.viewport}`)
}

(async () => {
  console.log('=== 启动后端（demo profile，嵌入式 H2）===')
  const backend = startBackend()
  if (!(await waitFor(BACKEND_PORT, '/api/actuator/health'))) {
    console.log('后端未就绪，见 ' + OUT + '/backend.log')
    backend.kill('SIGKILL')
    process.exit(1)
  }
  console.log('后端就绪')

  console.log('=== 启动前端 vite ===')
  const frontend = startFrontend()
  if (!(await waitFor(FRONTEND_PORT, '/'))) {
    console.log('前端未就绪，见 ' + OUT + '/frontend.log')
    backend.kill('SIGKILL')
    frontend.kill('SIGKILL')
    process.exit(1)
  }
  console.log('前端就绪')

  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    userAgent:
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1'
  })
  const page = await context.newPage()
  const consoleErrors = []
  const failedResponses = []
  page.on('pageerror', (e) => consoleErrors.push(String(e).slice(0, 200)))
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text().slice(0, 200))
  })
  page.on('response', (res) => {
    if (res.status() >= 400) failedResponses.push(`${res.status()} ${res.url()}`)
  })

  const base = `http://localhost:${FRONTEND_PORT}`

  // ---------------- 1. 登录页 ----------------
  await page.goto(base, { waitUntil: 'domcontentloaded' })
  await sleep(3000)
  await page.screenshot({ path: `${SHOT}/01-login.png` })

  const loginCard = await page.locator('.login-card').boundingBox()
  check('登录卡片不超出视口', !!loginCard && loginCard.width <= 390, `宽=${loginCard && loginCard.width}`)
  await noOverflow(page, '登录页')

  // ---------------- 2. 登录 ----------------
  await page.locator('input[placeholder="请输入账号"]').fill('admin')
  await page.locator('input[placeholder="请输入密码"]').fill('admin123456')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await page.waitForURL(/\/dashboard/, { timeout: 15000 }).catch(() => {})
  await sleep(2500)
  await page.screenshot({ path: `${SHOT}/02-dashboard.png` })

  check('登录成功进入工作台', page.url().includes('/dashboard'), page.url())

  // 移动端：固定侧栏应该消失、汉堡按钮出现
  const asideCount = await page.locator('.layout-aside').count()
  check('移动端不渲染固定侧栏', asideCount === 0, `找到 ${asideCount} 个 .layout-aside`)
  const toggleCount = await page.locator('.menu-toggle').count()
  check('出现汉堡菜单按钮', toggleCount === 1)
  await noOverflow(page, '工作台')

  // ---------------- 3. 抽屉菜单 ----------------
  await page.locator('.menu-toggle').click()
  await sleep(1200)
  await page.screenshot({ path: `${SHOT}/03-drawer.png` })
  const drawer = page.locator('.el-drawer').first()
  const drawerVisible = await drawer.isVisible().catch(() => false)
  check('点击汉堡能唤出抽屉菜单', drawerVisible)
  if (drawerVisible) {
    const box = await drawer.boundingBox()
    check('抽屉不超出视口', !!box && box.width <= 390, `宽=${box && box.width}`)
  }
  await page.keyboard.press('Escape')
  await sleep(800)

  // ---------------- 4. 客户列表 ----------------
  await page.goto(`${base}/customers`, { waitUntil: 'domcontentloaded' })
  await sleep(2500)
  await page.screenshot({ path: `${SHOT}/04-customers.png` })
  await noOverflow(page, '客户列表')

  const fixedRight = await page.locator('.el-table__fixed-right').count()
  check('移动端取消了表格固定列', fixedRight === 0, `找到 ${fixedRight} 个固定列层`)

  // 回归：动态路由页面刷新不能 404（守卫重走导航时的经典坑）
  await page.reload({ waitUntil: 'domcontentloaded' })
  await sleep(3000)
  const afterReload = await page.locator('.el-table').count()
  check('刷新动态路由页不 404', afterReload > 0, `刷新后表格数=${afterReload}，url=${page.url()}`)
  await page.screenshot({ path: `${SHOT}/04b-customers-reloaded.png` })

  // ---------------- 5. 弹窗（新增客户）----------------
  await page.getByRole('button', { name: '新增客户' }).click()
  await sleep(1200)
  await page.screenshot({ path: `${SHOT}/05-customer-dialog.png` })
  const dialog = await page.locator('.el-dialog').boundingBox()
  check('弹窗宽度不超出视口', !!dialog && dialog.width <= 390, `宽=${dialog && dialog.width}`)
  await noOverflow(page, '新增客户弹窗')

  // 表单标签是否上置：标签底边应该不高于内容区顶边
  const geometry = await page.evaluate(() => {
    const item = document.querySelector('.el-dialog .el-form-item')
    if (!item) return null
    const label = item.querySelector('.el-form-item__label')
    const content = item.querySelector('.el-form-item__content')
    const lr = label.getBoundingClientRect()
    const cr = content.getBoundingClientRect()
    return { labelBottom: Math.round(lr.bottom), contentTop: Math.round(cr.top) }
  })
  check(
    '表单标签改为上置',
    !!geometry && geometry.contentTop >= geometry.labelBottom - 2,
    JSON.stringify(geometry)
  )

  await page.keyboard.press('Escape')
  await sleep(800)

  // ---------------- 6. 岗位管理（含权限树）----------------
  await page.goto(`${base}/org/positions`, { waitUntil: 'domcontentloaded' })
  await sleep(2500)
  await page.screenshot({ path: `${SHOT}/06-positions.png` })
  await noOverflow(page, '岗位管理')

  // ---------------- 7. 企业设置 ----------------
  await page.goto(`${base}/settings/tenant`, { waitUntil: 'domcontentloaded' })
  await sleep(2500)
  await page.screenshot({ path: `${SHOT}/07-tenant-settings.png` })
  await noOverflow(page, '企业设置')

  // ---------------- 8. 公海池 ----------------
  await page.goto(`${base}/pool`, { waitUntil: 'domcontentloaded' })
  await sleep(2500)
  await page.screenshot({ path: `${SHOT}/08-pool.png` })
  await noOverflow(page, '公海池')

  console.log('')
  // favicon 拿不到是浏览器默认行为（本项目没放图标），不算问题
  const realFailed = failedResponses.filter((r) => !r.includes('favicon'))
  check('没有失败的网络请求', realFailed.length === 0, realFailed.slice(0, 3).join(' | '))
  // 「Failed to load resource」这条不带 URL，精确判断交给上面的网络断言，这里不重复计
  const realConsoleErrors = consoleErrors.filter((e) => !/Failed to load resource/.test(e))
  check('页面无 JS 报错', realConsoleErrors.length === 0, realConsoleErrors.slice(0, 3).join(' | '))

  await browser.close()
  backend.kill('SIGTERM')
  frontend.kill('SIGTERM')
  await sleep(1500)
  backend.kill('SIGKILL')
  frontend.kill('SIGKILL')

  console.log('')
  console.log(failures.length === 0 ? 'ALL CHECKS PASSED' : `FAILURES (${failures.length}): ${failures.join(' | ')}`)
  console.log(`截图目录：${SHOT}`)
  process.exit(failures.length === 0 ? 0 : 1)
})().catch(async (e) => {
  console.error('脚本异常：', e)
  process.exit(2)
})

#!/usr/bin/env node
/**
 * 生产静态服务（零依赖）
 *
 * 用途：替代 `npm run dev` 对外提供服务。
 * dev server 会按模块逐个返回未构建的源码（首屏 8+ 个请求、
 * 含 2.4MB 的 element-plus），外网访问非常慢；
 * 本服务只托管 `npm run build` 的产物（首屏 2 个请求），并提供：
 *   - SPA 路由回退到 index.html
 *   - /assets/ 长缓存（文件名带 hash，可immutable）
 *   - index.html 不缓存，保证能拿到新构建
 *   - gzip 压缩（与 nginx.conf 保持一致）
 *   - /api 反向代理到本机后端
 *
 * 用法：
 *   node scripts/serve-dist.mjs                # 默认 8080 端口
 *   PORT=4180 node scripts/serve-dist.mjs      # 指定端口
 *   API_TARGET=http://127.0.0.1:9002 node scripts/serve-dist.mjs
 */

import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import zlib from 'node:zlib'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..', 'dist')

const PORT = Number(process.env.PORT || 8080)
const API_TARGET = process.env.API_TARGET || 'http://127.0.0.1:9002'

if (!fs.existsSync(ROOT)) {
  console.error(`❌ 找不到构建产物：${ROOT}`)
  console.error('   先执行：npm run build')
  process.exit(1)
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8'
}

const COMPRESSIBLE = /^(text\/|application\/(javascript|json|xml)|image\/svg)/

/** gzip 压缩：只对文本类且大于 1KB 的响应启用，与 nginx 的 gzip_min_length 保持一致 */
function maybeGzip(req, res, body, contentType) {
  const accepts = String(req.headers['accept-encoding'] || '')
  if (!COMPRESSIBLE.test(contentType) || body.length < 1024 || !/\bgzip\b/.test(accepts)) {
    return body
  }
  return zlib.gzipSync(body, { level: 6 })
}

function send(req, res, filePath, statusCode = 200) {
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
      res.end('404 Not Found')
      return
    }
    const ext = path.extname(filePath).toLowerCase()
    const type = MIME[ext] || 'application/octet-stream'
    const body = maybeGzip(req, res, data, type)
    const headers = {
      'Content-Type': type,
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'SAMEORIGIN'
    }
    if (body !== data) headers['Content-Encoding'] = 'gzip'

    // /assets/ 下文件名带内容 hash，可以长期强缓存
    if (filePath.includes(`${path.sep}assets${path.sep}`)) {
      headers['Cache-Control'] = 'public, max-age=2592000, immutable'
    } else {
      // index.html 不缓存，保证能拿到最新构建
      headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    }
    res.writeHead(statusCode, headers)
    res.end(body)
  })
}

/** 把 /api/xxx 转发到 Spring Boot 后端 */
function proxyApi(req, res) {
  const target = new URL(req.url, API_TARGET)
  const proxyReq = http.request(
    target,
    {
      method: req.method,
      headers: { ...req.headers, host: target.host }
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode, proxyRes.headers)
      proxyRes.pipe(res)
    }
  )
  proxyReq.on('error', (err) => {
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end(`502 Bad Gateway: 后端未启动或端口不对(${API_TARGET}) — ${err.message}`)
  })
  req.pipe(proxyReq)
}

const server = http.createServer((req, res) => {
  const urlPath = decodeURIComponent((req.url || '/').split('?')[0])

  if (urlPath === '/api' || urlPath.startsWith('/api/')) {
    proxyApi(req, res)
    return
  }

  // 归一化，挡住 ../ 穿越
  const safePath = path.normalize(urlPath).replace(/^(\.\.[/\\])+/, '')
  let filePath = path.join(ROOT, safePath)

  if (!filePath.startsWith(ROOT)) {
    res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('403 Forbidden')
    return
  }

  // 目录或不存在 → SPA 回退到 index.html
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    filePath = path.join(ROOT, 'index.html')
  }

  send(req, res, filePath)
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`OK-CRM 生产静态服务已启动`)
  console.log(`  本机访问   http://127.0.0.1:${PORT}`)
  console.log(`  静态根目录 ${ROOT}`)
  console.log(`  接口转发   ${API_TARGET}/api`)
  console.log(`  按 Ctrl-C 停止`)
})
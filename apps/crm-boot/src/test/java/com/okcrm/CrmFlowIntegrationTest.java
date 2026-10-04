package com.okcrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端主链路测试。
 *
 * <p>走真实的 HTTP 栈（{@code RANDOM_PORT} + TestRestTemplate），
 * 而不是直接调 Service，这样能一并验证：</p>
 * <ul>
 *   <li>多租户隔离是否真的生效</li>
 *   <li>JWT 认证与 {@code @PreAuthorize} 权限校验</li>
 *   <li>模块授权闸门（未购买模块应返回 1401 而不是 404）</li>
 *   <li>Flyway 建表脚本在 H2(MySQL 模式) 上能否跑通</li>
 * </ul>
 *
 * <p>依赖 H2 内存库，不需要 MySQL / Redis，CI 里可直接跑。</p>
 *
 * <p><b>注意路径写法</b>：{@code TestRestTemplate} 会自动把
 * {@code server.servlet.context-path}（本项目是 {@code /api}）拼到根地址上，
 * 因此这里的 URL <b>不要再写 /api 前缀</b>，否则会变成 {@code /api/api/...}，
 * 表现为「接口 404 或莫名其妙 401」，很难排查。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrmFlowIntegrationTest {

    private static final String TENANT_CODE = "testco";
    private static final String ADMIN_PASSWORD = "admin123456";
    private static final String SALES_PASSWORD = "sales123456";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 跨用例传递的状态
    private static String adminToken;
    private static String salesToken;
    private static Long customerId;
    private static Long salesEmployeeId;

    // ============================================================ 主链路

    @Test
    @Order(1)
    @DisplayName("平台超管登录成功")
    void platform_admin_can_login() throws Exception {
        JsonNode body = post("/auth/platform-login",
                Map.of("username", "admin", "password", ADMIN_PASSWORD), null);
        assertOk(body);
        assertThat(body.at("/data/user/platformAdmin").asBoolean()).isTrue();
        assertThat(body.at("/data/token").asText()).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("开通租户：同时初始化默认岗位与默认管理员")
    void create_tenant() throws Exception {
        String platformToken = post("/auth/platform-login",
                Map.of("username", "admin", "password", ADMIN_PASSWORD), null)
                .at("/data/token").asText();

        JsonNode body = post("/platform/tenants", Map.of(
                "code", TENANT_CODE,
                "name", "测试企业",
                "region", "CN-HUBEI",
                "regionName", "湖北"
        ), platformToken);

        assertOk(body);
        assertThat(body.at("/data/code").asText()).isEqualTo(TENANT_CODE);
    }

    @Test
    @Order(3)
    @DisplayName("租户管理员登录，且拿到全部权限与已购模块")
    void tenant_admin_can_login() throws Exception {
        JsonNode body = login(TENANT_CODE, "admin", ADMIN_PASSWORD);
        adminToken = body.at("/data/token").asText();

        assertThat(body.at("/data/permissions").size()).isGreaterThan(10);
        assertThat(toList(body.at("/data/modules"))).contains("tenant", "iam", "customer", "pool");
    }

    @Test
    @Order(4)
    @DisplayName("租户开通后自动生成三个默认岗位，且权限已分配")
    void default_positions_created_with_permissions() throws Exception {
        JsonNode body = get("/positions", adminToken);
        assertOk(body);

        Map<String, JsonNode> byCode = new HashMap<>();
        body.at("/data").forEach(node -> byCode.put(node.at("/code").asText(), node));

        assertThat(byCode).containsKeys("admin", "sales_manager", "sales");
        // 管理员拥有全部权限
        assertThat(byCode.get("admin").at("/permissions").size()).isGreaterThan(20);
        // 销售只有客户与公海的基础权限，不应含分配/删除
        List<String> salesPermissions = toList(byCode.get("sales").at("/permissions"));
        assertThat(salesPermissions).contains("customer:list", "pool:claim");
        assertThat(salesPermissions).doesNotContain("customer:delete", "pool:assign", "iam:employee:create");
    }

    @Test
    @Order(5)
    @DisplayName("新增员工并关联到「销售」岗位")
    void create_employee_bound_to_position() throws Exception {
        Long salesPositionId = findPositionId("sales");

        JsonNode body = post("/employees", Map.of(
                "username", "sales1",
                "password", SALES_PASSWORD,
                "realName", "销售一号",
                "positionIds", List.of(salesPositionId)
        ), adminToken);

        assertOk(body);
        salesEmployeeId = body.at("/data/id").asLong();
        assertThat(toList(body.at("/data/positionNames"))).contains("销售");

        // 销售账号可以登录
        JsonNode loginBody = login(TENANT_CODE, "sales1", SALES_PASSWORD);
        salesToken = loginBody.at("/data/token").asText();
        assertThat(salesToken).isNotBlank();
    }

    @Test
    @Order(6)
    @DisplayName("新增客户时不指定负责人 → 直接进入公海池")
    void create_customer_without_owner_goes_to_pool() throws Exception {
        JsonNode body = post("/customers", Map.of(
                "name", "武汉某某科技有限公司",
                "industry", "软件",
                "level", "A",
                "phone", "027-88886666"
        ), salesToken);

        assertOk(body);
        customerId = body.at("/data/id").asLong();
        assertThat(body.at("/data/inPool").asBoolean()).isTrue();
        // 注意：Jackson 配置了 non_null 忽略空字段，所以 ownerId 是「缺失」而不是 null
        JsonNode ownerId = body.at("/data/ownerId");
        assertThat(ownerId.isMissingNode() || ownerId.isNull()).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("公海池能看到该客户，且领取后归属正确、流转日志留痕")
    void claim_from_pool_records_log() throws Exception {
        // 公海列表可见
        JsonNode pool = get("/pool/customers?pageSize=50", salesToken);
        assertOk(pool);
        assertThat(idsOf(pool.at("/data/records"))).contains(customerId);

        // 领取
        assertOk(post("/pool/customers/" + customerId + "/claim", null, salesToken));

        // 归属变成自己
        JsonNode detail = get("/customers/" + customerId, salesToken);
        assertOk(detail);
        assertThat(detail.at("/data/inPool").asBoolean()).isFalse();
        assertThat(detail.at("/data/ownerId").asLong()).isEqualTo(salesEmployeeId);
        assertThat(detail.at("/data/ownerName").asText()).isEqualTo("销售一号");

        // 流转日志
        JsonNode logs = get("/pool/logs?customerId=" + customerId, salesToken);
        assertOk(logs);
        List<String> actions = actionsOf(logs.at("/data/records"));
        assertThat(actions).contains("CLAIM");
    }

    @Test
    @Order(8)
    @DisplayName("已被领走的客户不能重复领取")
    void cannot_claim_twice() throws Exception {
        JsonNode body = post("/pool/customers/" + customerId + "/claim", null, salesToken);
        assertThat(body.get("code").asInt()).isEqualTo(1302);
    }

    @Test
    @Order(9)
    @DisplayName("超期未跟进自动回收：按创建时间口径把客户打回公海并记 RECYCLE 日志")
    void auto_recycle_returns_customer_to_pool() throws Exception {
        // 1. 配置回收规则：启用、1 天、按创建时间
        assertOk(put("/pool/settings", Map.of(
                "enabled", true,
                "days", 1,
                "basis", "CREATE_TIME",
                "claimLimit", 0
        ), adminToken));

        // 2. 把客户创建时间回拨 10 天，模拟「很久没人管」
        jdbcTemplate.update("UPDATE crm_customer SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(10)), customerId);

        // 3. 手工触发回收（不必等凌晨定时任务）
        JsonNode recycle = post("/pool/recycle/run", null, adminToken);
        assertOk(recycle);
        assertThat(recycle.get("data").asInt()).isGreaterThanOrEqualTo(1);

        // 4. 客户回到公海
        JsonNode detail = get("/customers/" + customerId, adminToken);
        assertOk(detail);
        assertThat(detail.at("/data/inPool").asBoolean()).isTrue();
        assertThat(detail.at("/data/poolReason").asText()).contains("未跟进");

        // 5. 日志里有 RECYCLE
        JsonNode logs = get("/pool/logs?customerId=" + customerId, adminToken);
        assertThat(actionsOf(logs.at("/data/records"))).contains("RECYCLE");
    }

    @Test
    @Order(10)
    @DisplayName("数据权限：销售（SELF 范围）看不到别人负责的客户")
    void sales_cannot_see_others_customer() throws Exception {
        Long adminEmployeeId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_employee WHERE username = 'admin'", Long.class);

        // 管理员建一个客户并直接指派给自己
        JsonNode created = post("/customers", Map.of(
                "name", "另一个客户-归属管理员",
                "ownerId", adminEmployeeId
        ), adminToken);
        assertOk(created);
        Long otherCustomerId = created.at("/data/id").asLong();

        // 销售直接访问该客户详情应被拒绝
        JsonNode detail = get("/customers/" + otherCustomerId, salesToken);
        assertThat(detail.get("code").asInt()).isEqualTo(1105);

        // 销售的客户列表里也不应出现它
        JsonNode list = get("/customers?pageSize=50", salesToken);
        assertOk(list);
        assertThat(idsOf(list.at("/data/records"))).doesNotContain(otherCustomerId);
    }

    @Test
    @Order(11)
    @DisplayName("模块授权闸门：撤销公海池模块后，该租户调用相关接口被明确拒绝")
    void unlicensed_module_is_blocked() throws Exception {
        String platformToken = post("/auth/platform-login",
                Map.of("username", "admin", "password", ADMIN_PASSWORD), null)
                .at("/data/token").asText();

        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_tenant WHERE code = ?", Long.class, TENANT_CODE);

        // 撤销公海池模块
        assertOk(delete("/platform/modules/tenant/" + tenantId + "/pool", platformToken));

        // 确认授权台账确实被置为停用（status=0）
        Integer moduleStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM sys_tenant_module WHERE tenant_id = ? AND module_key = 'pool'",
                Integer.class, tenantId);
        System.out.println(">>> pool 模块授权状态(期望 0) = " + moduleStatus);
        assertThat(moduleStatus).isEqualTo(0);

        // 租户管理员再调公海池接口 → 明确提示「未购买该模块」，而不是 404
        JsonNode blocked = get("/pool/customers", adminToken);
        assertThat(blocked.get("code").asInt()).isEqualTo(1401);

        // 客户模块不受影响
        assertOk(get("/customers?pageSize=5", adminToken));
    }

    @Test
    @Order(12)
    @DisplayName("岗位权限闸门：销售没有员工管理权限，接口返回 403")
    void sales_without_permission_is_forbidden() {
        int salesStatus = statusOf(HttpMethod.GET, "/employees", salesToken);
        int adminStatus = statusOf(HttpMethod.GET, "/employees", adminToken);
        int platformApiByTenantAdmin = statusOf(HttpMethod.GET, "/platform/tenants", adminToken);
        System.out.println(">>> 权限闸门: 销售访问员工列表=" + salesStatus
                + ", 管理员访问员工列表=" + adminStatus
                + ", 租户管理员访问平台超管接口=" + platformApiByTenantAdmin);

        // 销售岗位不含 iam:employee:list，方法级 @PreAuthorize 应拦住
        assertThat(salesStatus).as("销售不应能访问员工管理接口").isEqualTo(403);
        // 管理员有权限，正常放行
        assertThat(adminStatus).as("管理员应能访问员工管理接口").isEqualTo(200);
        // 租户管理员不是平台超管，不能访问跨租户管理接口
        assertThat(platformApiByTenantAdmin).as("租户管理员不应能访问平台超管接口").isEqualTo(403);
    }

    // ============================================================ 工具方法

    /** 只取 HTTP 状态码，用于断言 403 这类「被安全层拦住」的场景 */
    private int statusOf(HttpMethod method, String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(headers), String.class)
                .getStatusCode().value();
    }

    private JsonNode login(String tenantCode, String username, String password) throws Exception {
        JsonNode body = post("/auth/login", Map.of(
                "tenantCode", tenantCode,
                "username", username,
                "password", password
        ), null);
        assertOk(body);
        return body;
    }

    private Long findPositionId(String code) throws Exception {
        JsonNode body = get("/positions", adminToken);
        for (JsonNode node : body.at("/data")) {
            if (code.equals(node.at("/code").asText())) {
                return node.at("/id").asLong();
            }
        }
        throw new IllegalStateException("找不到岗位: " + code);
    }

    private JsonNode get(String path, String token) throws Exception {
        return exchange(HttpMethod.GET, path, null, token);
    }

    private JsonNode post(String path, Object body, String token) throws Exception {
        return exchange(HttpMethod.POST, path, body, token);
    }

    private JsonNode put(String path, Object body, String token) throws Exception {
        return exchange(HttpMethod.PUT, path, body, token);
    }

    private JsonNode delete(String path, String token) throws Exception {
        return exchange(HttpMethod.DELETE, path, null, token);
    }

    private JsonNode exchange(HttpMethod method, String path, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String payload = body == null ? null : objectMapper.writeValueAsString(body);
        ResponseEntity<String> response = rest.exchange(path, method, new HttpEntity<>(payload, headers), String.class);

        System.out.println(">>> " + method + " " + path
                + " status=" + response.getStatusCode()
                + " body=" + response.getBody());

        assertThat(response.getBody()).as("响应体不应为空: %s", path).isNotNull();
        return objectMapper.readTree(response.getBody());
    }

    private static void assertOk(JsonNode body) {
        assertThat(body.get("code").asInt())
                .as("接口返回失败: %s", body)
                .isZero();
    }

    private static List<Long> idsOf(JsonNode array) {
        List<Long> result = new java.util.ArrayList<>();
        array.forEach(node -> result.add(node.at("/id").asLong()));
        return result;
    }

    private static List<String> actionsOf(JsonNode array) {
        List<String> result = new java.util.ArrayList<>();
        array.forEach(node -> result.add(node.at("/action").asText()));
        return result;
    }

    private static List<String> toList(JsonNode array) {
        List<String> result = new java.util.ArrayList<>();
        array.forEach(node -> result.add(node.asText()));
        return result;
    }
}

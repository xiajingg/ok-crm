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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端主链路测试（单企业私有化部署）。
 *
 * <p>走真实的 HTTP 栈（{@code RANDOM_PORT} + TestRestTemplate），而不是直接调 Service，
 * 这样能一并验证：部署初始化、租户隔离、JWT 认证、{@code @PreAuthorize} 权限、
 * 数据权限、模块闸门。</p>
 *
 * <p>依赖 H2 内存库，不需要 MySQL / Redis。
 * 企业信息、默认岗位与管理员账号由 {@code DeploymentSetupRunner} 在启动时自动创建，
 * 因此用例里不再需要「开通租户」这一步。</p>
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

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123456";
    private static final String SALES_USERNAME = "sales1";
    private static final String SALES_PASSWORD = "sales123456";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String adminToken;
    private static String salesToken;
    private static Long customerId;
    private static Long salesEmployeeId;

    // ============================================================ 部署初始化

    @Test
    @Order(1)
    @DisplayName("部署初始化：应用启动时自动创建企业、默认岗位与管理员账号")
    void deployment_initialized_on_startup() {
        Long tenantCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_tenant", Long.class);
        assertThat(tenantCount).isEqualTo(1);

        Long positionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_position", Long.class);
        assertThat(positionCount).isEqualTo(3);

        Long adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_employee WHERE username = ?", Long.class, ADMIN_USERNAME);
        assertThat(adminCount).isEqualTo(1);

        // 默认把 sys_module 里已注册的模块全部授权给本部署
        Long licensedModuleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_tenant_module WHERE status = 1", Long.class);
        assertThat(licensedModuleCount).isEqualTo(4);
    }

    @Test
    @Order(2)
    @DisplayName("登录：只需账号密码，不需要选择企业")
    void login_without_tenant_code() throws Exception {
        JsonNode body = post("/auth/login", Map.of(
                "username", ADMIN_USERNAME,
                "password", ADMIN_PASSWORD));
        assertOk(body);

        adminToken = body.at("/data/token").asText();
        assertThat(adminToken).isNotBlank();
        assertThat(body.at("/data/tenant/code").asText()).isEqualTo("testco");
        assertThat(body.at("/data/permissions").size()).isGreaterThan(10);
        assertThat(toList(body.at("/data/modules"))).contains("tenant", "iam", "customer", "pool");
    }

    @Test
    @Order(3)
    @DisplayName("登录失败：密码错误返回 1101")
    void login_fails_with_wrong_password() throws Exception {
        JsonNode body = post("/auth/login", Map.of(
                "username", ADMIN_USERNAME,
                "password", "definitely-wrong"));
        assertThat(body.get("code").asInt()).isEqualTo(1101);
    }

    // ============================================================ 组织与权限

    @Test
    @Order(4)
    @DisplayName("默认岗位已创建且权限已分配")
    void default_positions_created_with_permissions() throws Exception {
        JsonNode body = get("/positions", adminToken);
        assertOk(body);

        Map<String, JsonNode> byCode = new java.util.HashMap<>();
        body.at("/data").forEach(node -> byCode.put(node.at("/code").asText(), node));

        assertThat(byCode).containsKeys("admin", "sales_manager", "sales");
        // 管理员拥有全部权限
        assertThat(byCode.get("admin").at("/permissions").size()).isGreaterThan(20);
        // 销售只有客户与公海的基础权限，不应含删除与指派
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
                "username", SALES_USERNAME,
                "password", SALES_PASSWORD,
                "realName", "销售一号",
                "positionIds", List.of(salesPositionId)
        ), adminToken);

        assertOk(body);
        salesEmployeeId = body.at("/data/id").asLong();
        assertThat(toList(body.at("/data/positionNames"))).contains("销售");

        // 销售账号可以登录
        JsonNode loginBody = post("/auth/login", Map.of(
                "username", SALES_USERNAME,
                "password", SALES_PASSWORD));
        assertOk(loginBody);
        salesToken = loginBody.at("/data/token").asText();
        assertThat(salesToken).isNotBlank();
    }

    // ============================================================ 客户与公海池

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
    @DisplayName("公海可见、可领取，领取后归属正确且流转日志留痕")
    void claim_from_pool_records_log() throws Exception {
        JsonNode pool = get("/pool/customers?pageSize=50", salesToken);
        assertOk(pool);
        assertThat(idsOf(pool.at("/data/records"))).contains(customerId);

        assertOk(post("/pool/customers/" + customerId + "/claim", null, salesToken));

        JsonNode detail = get("/customers/" + customerId, salesToken);
        assertOk(detail);
        assertThat(detail.at("/data/inPool").asBoolean()).isFalse();
        assertThat(detail.at("/data/ownerId").asLong()).isEqualTo(salesEmployeeId);
        assertThat(detail.at("/data/ownerName").asText()).isEqualTo("销售一号");

        JsonNode logs = get("/pool/logs?customerId=" + customerId, salesToken);
        assertOk(logs);
        assertThat(actionsOf(logs.at("/data/records"))).contains("CLAIM");
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
    @DisplayName("超期未跟进自动回收：按创建时间口径把客户打回公海并记 RECYCLE")
    void auto_recycle_returns_customer_to_pool() throws Exception {
        assertOk(put("/pool/settings", Map.of(
                "enabled", true,
                "days", 1,
                "basis", "CREATE_TIME",
                "claimLimit", 0
        ), adminToken));

        // 把客户创建时间回拨 10 天，模拟「很久没人管」
        jdbcTemplate.update("UPDATE crm_customer SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(10)), customerId);

        JsonNode recycle = post("/pool/recycle/run", null, adminToken);
        assertOk(recycle);
        assertThat(recycle.get("data").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode detail = get("/customers/" + customerId, adminToken);
        assertOk(detail);
        assertThat(detail.at("/data/inPool").asBoolean()).isTrue();
        assertThat(detail.at("/data/poolReason").asText()).contains("未跟进");

        JsonNode logs = get("/pool/logs?customerId=" + customerId, adminToken);
        assertThat(actionsOf(logs.at("/data/records"))).contains("RECYCLE");
    }

    // ============================================================ 权限闸门

    @Test
    @Order(10)
    @DisplayName("数据权限：销售（仅本人）看不到别人负责的客户")
    void sales_cannot_see_others_customer() throws Exception {
        Long adminEmployeeId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_employee WHERE username = ?", Long.class, ADMIN_USERNAME);

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
    @DisplayName("岗位权限闸门：销售没有员工管理权限，接口返回 403")
    void sales_without_permission_is_forbidden() {
        assertThat(statusOf(HttpMethod.GET, "/employees", salesToken))
                .as("销售不应能访问员工管理接口").isEqualTo(403);
        assertThat(statusOf(HttpMethod.GET, "/employees", adminToken))
                .as("管理员应能访问员工管理接口").isEqualTo(200);
    }

    @Test
    @Order(12)
    @DisplayName("多租户隔离仍在生效：跨租户查询不会串数据")
    void tenant_isolation_still_effective() throws Exception {
        // 插入一个「另一家企业」的客户，验证本租户的查询不会看到它
        jdbcTemplate.update(
                "INSERT INTO crm_customer (id, tenant_id, name, industry, level, created_at, deleted) "
                        + "VALUES (999001, 999, '别家的客户', '其他', 'C', CURRENT_TIMESTAMP, 0)");

        JsonNode list = get("/customers?pageSize=100&keyword=别家", adminToken);
        assertOk(list);
        assertThat(idsOf(list.at("/data/records"))).doesNotContain(999001L);

        jdbcTemplate.update("DELETE FROM crm_customer WHERE id = 999001");
    }

    @Test
    @Order(13)
    @DisplayName("企业设置：可以修改企业名称等基本信息")
    void enterprise_info_can_be_updated() throws Exception {
        JsonNode current = get("/tenants/current", adminToken);
        assertOk(current);
        assertThat(current.at("/data/code").asText()).isEqualTo("testco");

        JsonNode updated = put("/tenants/current", Map.of(
                "name", "测试企业（已改名）",
                "region", "CN-GUANGDONG",
                "regionName", "广东"
        ), adminToken);
        assertOk(updated);
        assertThat(updated.at("/data/name").asText()).isEqualTo("测试企业（已改名）");
        assertThat(updated.at("/data/regionName").asText()).isEqualTo("广东");
    }

    // ============================================================ 工具方法

    private JsonNode get(String path, String token) throws Exception {
        return exchange(HttpMethod.GET, path, null, token);
    }

    private JsonNode post(String path, Object body) throws Exception {
        return exchange(HttpMethod.POST, path, body, null);
    }

    private JsonNode post(String path, Object body, String token) throws Exception {
        return exchange(HttpMethod.POST, path, body, token);
    }

    private JsonNode put(String path, Object body, String token) throws Exception {
        return exchange(HttpMethod.PUT, path, body, token);
    }

    private JsonNode exchange(HttpMethod method, String path, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String payload = body == null ? null : objectMapper.writeValueAsString(body);
        ResponseEntity<String> response = rest.exchange(path, method, new HttpEntity<>(payload, headers), String.class);

        assertThat(response.getBody()).as("响应体不应为空: %s", path).isNotNull();
        return objectMapper.readTree(response.getBody());
    }

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

    private Long findPositionId(String code) throws Exception {
        JsonNode body = get("/positions", adminToken);
        for (JsonNode node : body.at("/data")) {
            if (code.equals(node.at("/code").asText())) {
                return node.at("/id").asLong();
            }
        }
        throw new IllegalStateException("找不到岗位: " + code);
    }

    private static void assertOk(JsonNode body) {
        assertThat(body.get("code").asInt())
                .as("接口返回失败: %s", body)
                .isZero();
    }

    private static List<Long> idsOf(JsonNode array) {
        List<Long> result = new ArrayList<>();
        array.forEach(node -> result.add(node.at("/id").asLong()));
        return result;
    }

    private static List<String> actionsOf(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(node -> result.add(node.at("/action").asText()));
        return result;
    }

    private static List<String> toList(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(node -> result.add(node.asText()));
        return result;
    }
}

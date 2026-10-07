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
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON 契约测试：<b>雪花 ID 必须以字符串下发</b>。
 *
 * <p>为什么单独有这个测试类：
 * {@link CrmFlowIntegrationTest} 是从 Java 直接调 HTTP 接口的，Java 里 {@code Long}
 * 就是 {@code Long}，**根本不经过「JSON → JavaScript」这一层**。所以哪怕后端把
 * 19 位 ID 序列化成 JSON 数字，那边 22 个用例照样全绿 —— 而真实浏览器里
 * {@code JSON.parse} 早就把 ID 的最后几位改掉了，前端再把改坏的 ID 发回来，
 * 后端按主键查不到，报出「存在无效的岗位，请刷新后重试」这种毫无道理的错。
 *
 * <p>本类专门盯住这条契约，断言三件事：
 * <ol>
 *   <li>实体 ID 在原始 JSON 里是<b>字符串</b>（不是数字）</li>
 *   <li>分页的 {@code total / pageNum / pageSize} 仍然是<b>数字</b>（别把分页搞坏）</li>
 *   <li>把接口原样返回的 ID <b>当作字符串</b>发回去，能正常建员工 / 建客户 —— 复现真实链路</li>
 * </ol>
 *
 * <p>路径写法同 {@link CrmFlowIntegrationTest}：{@code TestRestTemplate} 会自动带上
 * {@code server.servlet.context-path}（{@code /api}），所以这里不要写 /api 前缀。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnowflakeIdJsonContractTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123456";

    /** JavaScript 能精确表示的最大整数：2^53 - 1 */
    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private static String adminToken;
    private static String positionIdAsText;

    @Test
    @Order(1)
    @DisplayName("准备：管理员登录")
    void login() throws Exception {
        JsonNode body = post("/auth/login", Map.of(
                "username", ADMIN_USERNAME,
                "password", ADMIN_PASSWORD), null);
        assertOk(body);
        adminToken = body.at("/data/token").asText();
        assertThat(adminToken).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("岗位列表：id 是字符串，sortOrder 仍是数字")
    void position_ids_are_serialized_as_string() throws Exception {
        JsonNode body = get("/positions", adminToken);
        assertOk(body);

        JsonNode first = body.at("/data/0");
        assertThat(first.at("/id").isTextual())
                .as("岗位 id 必须是 JSON 字符串，否则浏览器 JSON.parse 会丢精度；实际是: %s",
                        first.at("/id"))
                .isTrue();

        // 顺带确认没把普通数值一起转成字符串（否则分页、排序都会受影响）
        assertThat(first.at("/sortOrder").isNumber())
                .as("sortOrder 是普通数值，不应被转成字符串")
                .isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("分页接口：records[].id 是字符串，total / pageNum / pageSize 仍是数字")
    void pagination_keeps_numbers_and_ids_as_strings() throws Exception {
        JsonNode body = get("/employees?pageNum=1&pageSize=10", adminToken);
        assertOk(body);

        JsonNode page = body.at("/data");
        assertThat(page.at("/records/0/id").isTextual())
                .as("员工 id 必须是 JSON 字符串；实际是: %s", page.at("/records/0/id"))
                .isTrue();
        assertThat(page.at("/total").isNumber())
                .as("分页 total 必须保持数字，否则前端分页组件会出问题")
                .isTrue();
        assertThat(page.at("/pageNum").isNumber()).isTrue();
        assertThat(page.at("/pageSize").isNumber()).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("真实链路：用接口原样返回的岗位 ID（字符串）新增员工")
    void create_employee_with_string_position_id() throws Exception {
        JsonNode positions = get("/positions", adminToken);
        JsonNode salesPosition = null;
        for (JsonNode node : positions.at("/data")) {
            if ("sales".equals(node.at("/code").asText())) {
                salesPosition = node;
            }
        }
        assertThat(salesPosition).as("找不到默认的 sales 岗位").isNotNull();

        // 关键：模拟**浏览器**把 JSON 里的 ID 再发回来的过程。
        // 字符串 → 原样回传（精确）；数字 → 经过 JS 的 Number，会丢精度。
        // 这一步是 Java 自己复现不出来的（Java 里 Long 就是 Long），必须显式模拟。
        positionIdAsText = simulateBrowserRoundTrip(salesPosition.at("/id"));
        assertThat(Long.parseLong(positionIdAsText))
                .as("雪花 ID 必须超过 JS 安全整数上限，否则这个测试就失去意义了")
                .isGreaterThan(JS_MAX_SAFE_INTEGER);

        JsonNode created = post("/employees", Map.of(
                "username", "idprobe_sales",
                "password", "probe123456",
                "realName", "ID 契约探针",
                "status", 1,
                // 故意用字符串数组，模拟前端 JSON.stringify 的结果
                "positionIds", new String[]{positionIdAsText}
        ), adminToken);

        // 修复前这里会失败并报「存在无效的岗位，请刷新后重试」—— 就是用户在页面上看到的那个错
        assertOk(created);
        assertThat(toTextList(created.at("/data/positionNames"))).contains("销售");
        assertThat(created.at("/data/id").isTextual())
                .as("新建实体的 id 也必须是字符串")
                .isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("真实链路：用接口原样返回的员工 ID（字符串）新增客户并指定归属")
    void create_customer_with_string_owner_id() throws Exception {
        JsonNode employees = get("/employees?keyword=idprobe_sales&pageNum=1&pageSize=10", adminToken);
        assertOk(employees);
        JsonNode target = employees.at("/data/records/0");
        assertThat(target.isMissingNode()).as("上一步创建的员工应该能查到").isFalse();

        String ownerIdAsText = simulateBrowserRoundTrip(target.at("/id"));
        assertThat(ownerIdAsText).isNotBlank();

        JsonNode created = post("/customers", Map.of(
                "name", "ID 契约测试客户",
                "industry", "软件",
                "ownerId", ownerIdAsText
        ), adminToken);

        assertOk(created);
        assertThat(created.at("/data/inPool").asBoolean())
                .as("指定了负责人就不该进公海池")
                .isFalse();
        // 归属必须真的是那个员工（说明字符串 ID 被后端正确反序列化成了 Long）
        assertThat(created.at("/data/ownerId").asText()).isEqualTo(ownerIdAsText);
        assertThat(created.at("/data/ownerName").asText()).isEqualTo("ID 契约探针");
    }

    @Test
    @Order(6)
    @DisplayName("反向证明：雪花 ID 若按 JSON 数字下发，JS 解析后一定与原值不等")
    void snowflake_id_would_lose_precision_as_number() {
        long snowflake = 2107832173509300225L;
        assertThat(snowflake).isGreaterThan(JS_MAX_SAFE_INTEGER);

        // double 就是 JS 的 Number，这一步等价于浏览器里的 JSON.parse
        double parsedAsJsNumber = snowflake;
        long roundTripped = (long) parsedAsJsNumber;

        assertThat(roundTripped)
                .as("如果这个断言失败，说明该 ID 恰好能被 double 精确表示，"
                        + "换一个真实的雪花 ID 再来验证")
                .isNotEqualTo(snowflake);

        // 前端把它发回来时，后端拿到的是这个错的 ID —— 所以「存在无效的岗位」是必然结果
        assertThat(String.valueOf(roundTripped)).isNotEqualTo(String.valueOf(snowflake));
    }

    // ============================================================ 工具方法

    /**
     * 模拟浏览器从 JSON 里取出一个 ID、再原样发回来的过程。
     *
     * <ul>
     *   <li>JSON 字符串 → JS 里就是 string，原样回传，精确</li>
     *   <li>JSON 数字 → JS 里是 Number（double），超出 2^53 就丢精度，回传的是错的值</li>
     * </ul>
     *
     * <p>这正是「后端返回数字」时用户在页面上看到「存在无效的岗位」的原因。
     * 用 Java 直接 {@code asText()} 取是<b>测不出</b>这个问题的，必须显式模拟。
     */
    private static String simulateBrowserRoundTrip(JsonNode idNode) {
        if (idNode.isTextual()) {
            return idNode.asText();
        }
        return String.valueOf((long) (double) idNode.asLong());
    }

    private JsonNode get(String path, String token) throws Exception {
        return exchange(HttpMethod.GET, path, null, token);
    }

    private JsonNode post(String path, Object body, String token) throws Exception {
        return exchange(HttpMethod.POST, path, body, token);
    }

    private JsonNode exchange(HttpMethod method, String path, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String payload = body == null ? null : objectMapper.writeValueAsString(body);
        ResponseEntity<String> response =
                rest.exchange(path, method, new HttpEntity<>(payload, headers), String.class);

        assertThat(response.getBody()).as("响应体不应为空: %s", path).isNotNull();
        // 直接解析原始字符串，而不是用 TestRestTemplate 自动转好的对象 ——
        // 只有这样才看得到「ID 到底是字符串还是数字」。
        return objectMapper.readTree(response.getBody());
    }

    private static void assertOk(JsonNode body) {
        assertThat(body.get("code").asInt())
                .as("接口返回失败: %s", body)
                .isZero();
    }

    private static java.util.List<String> toTextList(JsonNode array) {
        java.util.List<String> result = new java.util.ArrayList<>();
        array.forEach(node -> result.add(node.asText()));
        return result;
    }
}

package com.okcrm.platform.web.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * JSON 序列化配置。
 *
 * <p>核心目的：<b>雪花 ID 必须以字符串下发</b>。
 *
 * <p>背景：MyBatis-Plus 的 {@code assign_id} 生成 19 位雪花 ID（约 2.1e18），
 * 超过 JavaScript 的 {@code Number.MAX_SAFE_INTEGER}（9007199254740991 ≈ 9e15）。
 * 浏览器 {@code JSON.parse} 时会<b>静默丢精度</b>：
 * <pre>
 *   后端下发   2107832173509300225
 *   JS 里变成 2107832173509300200   ← 最后几位已经错了
 * </pre>
 * 前端再把这个 ID 发回来，后端按主键查不到，于是报出一些看起来毫无道理的错：
 * <ul>
 *   <li>创建员工时关联岗位 → 「存在无效的岗位，请刷新后重试」</li>
 *   <li>客户分配归属 / 转移 → 找不到员工</li>
 *   <li>按 ID 编辑、删除任意实体 → 查不到记录</li>
 * </ul>
 * 这类问题在单元测试里<b>测不出来</b>：从 Java 调接口时 Long 就是 Long，
 * 根本不经过「JSON → JavaScript」这一层。必须靠真实浏览器或断言原始 JSON 才能发现。
 *
 * <p>为什么不干脆 {@code Long -> String} 一刀切：那样连 {@code PageResult.total}
 * 这种 primitive long 的小数值（13）也会变成字符串，前端分页组件拿到字符串会告警、
 * 甚至算错。所以这里只在<b>真的会丢精度</b>时才转字符串 —— 小数值保持数字，
 * 雪花 ID 变成字符串，两边都不受委屈。
 */
@Configuration
public class JacksonConfig {

    /** JavaScript 能精确表示的最大整数（2^53 - 1） */
    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer snowflakeIdAsStringCustomizer() {
        SimpleModule module = new SimpleModule("snowflakeIdAsString");
        JsonSerializer<Long> serializer = new SafeLongSerializer();
        // 包装类型和基本类型都注册：Jackson 对 primitive long 的查找规则在各版本间
        // 有过变化，两个都注册最稳妥（序列化器自己会判断是否需要转字符串）。
        module.addSerializer(Long.class, serializer);
        module.addSerializer(Long.TYPE, serializer);
        // 用 modulesToInstall 而不是 modules：后者会覆盖掉 Spring Boot 自动发现的
        // 模块（JavaTimeModule 等），把日期序列化一起搞坏。
        return builder -> builder.modulesToInstall(module);
    }

    /**
     * 只在超出 JS 安全整数范围时才把 long 写成字符串。
     */
    static class SafeLongSerializer extends JsonSerializer<Long> {

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                gen.writeNull();
            } else if (value > JS_MAX_SAFE_INTEGER || value < -JS_MAX_SAFE_INTEGER) {
                gen.writeString(value.toString());
            } else {
                gen.writeNumber(value);
            }
        }
    }
}

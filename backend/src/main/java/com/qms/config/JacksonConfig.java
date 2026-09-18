package com.qms.config;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局 Jackson 定制。
 *
 * <p>业务主键使用雪花算法生成 Long（最大 19 位），超过 JS Number.MAX_SAFE_INTEGER
 * （16 位），若以 JSON number 下发，前端解析会丢失精度，导致更新/删除时主键不一致。
 * 故所有 Long/long/BigInteger 统一序列化为字符串；反序列化时 Jackson 原生支持
 * 字符串 → Long，前端原样回传即可。</p>
 *
 * <p>LocalDateTime 入参兼容 ISO-8601（{@code 2026-09-15T19:49:03}）与国内常用
 * {@code yyyy-MM-dd HH:mm:ss} 两种写法，避免外部适配层/前端时间格式差异导致 400；
 * 出参仍保持 ISO 格式，前端按字符串渲染。</p>
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter SPACE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            SimpleModule longModule = new SimpleModule();
            longModule.addSerializer(Long.class, ToStringSerializer.instance);
            longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            longModule.addSerializer(BigInteger.class, ToStringSerializer.instance);

            SimpleModule timeModule = new SimpleModule();
            timeModule.addDeserializer(LocalDateTime.class, new TolerantLocalDateTimeDeserializer());

            builder.modulesToInstall(longModule, timeModule);
        };
    }

    /**
     * MyBatis-Plus 的 JacksonTypeHandler 使用独立静态 ObjectMapper，默认不支持
     * JSR310（LocalDate/LocalDateTime 写入 JSON 列会抛 InvalidDefinitionException）。
     * 审批时间线快照等 JSON 列可能携带日期字段，这里统一注册 JavaTimeModule。
     */
    @Bean
    public InitializingBean jacksonTypeHandlerInitializer() {
        return () -> {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            JacksonTypeHandler.setObjectMapper(mapper);
        };
    }

    /** 先按 ISO-8601 解析，失败后回退 yyyy-MM-dd HH:mm:ss。 */
    static class TolerantLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        private final LocalDateTimeDeserializer iso = LocalDateTimeDeserializer.INSTANCE;

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            String trimmed = text.trim();
            // 含空格分隔符的走自定义格式；其余（含 'T' 的 ISO）交给标准反序列化器
            if (trimmed.charAt(10) == ' ') {
                try {
                    return LocalDateTime.parse(trimmed, SPACE_DATE_TIME);
                } catch (RuntimeException ignored) {
                    // 落到标准解析抛出统一异常
                }
            }
            return iso.deserialize(p, ctxt);
        }
    }
}

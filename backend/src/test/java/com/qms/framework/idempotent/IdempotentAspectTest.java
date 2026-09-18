package com.qms.framework.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.constant.SecurityConstants;
import com.qms.common.exception.BizException;
import com.qms.common.result.R;
import com.qms.common.result.ResultCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 幂等切面：无键放行、首请求缓存结果、处理中拦截、命中缓存直接返回、异常释放键。
 */
@ExtendWith(MockitoExtension.class)
class IdempotentAspectTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private ProceedingJoinPoint pjp;

    private IdempotentAspect aspect;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockHttpServletRequest request;

    private Idempotent idempotent() {
        return new Idempotent() {
            @Override
            public long ttl() {
                return 30L;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    @BeforeEach
    void setUp() {
        aspect = new IdempotentAspect(redisTemplate, objectMapper);
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void request_without_key_is_not_intercepted() throws Throwable {
        R<String> result = R.ok("x");
        when(pjp.proceed()).thenReturn(result);

        Object ret = aspect.around(pjp, idempotent());
        assertSame(result, ret);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void blank_key_is_not_intercepted() throws Throwable {
        request.addHeader(SecurityConstants.IDEMPOTENCY_HEADER, "  ");
        when(pjp.proceed()).thenReturn(R.ok());

        aspect.around(pjp, idempotent());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void first_request_proceeds_and_caches_r_result() throws Throwable {
        request.addHeader(SecurityConstants.IDEMPOTENCY_HEADER, "key-1");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
        when(pjp.proceed()).thenReturn(R.ok("payload"));

        Object ret = aspect.around(pjp, idempotent());
        assertEquals("0", ((R<?>) ret).getCode());
        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void duplicated_request_while_processing_is_rejected() {
        request.addHeader(SecurityConstants.IDEMPOTENCY_HEADER, "key-2");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
        when(valueOps.get(anyString())).thenReturn("PROCESSING");

        BizException ex = assertThrows(BizException.class, () -> aspect.around(pjp, idempotent()));
        assertEquals(ResultCode.REPEAT_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void duplicated_request_returns_cached_result_without_proceeding() throws Throwable {
        request.addHeader(SecurityConstants.IDEMPOTENCY_HEADER, "key-3");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
        when(valueOps.get(anyString()))
                .thenReturn(objectMapper.writeValueAsString(R.fail("BIZ_X", "cached failure")));

        Object ret = aspect.around(pjp, idempotent());
        assertEquals("BIZ_X", ((R<?>) ret).getCode());
        assertEquals("cached failure", ((R<?>) ret).getMessage());
        verify(pjp, never()).proceed();
    }

    @Test
    void duplicate_key_with_empty_cache_is_rejected() {
        request.addHeader(SecurityConstants.IDEMPOTENCY_HEADER, "key-4");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
        when(valueOps.get(anyString())).thenReturn(null);

        assertEquals(ResultCode.REPEAT_SUBMIT.getCode(),
                assertThrows(BizException.class, () -> aspect.around(pjp, idempotent())).getCode());
    }

    @Test
    void business_exception_releases_idempotency_key() {
        request.addHeader(SecurityConstants.IDEMPOTENCY_HEADER, "key-5");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        BizException boom = new BizException(ResultCode.BIZ_STATE_INVALID, "boom");
        try {
            org.mockito.Mockito.doThrow(boom).when(pjp).proceed();
        } catch (Throwable stubError) {
            throw new AssertionError(stubError);
        }

        BizException thrown = assertThrows(BizException.class, () -> aspect.around(pjp, idempotent()));
        assertEquals("boom", thrown.getMessage());
        verify(redisTemplate).delete(anyString());
    }
}

package com.qms.framework.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.security.LoginUser;
import com.qms.modules.system.entity.SysAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计切面：记录操作人、动作、业务id、变更前后值、IP/UA、结果与耗时。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        SysAuditLog record = new SysAuditLog();
        record.setTraceId(MDC.get("traceId"));
        record.setModule(auditLog.module());
        record.setAction(auditLog.action());
        record.setBizType(auditLog.bizType());
        record.setCreatedAt(LocalDateTime.now());

        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser != null) {
            record.setUserId(loginUser.getUserId());
            record.setUsername(loginUser.getUsername());
        }
        HttpServletRequest request = currentRequest();
        if (request != null) {
            record.setIp(resolveIp(request));
            record.setUserAgent(request.getHeader("User-Agent"));
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        record.setBizId(evalBizId(auditLog.bizIdExpr(), signature, pjp.getArgs()));

        Object result;
        // 注意：必须在 finally 清理 ThreadLocal 之前把 before 快照取到局部变量
        String beforeJson = AuditContext.getBefore();
        try {
            result = pjp.proceed();
            beforeJson = AuditContext.getBefore();
            record.setResult(1);
        } catch (Throwable ex) {
            beforeJson = AuditContext.getBefore();
            record.setResult(0);
            record.setErrorMsg(truncate(ex.getMessage(), 1000));
            fillAfterValue(record, pjp.getArgs());
            record.setBeforeValue(safeJson(beforeJson, 8000));
            record.setCostMs(System.currentTimeMillis() - start);
            auditLogService.saveAsync(record);
            throw ex;
        } finally {
            AuditContext.clear();
        }
        fillAfterValue(record, pjp.getArgs());
        record.setBeforeValue(safeJson(beforeJson, 8000));
        record.setCostMs(System.currentTimeMillis() - start);
        auditLogService.saveAsync(record);
        return result;
    }

    private void fillAfterValue(SysAuditLog record, Object[] args) {
        try {
            List<Object> serializable = new ArrayList<>();
            for (Object arg : args) {
                if (isSerializableArg(arg)) {
                    serializable.add(arg);
                }
            }
            if (!serializable.isEmpty()) {
                record.setAfterValue(safeJson(objectMapper.writeValueAsString(serializable), 8000));
            }
        } catch (Exception e) {
            log.debug("审计 afterValue 序列化失败: {}", e.getMessage());
        }
    }

    /**
     * before_value/after_value 在库中为 JSON 列，必须保证写入内容是合法 JSON；
     * 超长截断会破坏 JSON 结构，故超长时改写为说明性 JSON 对象。
     */
    private String safeJson(String json, int max) {
        if (json == null) {
            return null;
        }
        if (json.length() <= max && isValidJson(json)) {
            return json;
        }
        int cut = Math.max(0, Math.min(json.length(), max - 200));
        String preview = json.substring(0, cut).replace("\"", "'").replaceAll("[\\r\\n\\t]", " ");
        return "{\"truncated\":true,\"length\":" + json.length() + ",\"preview\":\"" + preview + "\"}";
    }

    private boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSerializableArg(Object arg) {
        if (arg == null) {
            return false;
        }
        if (arg instanceof MultipartFile || arg instanceof HttpServletRequest) {
            return false;
        }
        String pkg = arg.getClass().getPackageName();
        return pkg.startsWith("com.qingyuan") || arg instanceof Number || arg instanceof String
                || arg instanceof Boolean || arg instanceof List<?>;
    }

    private Long evalBizId(String expr, MethodSignature signature, Object[] args) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        try {
            EvaluationContext context = new StandardEvaluationContext();
            String[] names = paramDiscoverer.getParameterNames(signature.getMethod());
            if (names != null) {
                for (int i = 0; i < names.length && i < args.length; i++) {
                    context.setVariable(names[i], args[i]);
                }
            }
            Expression expression = parser.parseExpression(expr);
            Object value = expression.getValue(context);
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            log.warn("审计 bizId SpEL 解析失败 expr={}, msg={}", expr, e.getMessage());
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp : request.getRemoteAddr();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}

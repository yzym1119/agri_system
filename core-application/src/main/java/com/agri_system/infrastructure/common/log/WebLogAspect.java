package com.agri_system.infrastructure.common.log;

import com.agri_system.domain.common.log.LogMaskingUtils;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.staff.dto.command.SysOperationLogCommand;
import com.agri_system.application.staff.service.SysLogService;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class WebLogAspect {

    private static final long WARN_THRESHOLD = 1000L;
    private static final long ERROR_THRESHOLD = 3000L;
    private final SysLogService sysLogService;

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);

        HttpServletRequest request = currentRequest();
        String url = request != null ? request.getRequestURI() : "";
        String method = request != null ? request.getMethod() : "";
        String clientIp = request != null ? request.getRemoteAddr() : "";
        String args = maskArgs(pjp.getArgs());

        try {
            Object result = pjp.proceed();
            long cost = System.currentTimeMillis() - start;
            logWithLevel(cost, "API Success: [{} {}] traceId={} ip={} args={} cost={}ms",
                    method, url, traceId, clientIp, args, cost);
            recordLog(pjp, method, url, clientIp, args, traceId, cost, true, null);
            return result;
        } catch (Throwable ex) {
            long cost = System.currentTimeMillis() - start;
            log.error("API Failed: [{} {}] traceId={} ip={} args={} cost={}ms error={}",
                    method, url, traceId, clientIp, args, cost, ex.getMessage(), ex);
            recordLog(pjp, method, url, clientIp, args, traceId, cost, false, ex.getMessage());
            throw ex;
        } finally {
            MDC.remove("traceId");
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String maskArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        return Arrays.stream(args)
                .filter(arg -> !(arg instanceof ServletRequest) && !(arg instanceof ServletResponse))
                .map(LogMaskingUtils::mask)
                .collect(Collectors.joining(", "));
    }

    private void logWithLevel(long cost, String message, Object... args) {
        if (cost > ERROR_THRESHOLD) {
            log.error("Slow API - " + message, args);
        } else if (cost > WARN_THRESHOLD) {
            log.warn("Slow API - " + message, args);
        } else {
            log.info(message, args);
        }
    }

    private void recordLog(ProceedingJoinPoint pjp, String method, String url, String ip,
                           String params, String traceId, long cost, boolean success, String errorMsg) {
        Long staffId = resolveStaffId();
        SysOperationLogCommand command = SysOperationLogCommand.builder()
                .staffId(staffId)
                .module(extractModule(url))
                .action(pjp.getSignature().getName())
                .method(method)
                .requestUri(url)
                .requestParams(params)
                .ip(ip)
                .costTime(cost)
                .status(success ? 1 : 0)
                .errorMsg(errorMsg)
                .traceId(traceId)
                .build();
        sysLogService.recordAsync(command);
    }

    private Long resolveStaffId() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId != null && loginId.startsWith("STAFF_")) {
            try {
                return Long.parseLong(loginId.substring("STAFF_".length()));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String extractModule(String url) {
        if (url == null) {
            return "";
        }
        String[] parts = url.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("api".equals(parts[i]) && i + 2 < parts.length) {
                return parts[i + 2];
            }
        }
        return parts.length > 1 ? parts[1] : url;
    }
}






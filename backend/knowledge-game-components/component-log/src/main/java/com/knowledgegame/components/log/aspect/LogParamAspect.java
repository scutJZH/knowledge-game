package com.knowledgegame.components.log.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.components.log.annotation.LogParam;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AOP 切面：处理 @LogParam 注解，自动记录 Controller 方法参数
 */
@Aspect
public class LogParamAspect {

    private static final Logger log = LoggerFactory.getLogger(LogParamAspect.class);

    private static final int MAX_LENGTH = 1000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 环绕通知：拦截 @LogParam 标注的 Controller 方法
     */
    @Around("@annotation(logParam)")
    public Object logParameters(ProceedingJoinPoint joinPoint, LogParam logParam) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        // 构建需要脱敏的参数名集合
        Set<String> maskedParams = new HashSet<>(Arrays.asList(logParam.maskedParams()));

        // 序列化参数
        String paramsStr = serializeParameters(paramNames, args, maskedParams);

        log.info("Controller 方法调用: {} params={}", methodName, paramsStr);

        return joinPoint.proceed();
    }

    /**
     * 序列化参数列表为字符串
     */
    private String serializeParameters(String[] paramNames, Object[] args, Set<String> maskedParams) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String name = paramNames[i];
            Object value = args[i];

            sb.append(name).append("=");

            if (maskedParams.contains(name)) {
                sb.append("***");
            } else {
                sb.append(serializeValue(value));
            }
        }
        sb.append("}");
        return truncate(sb.toString());
    }

    /**
     * 序列化单个参数值
     */
    private String serializeValue(Object value) {
        if (value == null) {
            return "null";
        }
        // 文件类型仅记录文件名
        if (value instanceof MultipartFile mf) {
            return "<file:" + mf.getOriginalFilename() + ">";
        }
        if (value instanceof InputStream) {
            return "<binary>";
        }
        // 简单类型直接输出
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        // 复杂对象用 Jackson 序列化
        try {
            String json = objectMapper.writeValueAsString(value);
            return truncate(json);
        } catch (Exception e) {
            // fallback 到 toString
            return truncate(String.valueOf(value));
        }
    }

    /**
     * 超过最大长度则截断
     */
    private String truncate(String value) {
        if (value.length() > MAX_LENGTH) {
            return value.substring(0, MAX_LENGTH) + "...(truncated)";
        }
        return value;
    }
}

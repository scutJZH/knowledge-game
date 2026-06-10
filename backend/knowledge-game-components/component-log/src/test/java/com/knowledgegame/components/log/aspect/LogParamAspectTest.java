package com.knowledgegame.components.log.aspect;

import com.knowledgegame.components.log.annotation.LogParam;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * LogParamAspect 单元测试
 */
class LogParamAspectTest {

    private LogParamAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private MethodSignature methodSignature;

    @BeforeEach
    void setUp() throws Throwable {
        aspect = new LogParamAspect();
        joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        methodSignature = Mockito.mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.proceed()).thenReturn("result");
    }

    /**
     * 创建 @LogParam 注解实例
     */
    private LogParam createLogParam(String... maskedParams) throws NoSuchMethodException {
        // 使用一个带 @LogParam 的方法来获取注解实例
        Method method = TestController.class.getMethod("testMethod", String.class, String.class);
        return method.getAnnotation(LogParam.class);
    }

    /**
     * 创建自定义 maskedParams 的 LogParam 注解
     */
    private LogParam createLogParamWithMasked(String... maskedParams) {
        return new LogParam() {
            @Override
            public String[] maskedParams() {
                return maskedParams;
            }

            @Override
            public Class<LogParam> annotationType() {
                return LogParam.class;
            }
        };
    }

    /**
     * 测试用内部 Controller
     */
    public static class TestController {
        @LogParam
        public void testMethod(String name, String value) {
        }
    }

    /**
     * 测试用 DTO
     */
    public static class UserDto {
        private String username;
        private String email;

        public UserDto(String username, String email) {
            this.username = username;
            this.email = email;
        }

        public String getUsername() {
            return username;
        }

        public String getEmail() {
            return email;
        }
    }

    @Nested
    @DisplayName("简单类型参数序列化")
    class SimpleTypeTests {

        @Test
        @DisplayName("String 类型参数直接输出")
        void shouldDirectlyOutputStringParam() throws Throwable {
            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"name", "value"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{"john", "hello"});

            LogParam logParam = createLogParamWithMasked();

            Object result = aspect.logParameters(joinPoint, logParam);

            assertThat(result).isEqualTo("result");
            // 验证 joinPoint.proceed() 被调用
            Mockito.verify(joinPoint).proceed();
        }

        @Test
        @DisplayName("Number 类型参数直接输出")
        void shouldDirectlyOutputNumberParam() throws Throwable {
            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"count"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{42});

            LogParam logParam = createLogParamWithMasked();
            Object result = aspect.logParameters(joinPoint, logParam);
            assertThat(result).isEqualTo("result");
        }

        @Test
        @DisplayName("Boolean 类型参数直接输出")
        void shouldDirectlyOutputBooleanParam() throws Throwable {
            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"enabled"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{true});

            LogParam logParam = createLogParamWithMasked();
            Object result = aspect.logParameters(joinPoint, logParam);
            assertThat(result).isEqualTo("result");
        }

        @Test
        @DisplayName("null 参数输出为 null")
        void shouldOutputNullForNullParam() throws Throwable {
            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"name"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{null});

            LogParam logParam = createLogParamWithMasked();
            Object result = aspect.logParameters(joinPoint, logParam);
            assertThat(result).isEqualTo("result");
        }
    }

    @Nested
    @DisplayName("复杂对象序列化")
    class ComplexObjectTests {

        @Test
        @DisplayName("复杂对象使用 Jackson 序列化")
        void shouldSerializeComplexObjectWithJackson() throws Throwable {
            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            UserDto userDto = new UserDto("john", "john@test.com");
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"user"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{userDto});

            LogParam logParam = createLogParamWithMasked();

            // 不抛异常即为成功（Jackson 序列化）
            Object result = aspect.logParameters(joinPoint, logParam);
            assertThat(result).isEqualTo("result");
        }
    }

    @Nested
    @DisplayName("MultipartFile 处理")
    class MultipartFileTests {

        @Test
        @DisplayName("MultipartFile 仅记录文件名")
        void shouldOnlyLogFileNameForMultipartFile() throws Throwable {
            MultipartFile multipartFile = Mockito.mock(MultipartFile.class);
            when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");

            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"file"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{multipartFile});

            LogParam logParam = createLogParamWithMasked();

            Object result = aspect.logParameters(joinPoint, logParam);
            assertThat(result).isEqualTo("result");
        }
    }

    @Nested
    @DisplayName("InputStream 处理")
    class InputStreamTests {

        @Test
        @DisplayName("InputStream 输出为 <binary>")
        void shouldOutputBinaryForInputStream() throws Throwable {
            InputStream inputStream = new ByteArrayInputStream("data".getBytes());

            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"data"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{inputStream});

            LogParam logParam = createLogParamWithMasked();

            Object result = aspect.logParameters(joinPoint, logParam);
            assertThat(result).isEqualTo("result");
        }
    }

    @Nested
    @DisplayName("超长字符串截断")
    class TruncationTests {

        @Test
        @DisplayName("超过 1000 字符的参数值被截断")
        void shouldTruncateLongValues() throws Throwable {
            // 构建超长字符串
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 200; i++) {
                sb.append("abcdefghij"); // 每次加 10 个字符，总计 2000
            }
            String longValue = sb.toString();

            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"data"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{longValue});

            LogParam logParam = createLogParamWithMasked();

            Object result = aspect.logParameters(joinPoint, logParam);
            assertThat(result).isEqualTo("result");
        }
    }

    @Nested
    @DisplayName("脱敏参数")
    class MaskedParamsTests {

        @Test
        @DisplayName("maskedParams 列表中的参数值替换为 ***")
        void shouldMaskSpecifiedParams() throws Throwable {
            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"username", "password"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{"john", "secret123"});

            LogParam logParam = createLogParamWithMasked("password");

            Object result = aspect.logParameters(joinPoint, logParam);
            assertThat(result).isEqualTo("result");
        }

        @Test
        @DisplayName("maskedParams 为空时不脱敏任何参数")
        void shouldNotMaskWhenEmptyMaskedParams() throws Throwable {
            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"username", "password"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{"john", "secret123"});

            LogParam logParam = createLogParamWithMasked();

            Object result = aspect.logParameters(joinPoint, logParam);
            assertThat(result).isEqualTo("result");
        }
    }

    @Nested
    @DisplayName("方法异常传播")
    class ExceptionPropagationTests {

        @Test
        @DisplayName("目标方法抛出异常时，切面正确传播异常")
        void shouldPropagateExceptionFromTargetMethod() throws Throwable {
            when(methodSignature.getMethod()).thenReturn(
                    TestController.class.getMethod("testMethod", String.class, String.class));
            when(methodSignature.getParameterNames()).thenReturn(new String[]{"name"});
            when(joinPoint.getArgs()).thenReturn(new Object[]{"test"});

            RuntimeException expectedException = new RuntimeException("目标方法异常");
            when(joinPoint.proceed()).thenThrow(expectedException);

            LogParam logParam = createLogParamWithMasked();

            Throwable thrown = null;
            try {
                aspect.logParameters(joinPoint, logParam);
            } catch (Throwable t) {
                thrown = t;
            }

            assertThat(thrown).isSameAs(expectedException);
        }
    }
}

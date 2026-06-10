package com.knowledgegame.components.exception.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 单元测试
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();

        // 设置 Logback ListAppender 捕获日志
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger =
                loggerContext.getLogger(GlobalExceptionHandler.class);

        listAppender = new ListAppender<>();
        listAppender.setContext(loggerContext);
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        // 移除 appender 防止影响其他测试
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger =
                loggerContext.getLogger(GlobalExceptionHandler.class);
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Nested
    @DisplayName("BusinessException 处理")
    class BusinessExceptionTests {

        @Test
        @DisplayName("BusinessException 返回正确的 code 和 message")
        void shouldReturnCorrectCodeAndMessage() {
            BusinessException exception = new BusinessException(4001, "用户名已存在");

            Result<Void> result = handler.handleBusinessException(exception);

            assertThat(result.getCode()).isEqualTo(4001);
            assertThat(result.getMessage()).isEqualTo("用户名已存在");
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("BusinessException 使用 ResultCode 构造时返回对应 code")
        void shouldReturnResultCodeValue() {
            BusinessException exception = new BusinessException(ResultCode.FAIL);

            Result<Void> result = handler.handleBusinessException(exception);

            assertThat(result.getCode()).isEqualTo(ResultCode.FAIL.getCode());
            assertThat(result.getMessage()).isEqualTo(ResultCode.FAIL.getMessage());
        }

        @Test
        @DisplayName("BusinessException 使用仅 message 构造时 code 默认为 400")
        void shouldReturnDefaultCodeWhenMessageOnly() {
            BusinessException exception = new BusinessException("自定义错误");

            Result<Void> result = handler.handleBusinessException(exception);

            assertThat(result.getCode()).isEqualTo(400);
            assertThat(result.getMessage()).isEqualTo("自定义错误");
        }

        @Test
        @DisplayName("BusinessException 日志级别为 WARN")
        void shouldLogAtWarnLevel() {
            BusinessException exception = new BusinessException(400, "业务异常");
            handler.handleBusinessException(exception);

            List<ILoggingEvent> warnEvents = listAppender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .toList();

            assertThat(warnEvents).isNotEmpty();
            assertThat(warnEvents.get(0).getFormattedMessage()).contains("业务异常");
        }
    }

    @Nested
    @DisplayName("MethodArgumentNotValidException 处理")
    class ValidationExceptionTests {

        @Test
        @DisplayName("参数校验异常返回 FAIL(400) 和拼接的错误消息")
        void shouldReturnValidationErrors() {
            // 构造 mock 的 MethodArgumentNotValidException
            MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);

            FieldError fieldError1 = new FieldError("userDto", "username", "不能为空");
            FieldError fieldError2 = new FieldError("userDto", "email", "格式不正确");

            when(exception.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

            Result<Void> result = handler.handleValidationException(exception);

            assertThat(result.getCode()).isEqualTo(ResultCode.FAIL.getCode());
            assertThat(result.getMessage()).contains("username", "不能为空", "email", "格式不正确");
        }

        @Test
        @DisplayName("无 FieldError 时返回默认消息")
        void shouldReturnDefaultMessageWhenNoFieldErrors() {
            MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);

            when(exception.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getFieldErrors()).thenReturn(Collections.emptyList());

            Result<Void> result = handler.handleValidationException(exception);

            assertThat(result.getCode()).isEqualTo(ResultCode.FAIL.getCode());
            assertThat(result.getMessage()).isEqualTo("参数校验失败");
        }

        @Test
        @DisplayName("参数校验异常日志级别为 WARN")
        void shouldLogAtWarnLevel() {
            MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);

            FieldError fieldError = new FieldError("dto", "name", "不能为空");
            when(exception.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

            handler.handleValidationException(exception);

            List<ILoggingEvent> warnEvents = listAppender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .toList();

            assertThat(warnEvents).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("系统异常处理")
    class SystemExceptionTests {

        @Test
        @DisplayName("系统异常返回 INTERNAL_ERROR(500)")
        void shouldReturnInternalError() {
            Exception exception = new RuntimeException("数据库连接失败");

            Result<Void> result = handler.handleException(exception);

            assertThat(result.getCode()).isEqualTo(ResultCode.INTERNAL_ERROR.getCode());
            assertThat(result.getMessage()).isEqualTo(ResultCode.INTERNAL_ERROR.getMessage());
        }

        @Test
        @DisplayName("系统异常日志级别为 ERROR（含堆栈）")
        void shouldLogAtErrorLevelWithStackTrace() {
            Exception exception = new RuntimeException("模拟系统异常");

            handler.handleException(exception);

            List<ILoggingEvent> errorEvents = listAppender.list.stream()
                    .filter(event -> event.getLevel() == Level.ERROR)
                    .toList();

            assertThat(errorEvents).isNotEmpty();
            assertThat(errorEvents.get(0).getFormattedMessage()).contains("模拟系统异常");

            // 验证包含堆栈信息（throwable 不为 null 表示有堆栈）
            assertThat(errorEvents.get(0).getThrowableProxy()).isNotNull();
        }
    }
}

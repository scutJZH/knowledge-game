package com.knowledgegame.app.common;

import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 乐观锁异常处理器，与 component-exception 的 GlobalExceptionHandler 共存。
 * component-exception 无 spring-orm 依赖无法 import ObjectOptimisticLockingFailureException，
 * 因此由 app 模块自行处理。
 */
@RestControllerAdvice
public class OptimisticLockExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockExceptionHandler.class);

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Result<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("乐观锁冲突: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.fail(ResultCode.OPTIMISTIC_LOCK_CONFLICT));
    }
}

package com.knowledgegame.components.log.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 方法上，AOP 切面自动记录方法名和参数值（经过脱敏）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogParam {

    /**
     * 需要脱敏的参数名列表
     */
    String[] maskedParams() default {};
}

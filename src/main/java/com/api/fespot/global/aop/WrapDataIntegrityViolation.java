package com.api.fespot.global.aop;

import com.api.fespot.global.exception.BaseException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WrapDataIntegrityViolation {

    Class<? extends BaseException> value();
    String constraint() default "";
}

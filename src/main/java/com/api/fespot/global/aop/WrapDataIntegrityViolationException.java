package com.api.fespot.global.aop;

import com.api.fespot.global.exception.BaseException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.BeanUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Aspect
@Component
// Spring이 어노테이션 인자를 바인딩할 수 있도록 ExposeInvocationInterceptor 이후에 실행한다.
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class WrapDataIntegrityViolationException {

    @Around("@annotation(wrap)")
    public Object wrap(ProceedingJoinPoint pjp, WrapUniqueViolation wrap) throws Throwable {


        try {
            // target method invoke
            return pjp.proceed();
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e, wrap.constraint())) {
                throw newBaseException(wrap.value(), e);
            }
            throw e;
        }
    }

    private boolean isUniqueViolation(Throwable e, String expected) {
        ConstraintViolationException cve = findCause(e, ConstraintViolationException.class);

        if (cve == null || cve.getKind() != ConstraintViolationException.ConstraintKind.UNIQUE) {
            return false;
        }

        if (expected.isEmpty()) {
            return true;
        }

        String actual = cve.getConstraintName();

        if (actual == null) {
            return false;
        }

        String a = actual.toLowerCase(Locale.ROOT);
        String x = expected.toLowerCase(Locale.ROOT);

        return a.equals(x) || a.endsWith("." + x);

    }

    private static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        for (Throwable cur = e; cur != null; cur = cur.getCause()) {
            if(type.isInstance(cur)) {
                return type.cast(cur);
            }
        }
        return null;
    }

    private BaseException newBaseException(Class<? extends BaseException> type, Throwable cause) {

        try {
            return BeanUtils.instantiateClass(type.getDeclaredConstructor(Throwable.class), cause);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(type.getName() + "is need a (Throwable cause) constructor", e);
        }
    }
}

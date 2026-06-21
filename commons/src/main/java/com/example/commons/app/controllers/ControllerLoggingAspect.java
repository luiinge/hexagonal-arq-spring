package com.example.commons.app.controllers;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class ControllerLoggingAspect {

	@Around("within(@org.springframework.web.bind.annotation.RestController *) && execution(public * *(..))")
	public Object logControllerCall(ProceedingJoinPoint joinPoint) throws Throwable {

		String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
		String methodName = joinPoint.getSignature().getName();

		log.debug("-> {}.{}", className, methodName);
		long start = System.nanoTime();
		try {
			Object result = joinPoint.proceed();
			log.debug(
				"<- {}.{} {}ms",
				className,
				methodName,
				TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
			);
			return result;

		} catch (Throwable ex) {
			log.error(
				"<- ERROR {}.{} {}ms :: {}",
				className,
				methodName,
				TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
				ex.getMessage()
			);
			throw ex;
		}
	}
}

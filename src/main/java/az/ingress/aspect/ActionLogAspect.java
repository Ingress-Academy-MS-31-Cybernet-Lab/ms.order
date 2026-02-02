package az.ingress.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ActionLogAspect {

    @Around("@annotation(az.ingress.annotation.ActionLog)")
    public Object logAction(ProceedingJoinPoint joinPoint) throws Throwable {
        var start = System.currentTimeMillis();
        var methodName = joinPoint.getSignature().getName();
        var className = joinPoint.getTarget().getClass().getSimpleName();
        var args = joinPoint.getArgs();

        log.info("ActionLog.{}.{} start | args: {}", className, methodName, args);

        Object proceed = joinPoint.proceed();

        var executionTime = System.currentTimeMillis() - start;
        log.info("ActionLog.{}.{} end | duration: {} ms", className, methodName, executionTime);

        return proceed;
    }
}

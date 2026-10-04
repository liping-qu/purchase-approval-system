package com.enterprise.procurement.aspect;

import com.enterprise.procurement.admin.entity.OperationLogEntity;
import com.enterprise.procurement.admin.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.LocalDateTime;

@Aspect
@Component
public class OperationLogAspect {
    private final OperationLogMapper mapper;
    public OperationLogAspect(OperationLogMapper mapper) { this.mapper = mapper; }
    @Around("@annotation(com.enterprise.procurement.aspect.OperationLog)")
    public Object record(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        OperationLog op = signature.getMethod().getAnnotation(OperationLog.class);
        String result = "SUCCESS";
        try { return point.proceed(); } catch (Throwable ex) { result = "FAIL"; throw ex; }
        finally {
            OperationLogEntity log = new OperationLogEntity();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            log.setOperatorId(auth != null && auth.isAuthenticated() ? null : null);
            log.setModule("采购管理"); log.setAction(op.value()); log.setResult(result); log.setCreateTime(LocalDateTime.now());
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) { HttpServletRequest req = attrs.getRequest(); log.setRequestUri(req.getRequestURI()); log.setIp(req.getRemoteAddr()); }
            mapper.insert(log);
        }
    }
}

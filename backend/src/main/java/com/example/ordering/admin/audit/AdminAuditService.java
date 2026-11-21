package com.example.ordering.admin.audit;

import com.example.ordering.user.UserAccount;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;

    public AdminAuditService(AdminAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(
            UserAccount actor,
            String action,
            String targetType,
            Object targetId,
            String beforeValue,
            String afterValue,
            BigDecimal amountDelta,
            String detail
    ) {
        AdminAuditLogEntity log = new AdminAuditLogEntity();
        if (actor != null) {
            log.setActorUserId(actor.getId());
            log.setActorUsername(trim(actor.getUsername(), 32));
        }
        log.setAction(trim(action, 64));
        log.setTargetType(trim(targetType, 64));
        log.setTargetId(trim(targetId == null ? "" : targetId.toString(), 80));
        log.setBeforeValue(trim(beforeValue, 120));
        log.setAfterValue(trim(afterValue, 120));
        log.setAmountDelta(amountDelta);
        log.setDetail(trim(detail, 600));
        auditLogRepository.save(log);
    }

    private String trim(String value, int limit) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, limit);
    }
}

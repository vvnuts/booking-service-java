package com.booking.service.service.mapper;

import com.booking.service.dto.response.AuditStatusLogResponse;
import com.booking.service.entity.AuditStatusLog;
import org.springframework.stereotype.Component;

/**
 * Mapper для преобразования записи аудита статуса в DTO ответа.
 */
@Component
public class AuditStatusLogMapper {

    /**
     * Преобразует JPA-сущность записи аудита статуса в DTO для REST-ответа.
     *
     * @param auditStatusLog запись аудита статуса
     * @return DTO записи аудита статуса
     */
    public AuditStatusLogResponse toResponse(AuditStatusLog auditStatusLog) {
        return new AuditStatusLogResponse(
                auditStatusLog.getId(),
                auditStatusLog.getBooking().getId(),
                auditStatusLog.getPreviousStatus(),
                auditStatusLog.getNewStatus(),
                auditStatusLog.getChangedAt(),
                auditStatusLog.getReason(),
                auditStatusLog.getInitiator()
        );
    }
}

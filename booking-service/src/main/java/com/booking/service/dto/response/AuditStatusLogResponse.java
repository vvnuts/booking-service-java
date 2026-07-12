package com.booking.service.dto.response;

import com.booking.service.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO для передачи записи аудита изменения статуса бронирования в REST-ответе.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditStatusLogResponse {
    private Long id;
    private Long bookingId;
    private BookingStatus previousStatus;
    private BookingStatus newStatus;
    private OffsetDateTime changedAt;
    private String reason;
    private String initiator;
}

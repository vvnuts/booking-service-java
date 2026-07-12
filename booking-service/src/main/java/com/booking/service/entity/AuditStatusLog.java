package com.booking.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * JPA-сущность записи аудита изменения статуса бронирования.
 */
@Entity
@Table(name = "audit_status_log")
@Getter
@NoArgsConstructor
public class AuditStatusLog {

    public static final String SYSTEM_INITIATOR = "System";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "previous_status", nullable = false)
    private BookingStatus previousStatus;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "new_status", nullable = false)
    private BookingStatus newStatus;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String initiator;

    /**
     * Создает запись аудита изменения статуса бронирования.
     *
     * @param booking бронирование, для которого изменился статус
     * @param previousStatus предыдущий статус бронирования
     * @param newStatus новый статус бронирования
     * @param changedAt время изменения статуса
     * @param reason причина изменения статуса
     * @param initiator инициатор изменения: идентификатор пользователя или {@link #SYSTEM_INITIATOR}
     * @return новая запись аудита статуса
     */
    public static AuditStatusLog create(Booking booking,
                                        BookingStatus previousStatus,
                                        BookingStatus newStatus,
                                        OffsetDateTime changedAt,
                                        String reason,
                                        String initiator) {
        AuditStatusLog auditStatusLog = new AuditStatusLog();
        auditStatusLog.booking = booking;
        auditStatusLog.previousStatus = previousStatus;
        auditStatusLog.newStatus = newStatus;
        auditStatusLog.changedAt = changedAt;
        auditStatusLog.reason = reason;
        auditStatusLog.initiator = initiator;
        return auditStatusLog;
    }
}

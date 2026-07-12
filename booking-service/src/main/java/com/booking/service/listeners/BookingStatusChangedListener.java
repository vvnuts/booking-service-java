package com.booking.service.listeners;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.entity.AuditStatusLog;
import com.booking.service.listeners.events.BookingStatusChangedEvent;
import com.booking.service.repository.AuditStatusLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Листенер для событий связанных со статусами
 */
@Component
@RequiredArgsConstructor
public class BookingStatusChangedListener {

    private final AuditStatusLogRepository repository;
    private final CurrentDateTimeProvider dateTimeProvider;

    /**
     * Обрабатывает событие изменения статуса перед коммитом транзакции и сохраняет запись аудита.
     *
     * @param event событие изменения статуса бронирования
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(BookingStatusChangedEvent event) {
        AuditStatusLog auditStatusLog = AuditStatusLog.create(
                event.booking(),
                event.oldStatus(),
                event.newStatus(),
                dateTimeProvider.utcNow(),
                event.reason().getValue(),
                event.initiator()
        );
        repository.save(auditStatusLog);
    }
}

package com.booking.service.scheduler;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import com.booking.service.repository.BookingRepository;
import com.booking.service.service.BookingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Планировщик фоновых задач для восстановления асинхронных операций бронирования.
 */
@Component
@Slf4j
public class BookingScheduler {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final CurrentDateTimeProvider dateTimeProvider;
    private final Long checkCancellationPending;

    public BookingScheduler(BookingService bookingService,
                            BookingRepository bookingRepository,
                            CurrentDateTimeProvider dateTimeProvider,
                            @Value("${booking.scheduler.check-cancellation-pending-s}") Long checkCancellationPending) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.dateTimeProvider = dateTimeProvider;
        this.checkCancellationPending = checkCancellationPending;
    }

    /**
     * Находит бронирования, зависшие в статусе {@link BookingStatus#CANCELLATION_PENDING},
     * и повторно отправляет команду отмены в Catalog Service.
     * Бронирование считается зависшим, если с момента последней отправки команды прошло
     * больше {@code booking.scheduler.check-cancellation-pending-s} секунд.
     */
    @Scheduled(fixedDelayString = "${booking.scheduler.find-cancellation-pending-delay-ms}")
    public void findAndResendHoveringCancellationPending() {
        OffsetDateTime utcNow = dateTimeProvider.utcNow();
        OffsetDateTime minUtcForChecking = utcNow.minusSeconds(checkCancellationPending);

        List<Booking> hoverings = bookingRepository.findByStatusAndSendCommandTimeBefore(
                BookingStatus.CANCELLATION_PENDING,
                minUtcForChecking
        );

        for (Booking hovering : hoverings) {
            try {
                bookingService.resendCancelBookingCommand(hovering);
            } catch (Exception ex) {
                log.error("Failed to send cancel command: bookingId={}", hovering.getId(), ex);
            }
        }
    }
}

package com.booking.service.scheduler;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import com.booking.service.repository.BookingRepository;
import com.booking.service.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingSchedulerTest {

    private static final long CHECK_CANCELLATION_PENDING_SECONDS = 600L;

    @Mock
    private BookingService bookingService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CurrentDateTimeProvider dateTimeProvider;

    private BookingScheduler bookingScheduler;

    @BeforeEach
    void setUp() {
        bookingScheduler = new BookingScheduler(
                bookingService,
                bookingRepository,
                dateTimeProvider,
                CHECK_CANCELLATION_PENDING_SECONDS
        );
    }

    @Test
    void findAndResendHoveringCancellationPending_shouldFindHoveringBookingsByStatusAndSendCommandTime() {
        OffsetDateTime now = OffsetDateTime.of(2026, 7, 12, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime sentBefore = now.minusSeconds(CHECK_CANCELLATION_PENDING_SECONDS);
        Booking hoveringBooking = pendingBooking(10L, now.minusSeconds(601));

        when(dateTimeProvider.utcNow()).thenReturn(now);
        when(bookingRepository.findByStatusAndSendCommandTimeBefore(BookingStatus.CANCELLATION_PENDING, sentBefore))
                .thenReturn(List.of(hoveringBooking));

        bookingScheduler.findAndResendHoveringCancellationPending();

        verify(dateTimeProvider).utcNow();
        verify(bookingRepository).findByStatusAndSendCommandTimeBefore(
                BookingStatus.CANCELLATION_PENDING,
                sentBefore
        );
        verify(bookingService).resendCancelBookingCommand(hoveringBooking);
        verifyNoMoreInteractions(bookingService, bookingRepository, dateTimeProvider);
    }

    @Test
    void findAndResendHoveringCancellationPending_shouldContinueWhenResendFailsForOneBooking() {
        OffsetDateTime now = OffsetDateTime.of(2026, 7, 12, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime sentBefore = now.minusSeconds(CHECK_CANCELLATION_PENDING_SECONDS);
        Booking firstHoveringBooking = pendingBooking(10L, now.minusSeconds(601));
        Booking secondHoveringBooking = pendingBooking(20L, now.minusSeconds(700));

        when(dateTimeProvider.utcNow()).thenReturn(now);
        when(bookingRepository.findByStatusAndSendCommandTimeBefore(BookingStatus.CANCELLATION_PENDING, sentBefore))
                .thenReturn(List.of(firstHoveringBooking, secondHoveringBooking));
        doThrow(new RuntimeException("RabbitMQ unavailable"))
                .when(bookingService).resendCancelBookingCommand(firstHoveringBooking);

        bookingScheduler.findAndResendHoveringCancellationPending();

        verify(dateTimeProvider).utcNow();
        verify(bookingRepository).findByStatusAndSendCommandTimeBefore(
                BookingStatus.CANCELLATION_PENDING,
                sentBefore
        );
        verify(bookingService).resendCancelBookingCommand(firstHoveringBooking);
        verify(bookingService).resendCancelBookingCommand(secondHoveringBooking);
        verifyNoMoreInteractions(bookingService, bookingRepository, dateTimeProvider);
    }

    private Booking pendingBooking(Long resourceId, OffsetDateTime sendCommandTime) {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 7, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        Booking booking = Booking.create(
                1L,
                resourceId,
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 12),
                createdAt
        );
        booking.cancellationPending(createdAt.plusDays(1));
        booking.markCancelCommandSent(sendCommandTime);
        return booking;
    }
}

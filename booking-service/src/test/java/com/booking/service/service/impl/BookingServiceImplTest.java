package com.booking.service.service.impl;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.dto.response.StatisticsResponse;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import com.booking.service.exception.BusinessException;
import com.booking.service.messaging.contracts.CancelBookingJobByRequestIdRequest;
import com.booking.service.messaging.listener.BookingEventPublisher;
import com.booking.service.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    @Mock
    private CurrentDateTimeProvider dateTimeProvider;

    @InjectMocks
    private BookingServiceImpl bookingService;

    @Test
    void getStatisticsByDate_shouldReturnAggregatedStatistics() {
        LocalDate dateFrom = LocalDate.of(2026, 7, 1);
        LocalDate dateTo = LocalDate.of(2026, 7, 31);
        OffsetDateTime rangeStart = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime rangeEndExclusive = OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        PageRequest topFive = PageRequest.of(0, 5);

        when(bookingRepository.countByCreatedAtBetween(rangeStart, rangeEndExclusive))
                .thenReturn(4L);
        when(bookingRepository.findStatusStatisticsByCreatedAtBetween(rangeStart, rangeEndExclusive))
                .thenReturn(List.of(
                        statusCount(BookingStatus.AWAIT_CONFIRMATION, 2L),
                        statusCount(BookingStatus.CONFIRMED, 1L),
                        statusCount(BookingStatus.CANCELLED, 1L)
                ));
        when(bookingRepository.findTopResourceStatisticsByCreatedAtBetween(rangeStart, rangeEndExclusive, topFive))
                .thenReturn(List.of(
                        resourceCount(10L, 2L),
                        resourceCount(20L, 1L),
                        resourceCount(30L, 1L)
                ));

        StatisticsResponse result = bookingService.getStatisticsByDate(dateFrom, dateTo);

        assertThat(result.getTotalBookings()).isEqualTo(4);
        assertThat(result.getStatusBreakdown()).containsExactlyInAnyOrderEntriesOf(Map.of(
                BookingStatus.NONE, 0L,
                BookingStatus.AWAIT_CONFIRMATION, 2L,
                BookingStatus.CONFIRMED, 1L,
                BookingStatus.CANCELLED, 1L,
                BookingStatus.CANCELLATION_PENDING, 0L
        ));
        assertThat(result.getTopResources()).containsExactly(10L, 20L, 30L);

        verify(bookingRepository).countByCreatedAtBetween(rangeStart, rangeEndExclusive);
        verify(bookingRepository).findStatusStatisticsByCreatedAtBetween(rangeStart, rangeEndExclusive);
        verify(bookingRepository).findTopResourceStatisticsByCreatedAtBetween(rangeStart, rangeEndExclusive, topFive);
        verifyNoInteractions(bookingEventPublisher, dateTimeProvider);
    }

    @Test
    void getStatisticsByDate_shouldThrowWhenDateFromIsAfterDateTo() {
        LocalDate dateFrom = LocalDate.of(2026, 7, 31);
        LocalDate dateTo = LocalDate.of(2026, 7, 1);

        assertThatThrownBy(() -> bookingService.getStatisticsByDate(dateFrom, dateTo))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(bookingRepository, bookingEventPublisher, dateTimeProvider);
    }

    @Test
    void handleBookingJobConfirmed_shouldConfirmCancellationPendingAndClearCancellationFields() {
        UUID requestId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 7, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime cancellationStartedAt = OffsetDateTime.of(2026, 7, 2, 10, 0, 0, 0, ZoneOffset.UTC);

        Booking booking = Booking.create(
                1L,
                10L,
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 12),
                createdAt
        );
        booking.setCatalogRequestId(requestId);
        booking.cancellationPending(cancellationStartedAt);

        when(bookingRepository.findByCatalogRequestId(requestId)).thenReturn(Optional.of(booking));

        bookingService.handleBookingJobConfirmed(requestId);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPrevStatus()).isNull();
        assertThat(booking.getSendCommandTime()).isNull();

        verify(bookingRepository).findByCatalogRequestId(requestId);
        verify(bookingRepository).save(booking);
        verifyNoInteractions(bookingEventPublisher, dateTimeProvider);
    }

    @Test
    void resendCancelBookingCommand_shouldUpdateSendCommandTimeSaveAndPublishCommand() {
        UUID requestId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 7, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime cancellationStartedAt = OffsetDateTime.of(2026, 7, 2, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime resentAt = OffsetDateTime.of(2026, 7, 2, 10, 15, 0, 0, ZoneOffset.UTC);
        Booking booking = pendingBooking(requestId, createdAt, cancellationStartedAt);

        when(dateTimeProvider.utcNow()).thenReturn(resentAt);

        bookingService.resendCancelBookingCommand(booking);

        ArgumentCaptor<CancelBookingJobByRequestIdRequest> commandCaptor =
                ArgumentCaptor.forClass(CancelBookingJobByRequestIdRequest.class);
        assertThat(booking.getSendCommandTime()).isEqualTo(resentAt);

        verify(dateTimeProvider).utcNow();
        verify(bookingRepository).save(booking);
        verify(bookingEventPublisher).publishCancelBookingJob(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getEventId()).isNotNull();
        assertThat(commandCaptor.getValue().getRequestId()).isEqualTo(requestId);
    }

    private BookingRepository.BookingStatusCountProjection statusCount(BookingStatus status, Long count) {
        return new BookingRepository.BookingStatusCountProjection() {
            @Override
            public BookingStatus getStatus() {
                return status;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    private BookingRepository.ResourceBookingCountProjection resourceCount(Long resourceId, Long count) {
        return new BookingRepository.ResourceBookingCountProjection() {
            @Override
            public Long getResourceId() {
                return resourceId;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    private Booking pendingBooking(UUID requestId, OffsetDateTime createdAt, OffsetDateTime cancellationStartedAt) {
        Booking booking = Booking.create(
                1L,
                10L,
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 12),
                createdAt
        );
        if (requestId != null) {
            booking.setCatalogRequestId(requestId);
        }
        booking.cancellationPending(cancellationStartedAt);
        return booking;
    }
}

package com.booking.service.service.impl;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.dto.response.StatisticsResponse;
import com.booking.service.entity.BookingStatus;
import com.booking.service.exception.BusinessException;
import com.booking.service.messaging.listener.BookingEventPublisher;
import com.booking.service.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
        PageRequest topFive = PageRequest.of(0, 5);

        when(bookingRepository.countByCreatedAtBetween(dateFrom, dateTo))
                .thenReturn(4L);
        when(bookingRepository.findStatusStatisticsByCreatedAtBetween(dateFrom, dateTo))
                .thenReturn(List.of(
                        statusCount(BookingStatus.AWAIT_CONFIRMATION, 2L),
                        statusCount(BookingStatus.CONFIRMED, 1L),
                        statusCount(BookingStatus.CANCELLED, 1L)
                ));
        when(bookingRepository.findTopResourceStatisticsByCreatedAtBetween(dateFrom, dateTo, topFive))
                .thenReturn(List.of(
                        resourceCount(10L, 2L),
                        resourceCount(20L, 1L),
                        resourceCount(30L, 1L)
                ));

        StatisticsResponse result = bookingService.getStatisticsByDate(dateFrom, dateTo);

        assertThat(result.getTotalBookings()).isEqualTo(4);
        assertThat(result.getStatusBreakdown()).containsExactlyInAnyOrderEntriesOf(Map.of(
                BookingStatus.AWAIT_CONFIRMATION, 2L,
                BookingStatus.CONFIRMED, 1L,
                BookingStatus.CANCELLED, 1L
        ));
        assertThat(result.getTopResources()).containsExactly(10L, 20L, 30L);

        verify(bookingRepository).countByCreatedAtBetween(dateFrom, dateTo);
        verify(bookingRepository).findStatusStatisticsByCreatedAtBetween(dateFrom, dateTo);
        verify(bookingRepository).findTopResourceStatisticsByCreatedAtBetween(dateFrom, dateTo, topFive);
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
}

package com.booking.service.service.impl;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.dto.response.StatisticsResponse;
import com.booking.service.entity.AuditStatusLog;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import com.booking.service.exception.BusinessException;
import com.booking.service.listeners.events.BookingStatusChangeReason;
import com.booking.service.listeners.events.BookingStatusChangedEvent;
import com.booking.service.messaging.contracts.CancelBookingJobByRequestIdRequest;
import com.booking.service.messaging.contracts.CreateBookingJobRequest;
import com.booking.service.messaging.listener.BookingEventPublisher;
import com.booking.service.repository.BookingRepository;
import com.booking.service.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher bookingEventPublisher;
    private final CurrentDateTimeProvider dateTimeProvider;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public Long createBooking(Long userId, Long resourceId, LocalDate bookedFrom, LocalDate bookedTo) {
        Booking booking = Booking.create(userId, resourceId, bookedFrom, bookedTo, dateTimeProvider.utcNow());

        UUID requestId = UUID.randomUUID();
        booking.setCatalogRequestId(requestId);

        booking = bookingRepository.save(booking);
        publishStatusChanged(
                booking,
                BookingStatus.NONE,
                BookingStatusChangeReason.BOOKING_CREATED,
                userInitiator(booking)
        );

        CreateBookingJobRequest command = new CreateBookingJobRequest(
                UUID.randomUUID(),
                requestId,
                booking.getResourceId(),
                booking.getBookedFrom(),
                booking.getBookedTo()
        );

        bookingEventPublisher.publishCreateBookingJob(command);

        log.info("Создано бронирование с ID: {} и requestId: {}", booking.getId(), requestId);
        return booking.getId();
    }

    @Override
    @Transactional
    public void resendCancelBookingCommand(Booking booking) {
        booking.markCancelCommandSent(dateTimeProvider.utcNow());
        bookingRepository.save(booking);

        sendCommandIfNeed(booking);

        log.info("Повторная отмена бронирования с ID: {}", booking.getId());
    }

    @Override
    public void cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Бронирование с указанным id: '" + id + "' не найдено."));

        BookingStatus oldStatus = booking.getStatus();
        booking.cancellationPending(dateTimeProvider.utcNow());

        bookingRepository.save(booking);
        publishStatusChanged(booking, oldStatus, BookingStatusChangeReason.CANCELLATION_REQUESTED, userInitiator(booking));

        sendCommandIfNeed(booking);

        log.info("Отменено бронирование с ID: {}", id);
    }

    private void sendCommandIfNeed(Booking booking) {
        if (booking.getCatalogRequestId() == null) {
            return;
        }
        CancelBookingJobByRequestIdRequest command = new CancelBookingJobByRequestIdRequest(
                UUID.randomUUID(),
                booking.getCatalogRequestId()
        );

        bookingEventPublisher.publishCancelBookingJob(command);
    }

    @Transactional(readOnly = true)
    @Override
    public Booking getById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Бронирование с указанным id: '" + id + "' не найдено."));
    }

    @Transactional(readOnly = true)
    @Override
    public List<Booking> getByFilter(Long userId, Long resourceId, BookingStatus status,
                                     int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        return bookingRepository.findByFilter(userId, resourceId, status, pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public BookingStatus getStatusById(Long id) {
        return bookingRepository.findStatusById(id);
    }

    @Transactional
    @Override
    public void handleBookingJobConfirmed(UUID requestId) {
        log.info("Получено событие BookingJobConfirmed: requestId={}", requestId);

        Booking booking = bookingRepository.findByCatalogRequestId(requestId).orElse(null);
        if (booking == null) {
            log.warn("Бронирование не найдено по requestId: {}. Событие проигнорировано.", requestId);
            return;
        }

        log.info("Найдено бронирование: id={}, статус={}. Подтверждаем...",
                booking.getId(), booking.getStatus());

        if (booking.getStatus() == BookingStatus.CANCELLATION_PENDING) {
            log.warn("Обнаружена Race condition при обработке BookingJobConfirmed: bookingId={}, requestId={}, prevStatus={}, sendCommandTime={}",
                    booking.getId(), requestId, booking.getPrevStatus(), booking.getSendCommandTime());
        }

        BookingStatus oldStatus = booking.getStatus();
        booking.confirm();
        bookingRepository.save(booking);
        publishStatusChanged(booking, oldStatus, BookingStatusChangeReason.BOOKING_CONFIRMED_BY_CATALOG, AuditStatusLog.SYSTEM_INITIATOR);

        log.info("Бронирование успешно подтверждено: id={}, новый статус={}",
                booking.getId(), booking.getStatus());
    }

    @Transactional
    @Override
    public void handleBookingJobDenied(UUID requestId) {
        log.info("Получено событие BookingJobDenied: requestId={}", requestId);

        Booking booking = bookingRepository.findByCatalogRequestId(requestId).orElse(null);
        if (booking == null) {
            log.warn("Бронирование не найдено по requestId: {}. Событие проигнорировано.", requestId);
            return;
        }

        log.info("Найдено бронирование: id={}, статус={}. Отменяем...",
                booking.getId(), booking.getStatus());

        LocalDate currentDate = LocalDate.from(dateTimeProvider.utcNow());
        BookingStatus oldStatus = booking.getStatus();
        booking.cancel(currentDate);
        bookingRepository.save(booking);
        publishStatusChanged(booking, oldStatus, BookingStatusChangeReason.BOOKING_DENIED_BY_CATALOG, AuditStatusLog.SYSTEM_INITIATOR);

        log.info("Бронирование успешно отменено: id={}, новый статус={}",
                booking.getId(), booking.getStatus());
    }

    @Transactional
    @Override
    public void handleError(UUID requestId) {
        log.info("Получено событие ошибки из DLQ: requestId={}", requestId);

        Booking booking = bookingRepository.findByCatalogRequestId(requestId).orElse(null);
        if (booking == null) {
            log.warn("Бронирование не найдено по requestId: {}. Событие проигнорировано.", requestId);
            return;
        }

        log.info("Найдено бронирование: id={}, статус={}, предыдущий статус={}, время отправки команды={}. " +
                        "Выполняем откат к предыдущему",
                booking.getId(), booking.getStatus(), booking.getPrevStatus(), booking.getSendCommandTime());

        BookingStatus oldStatus = booking.getStatus();
        booking.revertPendingCancellation();
        bookingRepository.save(booking);
        publishStatusChanged(booking, oldStatus, BookingStatusChangeReason.CANCELLATION_ERROR_HANDLED, AuditStatusLog.SYSTEM_INITIATOR);

        log.info("Статус бронирования был успешно откачен: id={}, новый статус={}",
                booking.getId(), booking.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public StatisticsResponse getStatisticsByDate(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom.isAfter(dateTo)) {
            throw new BusinessException("Дата начала не может быть позже даты конца.");
        }

        OffsetDateTime rangeStart = dateFrom.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime rangeEndExclusive = dateTo.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        long totalBookings = bookingRepository.countByCreatedAtBetween(rangeStart, rangeEndExclusive);

        Map<BookingStatus, Long> statusBreakdown = new EnumMap<>(BookingStatus.class);
        Arrays.stream(BookingStatus.values()).forEach(status -> statusBreakdown.put(status, 0L));
        bookingRepository.findStatusStatisticsByCreatedAtBetween(rangeStart, rangeEndExclusive)
                .forEach(row -> statusBreakdown.put(row.getStatus(), row.getCount()));

        List<Long> topResources = bookingRepository
                .findTopResourceStatisticsByCreatedAtBetween(rangeStart, rangeEndExclusive, PageRequest.of(0, 5))
                .stream()
                .map(BookingRepository.ResourceBookingCountProjection::getResourceId)
                .toList();

        return new StatisticsResponse(
                totalBookings,
                statusBreakdown,
                topResources
        );
    }

    private void publishStatusChanged(Booking booking,
                                      BookingStatus oldStatus,
                                      BookingStatusChangeReason reason,
                                      String initiator) {
        BookingStatus newStatus = booking.getStatus();
        if (oldStatus == newStatus) {
            return;
        }
        applicationEventPublisher.publishEvent(new BookingStatusChangedEvent(
                booking,
                oldStatus,
                newStatus,
                reason,
                initiator
        ));
    }

    private String userInitiator(Booking booking) {
        return String.valueOf(booking.getUserId());
    }
}

package com.booking.service.service.impl;

import com.booking.service.config.CurrentDateTimeProvider;
import com.booking.service.dto.response.StatisticsResponse;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import com.booking.service.exception.BusinessException;
import com.booking.service.messaging.contracts.CancelBookingJobByRequestIdRequest;
import com.booking.service.messaging.contracts.CreateBookingJobRequest;
import com.booking.service.messaging.listener.BookingEventPublisher;
import com.booking.service.repository.BookingRepository;
import com.booking.service.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher bookingEventPublisher;
    private final CurrentDateTimeProvider dateTimeProvider;

    @Override
    public Long createBooking(Long userId, Long resourceId, LocalDate bookedFrom, LocalDate bookedTo) {
        Booking booking = Booking.create(userId, resourceId, bookedFrom, bookedTo, dateTimeProvider.utcNow());

        UUID requestId = UUID.randomUUID();
        booking.setCatalogRequestId(requestId);

        booking = bookingRepository.save(booking);

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
    public void cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Бронирование с указанным id: '" + id + "' не найдено."));

        booking.cancellationPending(dateTimeProvider.utcNow());

        bookingRepository.save(booking);

        if (booking.getCatalogRequestId() != null) {
            CancelBookingJobByRequestIdRequest command = new CancelBookingJobByRequestIdRequest(
                    UUID.randomUUID(),
                    booking.getCatalogRequestId()
            );

            bookingEventPublisher.publishCancelBookingJob(command);
        }

        log.info("Отменено бронирование с ID: {}", id);
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

        booking.confirm();
        bookingRepository.save(booking);

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
        booking.cancel(currentDate);
        bookingRepository.save(booking);

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

        booking.revertPendingCancellation();
        bookingRepository.save(booking);

        log.info("Статус бронирования был успешно откачен: id={}, новый статус={}",
                booking.getId(), booking.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public StatisticsResponse getStatisticsByDate(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom.isAfter(dateTo)) {
            throw new BusinessException("Дата начала не может быть позже даты конца.");
        }

        long totalBookings = bookingRepository.countByCreatedAtBetween(dateFrom, dateTo);

        Map<BookingStatus, Long> statusBreakdown = bookingRepository
                .findStatusStatisticsByCreatedAtBetween(dateFrom, dateTo)
                .stream()
                .collect(Collectors.toMap(
                        BookingRepository.BookingStatusCountProjection::getStatus,
                        BookingRepository.BookingStatusCountProjection::getCount
                ));

        List<Long> topResources = bookingRepository
                .findTopResourceStatisticsByCreatedAtBetween(dateFrom, dateTo, PageRequest.of(0, 5))
                .stream()
                .map(BookingRepository.ResourceBookingCountProjection::getResourceId)
                .toList();

        return new StatisticsResponse(
                totalBookings,
                statusBreakdown,
                topResources
        );
    }
}

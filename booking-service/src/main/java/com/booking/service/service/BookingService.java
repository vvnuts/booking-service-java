package com.booking.service.service;

import com.booking.service.dto.response.StatisticsResponse;
import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Сервис для работы с бронированиями.
 * Объединяет CRUD операции, бизнес-логику и обработку событий.
 */
public interface BookingService {
    /**
     * Создать новое бронирование.
     * Отправляет асинхронную команду в Catalog Service для создания booking job.
     *
     * @return ID созданного бронирования
     */
    Long createBooking(Long userId, Long resourceId, LocalDate bookedFrom, LocalDate bookedTo);

    /**
     * Отменить бронирование.
     * Отправляет асинхронную команду в Catalog Service для отмены booking job.
     *
     * @param id идентификатор бронирования
     */
    void cancelBooking(Long id);

    /**
     * Получить бронирование по ID.
     *
     * @param id идентификатор бронирования
     * @return бронирование
     */
    Booking getById(Long id);

    /**
     * Получить бронирования по фильтрам с пагинацией.
     *
     * @param userId идентификатор пользователя (опционально)
     * @param resourceId идентификатор ресурса (опционально)
     * @param status статус бронирования (опционально)
     * @param pageNumber номер страницы
     * @param pageSize размер страницы
     * @return страница с бронированиями
     */
    List<Booking> getByFilter(Long userId, Long resourceId, BookingStatus status, int pageNumber, int pageSize);

    /**
     * Получить только статус бронирования по ID.
     *
     * @param id идентификатор бронирования
     * @return статус бронирования или null
     */
    BookingStatus getStatusById(Long id);

    /**
     * Обработать событие подтверждения booking job от Catalog Service.
     * Обновляет статус бронирования на CONFIRMED.
     *
     * @param requestId идентификатор запроса
     */
    void handleBookingJobConfirmed(UUID requestId);

    /**
     * Обработать событие отклонения booking job от Catalog Service.
     * Отменяет бронирование.
     *
     * @param requestId идентификатор запроса
     */
    void handleBookingJobDenied(UUID requestId);

    /**
     * Обработать событие ошибки от Catalog Service.
     *
     * @param requestId идентификатор запроса
     */
    void handleError(UUID requestId);

    /**
     * Получить статистику по событиям за дату
     *
     * @param dateFrom первый промежуток
     * @param dateTo второй промежуток
     * @return статистика
     */
    StatisticsResponse getStatisticsByDate(LocalDate dateFrom, LocalDate dateTo);
}

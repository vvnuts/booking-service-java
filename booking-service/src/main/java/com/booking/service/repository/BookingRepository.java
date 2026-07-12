package com.booking.service.repository;

import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA репозиторий для работы с бронированиями
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    interface BookingStatusCountProjection {
        BookingStatus getStatus();

        Long getCount();
    }

    interface ResourceBookingCountProjection {
        Long getResourceId();

        Long getCount();
    }

    /**
     * Найти бронирование по идентификатору запроса в Catalog Service
     * @param catalogRequestId идентификатор запроса
     * @return Optional с бронированием или empty
     */
    Optional<Booking> findByCatalogRequestId(UUID catalogRequestId);

    /**
     * Найти бронирования по фильтрам с пагинацией
     * Используется для получения списка бронирований с опциональными фильтрами
     *
     * @param userId идентификатор пользователя (опционально)
     * @param resourceId идентификатор ресурса (опционально)
     * @param status статус бронирования (опционально)
     * @param pageable параметры пагинации и сортировки
     * @return Page с результатами
     */
    @Query("SELECT b FROM Booking b WHERE " +
           "(:userId IS NULL OR b.userId = :userId) AND " +
           "(:resourceId IS NULL OR b.resourceId = :resourceId) AND " +
           "(:status IS NULL OR b.status = :status)")
    List<Booking> findByFilter(@Param("userId") Long userId,
                               @Param("resourceId") Long resourceId,
                               @Param("status") BookingStatus status,
                               Pageable pageable);

    /**
     * Найти бронирования, которые зависли в ожидании подтверждения отмены.
     *
     * @param status статус ожидания отмены
     * @param sentBefore максимальное время последней отправки команды
     * @return список бронирований, требующих повторной отправки команды отмены
     */
    @Query("""
            SELECT b
            FROM Booking b
            WHERE b.status = :status
              AND (b.sendCommandTime IS NULL OR b.sendCommandTime < :sentBefore)
            """)
    List<Booking> findByStatusAndSendCommandTimeBefore(
            @Param("status") BookingStatus status,
            @Param("sentBefore") OffsetDateTime sentBefore
    );

    /**
     * Получить только статус бронирования по ID
     * @param id идентификатор бронирования
     * @return статус бронирования или null
     */
    @Query("SELECT b.status FROM Booking b WHERE b.id = :id")
    BookingStatus findStatusById(@Param("id") Long id);

    @Query("""
            SELECT COUNT(b)
            FROM Booking b
            WHERE b.createdAt >= :dateFrom AND b.createdAt < :dateToExclusive
            """)
    long countByCreatedAtBetween(
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateToExclusive") OffsetDateTime dateToExclusive
    );

    @Query("""
            SELECT b.status AS status, COUNT(b) AS count
            FROM Booking b
            WHERE b.createdAt >= :dateFrom AND b.createdAt < :dateToExclusive
            GROUP BY b.status
            """)
    List<BookingStatusCountProjection> findStatusStatisticsByCreatedAtBetween(
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateToExclusive") OffsetDateTime dateToExclusive
    );

    @Query("""
            SELECT b.resourceId AS resourceId, COUNT(b) AS count
            FROM Booking b
            WHERE b.createdAt >= :dateFrom AND b.createdAt < :dateToExclusive
            GROUP BY b.resourceId
            ORDER BY COUNT(b) DESC, b.resourceId ASC
            """)
    List<ResourceBookingCountProjection> findTopResourceStatisticsByCreatedAtBetween(
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateToExclusive") OffsetDateTime dateToExclusive,
            Pageable pageable
    );
}

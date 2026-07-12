package com.booking.service.repository;

import com.booking.service.entity.AuditStatusLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для чтения и сохранения записей изменения статусов бронирований.
 */
@Repository
public interface AuditStatusLogRepository extends JpaRepository<AuditStatusLog, Long> {

    /**
     * Находит историю изменения статусов для одного бронирования.
     * Результат отсортирован от новых записей к старым, а идентификатор используется
     * как дополнительный ключ сортировки для стабильной пагинации.
     *
     * @param bookingId идентификатор бронирования
     * @param pageable параметры пагинации
     * @return страница записей аудита статусов
     */
    Page<AuditStatusLog> findByBookingIdOrderByChangedAtDescIdDesc(Long bookingId, Pageable pageable);
}

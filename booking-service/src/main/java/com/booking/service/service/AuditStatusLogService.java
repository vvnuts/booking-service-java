package com.booking.service.service;

import com.booking.service.dto.response.AuditStatusLogResponse;
import org.springframework.data.domain.Page;

/**
 * Сервис для работы с аудитом изменения статусов бронирований.
 */
public interface AuditStatusLogService {

    /**
     * Возвращает постраничную историю изменения статусов для указанного бронирования.
     *
     * @param bookingId идентификатор бронирования
     * @param pageNumber номер страницы, начиная с 0
     * @param pageSize размер страницы
     * @return страница записей аудита статусов
     */
    Page<AuditStatusLogResponse> getBookingHistory(Long bookingId, int pageNumber, int pageSize);
}

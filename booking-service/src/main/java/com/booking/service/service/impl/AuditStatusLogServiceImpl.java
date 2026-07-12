package com.booking.service.service.impl;

import com.booking.service.dto.response.AuditStatusLogResponse;
import com.booking.service.exception.BusinessException;
import com.booking.service.repository.AuditStatusLogRepository;
import com.booking.service.repository.BookingRepository;
import com.booking.service.service.AuditStatusLogService;
import com.booking.service.service.mapper.AuditStatusLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Реализация сервиса чтения аудита изменения статусов бронирований.
 */
@Service
@RequiredArgsConstructor
public class AuditStatusLogServiceImpl implements AuditStatusLogService {

    private final AuditStatusLogRepository auditStatusLogRepository;
    private final BookingRepository bookingRepository;
    private final AuditStatusLogMapper auditStatusLogMapper;

    /**
     * Возвращает постраничную историю изменения статусов для существующего бронирования.
     * Проверяет параметры пагинации и наличие бронирования перед чтением аудита.
     *
     * @param bookingId идентификатор бронирования
     * @param pageNumber номер страницы, начиная с 0
     * @param pageSize размер страницы
     * @return страница записей аудита статусов
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AuditStatusLogResponse> getBookingHistory(Long bookingId, int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new BusinessException("Page number must be greater than or equal to 0.");
        }
        if (pageSize < 1) {
            throw new BusinessException("Page size must be greater than 0.");
        }
        if (!bookingRepository.existsById(bookingId)) {
            throw new BusinessException("Отсутствует запись с id =" + bookingId);
        }

        return auditStatusLogRepository
                .findByBookingIdOrderByChangedAtDescIdDesc(bookingId, PageRequest.of(pageNumber, pageSize))
                .map(auditStatusLogMapper::toResponse);
    }
}

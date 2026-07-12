package com.booking.service.controller;

import com.booking.service.dto.response.AuditStatusLogResponse;
import com.booking.service.service.AuditStatusLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер для чтения аудита изменений статусов бронирований.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Validated
public class AuditController {

    private final AuditStatusLogService auditStatusLogService;

    /**
     * Возвращает постраничную историю изменений статуса указанного бронирования.
     * Записи отсортированы от новых к старым.
     *
     * @param id идентификатор бронирования
     * @param page номер страницы, начиная с 0
     * @param size размер страницы
     * @return страница записей аудита статусов
     */
    @GetMapping("{id}/history")
    public Page<AuditStatusLogResponse> getHistory(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size) {
        return auditStatusLogService.getBookingHistory(id, page, size);
    }
}

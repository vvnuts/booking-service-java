package com.booking.service.dto.response;

import com.booking.service.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO для передачи данных статистики
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsResponse {
    private Long totalBookings;
    private Map<BookingStatus, Long> statusBreakdown;
    private List<Long> topResources;
}

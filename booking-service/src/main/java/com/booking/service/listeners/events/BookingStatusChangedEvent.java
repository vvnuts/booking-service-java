package com.booking.service.listeners.events;

import com.booking.service.entity.Booking;
import com.booking.service.entity.BookingStatus;

/**
 * Доменное событие изменения статуса бронирования.
 */
public record BookingStatusChangedEvent(
        Booking booking,
        BookingStatus oldStatus,
        BookingStatus newStatus,
        BookingStatusChangeReason reason,
        String initiator
) {
}

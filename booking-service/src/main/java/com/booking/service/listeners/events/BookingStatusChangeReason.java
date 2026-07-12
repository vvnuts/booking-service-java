package com.booking.service.listeners.events;

import lombok.Getter;

/**
 * Причина изменения статуса бронирования для аудита.
 */
@Getter
public enum BookingStatusChangeReason {
    BOOKING_CREATED("Booking created"),
    CANCELLATION_REQUESTED("Cancellation requested"),
    BOOKING_CONFIRMED_BY_CATALOG("Booking confirmed by catalog"),
    BOOKING_DENIED_BY_CATALOG("Booking denied by catalog"),
    CANCELLATION_ERROR_HANDLED("Cancellation error handled");

    private final String value;

    BookingStatusChangeReason(String value) {
        this.value = value;
    }
}

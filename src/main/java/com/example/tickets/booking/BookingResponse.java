package com.example.tickets.booking;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(Long id, Long eventId, String owner, int quantity, BigDecimal totalPrice,
                              BookingStatus status, Instant createdAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getEventId(), booking.getOwnerUsername(),
                booking.getQuantity(), booking.getTotalPrice(), booking.getStatus(), booking.getCreatedAt());
    }
}

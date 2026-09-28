package com.example.tickets.event;

import java.math.BigDecimal;

public record EventResponse(Long id, String name, BigDecimal price, int totalSeats, int seatsLeft,
                            EventStatus status) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getPrice(), event.getTotalSeats(),
                event.getSeatsLeft(), event.getStatus());
    }
}

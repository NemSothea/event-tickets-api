package com.example.tickets.booking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Deliberately has no price field: the server computes the total (rule R4). */
public record CreateBookingRequest(
        @NotNull Long eventId,
        @NotNull @Min(1) @Max(4) Integer quantity) {
}

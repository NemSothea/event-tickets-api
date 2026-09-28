package com.example.tickets.booking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest request, Authentication auth) {
        return bookingService.create(request, auth.getName());
    }

    @GetMapping("/me")
    public List<BookingResponse> mine(Authentication auth) {
        return bookingService.findMine(auth.getName());
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable Long id, Authentication auth) {
        return bookingService.findById(id, auth.getName());
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, Authentication auth) {
        return bookingService.cancel(id, auth.getName());
    }
}

package com.example.tickets.booking;

import com.example.tickets.common.ConflictException;
import com.example.tickets.common.ForbiddenException;
import com.example.tickets.common.NotFoundException;
import com.example.tickets.event.Event;
import com.example.tickets.event.EventRepository;
import com.example.tickets.event.EventStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;

    public BookingService(BookingRepository bookingRepository, EventRepository eventRepository) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request, String username) {
        Long eventId = request.eventId();
        int quantity = request.quantity();

        // R1, R2, R5: check and decrement in one atomic statement.
        if (eventRepository.reserveSeats(eventId, quantity) == 0) {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new NotFoundException("Event " + eventId + " not found"));
            if (event.getStatus() == EventStatus.CANCELLED) {
                throw new ConflictException("Event " + eventId + " is cancelled");
            }
            throw new ConflictException("Not enough seats: requested " + quantity + ", left " + event.getSeatsLeft());
        }

        // R4: price always comes from the server.
        Event event = eventRepository.findById(eventId).orElseThrow();
        BigDecimal totalPrice = event.getPrice().multiply(BigDecimal.valueOf(quantity));

        Booking booking = bookingRepository.save(new Booking(eventId, username, quantity, totalPrice));
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findMine(String username) {
        return bookingRepository.findByOwnerUsernameOrderByIdAsc(username).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(Long id, String username) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> bookingNotFound(id));
        checkOwner(booking, username);
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse cancel(Long id, String username) {
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> bookingNotFound(id));
        checkOwner(booking, username);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking " + id + " is already cancelled");
        }

        booking.cancel();
        // R2: give the seats back in the same transaction.
        eventRepository.releaseSeats(booking.getEventId(), booking.getQuantity());
        return BookingResponse.from(booking);
    }

    // R6: customers only see and cancel their own bookings.
    private void checkOwner(Booking booking, String username) {
        if (!booking.isOwnedBy(username)) {
            throw new ForbiddenException("Booking " + booking.getId() + " belongs to another customer");
        }
    }

    private NotFoundException bookingNotFound(Long id) {
        return new NotFoundException("Booking " + id + " not found");
    }
}

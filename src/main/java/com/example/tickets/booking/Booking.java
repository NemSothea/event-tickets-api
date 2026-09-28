package com.example.tickets.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private String ownerUsername;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected Booking() {
    }

    public Booking(Long eventId, String ownerUsername, int quantity, BigDecimal totalPrice) {
        this.eventId = eventId;
        this.ownerUsername = ownerUsername;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.status = BookingStatus.CONFIRMED;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isOwnedBy(String username) {
        return ownerUsername.equals(username);
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }
}

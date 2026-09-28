package com.example.tickets.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Atomically takes seats from an ACTIVE event. The WHERE clause is the check and the
     * SET is the write, in one statement, so concurrent bookings cannot oversell.
     *
     * @return 1 if seats were reserved, 0 if the event is missing, cancelled or short on seats
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Event e SET e.seatsLeft = e.seatsLeft - :quantity
            WHERE e.id = :id AND e.seatsLeft >= :quantity
              AND e.status = com.example.tickets.event.EventStatus.ACTIVE
            """)
    int reserveSeats(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Event e SET e.seatsLeft = e.seatsLeft + :quantity WHERE e.id = :id")
    int releaseSeats(@Param("id") Long id, @Param("quantity") int quantity);
}

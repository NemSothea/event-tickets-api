package com.example.tickets.config;

import com.example.tickets.event.Event;
import com.example.tickets.event.EventRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private final EventRepository eventRepository;

    public DataSeeder(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public void run(String... args) {
        if (eventRepository.count() > 0) {
            return;
        }
        eventRepository.save(new Event("Rock Concert", new BigDecimal("25.00"), 100));

        Event jazz = new Event("Jazz Night", new BigDecimal("40.00"), 50);
        jazz.cancel();
        eventRepository.save(jazz);

        eventRepository.save(new Event("Tech Talk", new BigDecimal("10.00"), 20));
    }
}

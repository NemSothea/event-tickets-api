package com.example.tickets.event;

import com.example.tickets.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> findAll() {
        return eventRepository.findAll().stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EventResponse findById(Long id) {
        return EventResponse.from(getEvent(id));
    }

    @Transactional
    public EventResponse cancel(Long id) {
        Event event = getEvent(id);
        event.cancel();
        return EventResponse.from(event);
    }

    private Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event " + id + " not found"));
    }
}

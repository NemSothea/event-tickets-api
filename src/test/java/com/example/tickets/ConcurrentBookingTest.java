package com.example.tickets;

import com.example.tickets.event.Event;
import com.example.tickets.event.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentBookingTest {

    private static final int THREADS = 10;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    EventRepository eventRepository;

    @Test
    void s18_tenParallelRequestsForLastSeat_exactlyOneWins() throws Exception {
        Event event = eventRepository.save(new Event("Last seat", new BigDecimal("10.00"), 1));

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("alice", "password");
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(
                "{\"eventId\":" + event.getId() + ",\"quantity\":1}", headers);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return rest.postForEntity("/api/bookings", request, String.class).getStatusCode().value();
                }));
            }
            start.countDown();

            int created = 0;
            int conflicts = 0;
            for (Future<Integer> result : results) {
                int status = result.get();
                if (status == 201) {
                    created++;
                } else if (status == 409) {
                    conflicts++;
                }
            }

            assertThat(created).isEqualTo(1);
            assertThat(conflicts).isEqualTo(THREADS - 1);
        }

        assertThat(eventRepository.findById(event.getId()).orElseThrow().getSeatsLeft()).isZero();
    }
}

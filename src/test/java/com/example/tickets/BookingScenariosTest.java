package com.example.tickets;

import com.example.tickets.booking.BookingRepository;
import com.example.tickets.booking.BookingStatus;
import com.example.tickets.event.Event;
import com.example.tickets.event.EventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingScenariosTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void s01_happyPath_bookingReducesSeatsAndComputesPrice() throws Exception {
        Event event = newEvent("25.00", 10);

        book("alice", event.getId(), 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(50.00))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.owner").value("alice"));

        assertThat(seatsLeft(event)).isEqualTo(8);
    }

    @Test
    void s02_bookingExactLastSeats_succeeds() throws Exception {
        Event event = newEvent("10.00", 3);

        book("alice", event.getId(), 3).andExpect(status().isCreated());

        assertThat(seatsLeft(event)).isZero();
    }

    @Test
    void s03_bookingMoreThanSeatsLeft_returns409() throws Exception {
        Event event = newEvent("10.00", 3);

        book("alice", event.getId(), 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(seatsLeft(event)).isEqualTo(3);
    }

    @Test
    void s04_quantityZero_returns400() throws Exception {
        Event event = newEvent("10.00", 10);

        book("alice", event.getId(), 0).andExpect(status().isBadRequest());

        assertThat(seatsLeft(event)).isEqualTo(10);
    }

    @Test
    void s05_quantityFive_returns400() throws Exception {
        Event event = newEvent("10.00", 10);

        book("alice", event.getId(), 5).andExpect(status().isBadRequest());

        assertThat(seatsLeft(event)).isEqualTo(10);
    }

    @Test
    void s06_quantityMissing_returns400() throws Exception {
        Event event = newEvent("10.00", 10);

        postJson("/api/bookings", "alice", "{\"eventId\":" + event.getId() + "}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void s07_clientSentPriceIsIgnored() throws Exception {
        Event event = newEvent("25.00", 10);
        String body = "{\"eventId\":" + event.getId() + ",\"quantity\":2,\"price\":0.01,\"totalPrice\":0.01}";

        postJson("/api/bookings", "alice", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(50.00));
    }

    @Test
    void s08_bookingCancelledEvent_returns409() throws Exception {
        Event event = newEvent("10.00", 10);
        event.cancel();
        eventRepository.save(event);

        book("alice", event.getId(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Event " + event.getId() + " is cancelled"));

        assertThat(seatsLeft(event)).isEqualTo(10);
    }

    @Test
    void s09_bookingUnknownEvent_returns404() throws Exception {
        book("alice", 999_999L, 1).andExpect(status().isNotFound());
    }

    @Test
    void s10_viewingAnotherCustomersBooking_returns403() throws Exception {
        Long bookingId = createBooking("alice", newEvent("10.00", 10).getId(), 1);

        mvc.perform(get("/api/bookings/" + bookingId).with(httpBasic("bob", "password")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/bookings/" + bookingId).with(httpBasic("alice", "password")))
                .andExpect(status().isOk());
    }

    @Test
    void s11_cancellingAnotherCustomersBooking_returns403() throws Exception {
        Event event = newEvent("10.00", 10);
        Long bookingId = createBooking("alice", event.getId(), 2);

        postJson("/api/bookings/" + bookingId + "/cancel", "bob", "")
                .andExpect(status().isForbidden());

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(seatsLeft(event)).isEqualTo(8);
    }

    @Test
    void s12_cancellingOwnBooking_returnsSeats() throws Exception {
        Event event = newEvent("10.00", 10);
        Long bookingId = createBooking("alice", event.getId(), 2);
        assertThat(seatsLeft(event)).isEqualTo(8);

        postJson("/api/bookings/" + bookingId + "/cancel", "alice", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(seatsLeft(event)).isEqualTo(10);
    }

    @Test
    void s13_cancellingTwice_returns409AndDoesNotReturnSeatsTwice() throws Exception {
        Event event = newEvent("10.00", 10);
        Long bookingId = createBooking("alice", event.getId(), 2);

        postJson("/api/bookings/" + bookingId + "/cancel", "alice", "").andExpect(status().isOk());
        postJson("/api/bookings/" + bookingId + "/cancel", "alice", "").andExpect(status().isConflict());

        assertThat(seatsLeft(event)).isEqualTo(10);
    }

    @Test
    void s14_myBookingsListsOnlyOwnBookings() throws Exception {
        Long eventId = newEvent("10.00", 20).getId();
        createBooking("alice", eventId, 1);
        createBooking("bob", eventId, 1);

        String json = mvc.perform(get("/api/bookings/me").with(httpBasic("alice", "password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode bookings = objectMapper.readTree(json);
        assertThat(bookings).isNotEmpty();
        bookings.forEach(b -> assertThat(b.get("owner").asText()).isEqualTo("alice"));
    }

    @Test
    void s15_noCredentials_returns401() throws Exception {
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void s16_customerCannotCancelEvent_returns403() throws Exception {
        Event event = newEvent("10.00", 10);

        postJson("/api/events/" + event.getId() + "/cancel", "alice", "")
                .andExpect(status().isForbidden());
    }

    @Test
    void s17_adminCancelsEvent_thenBookingReturns409() throws Exception {
        Event event = newEvent("10.00", 10);

        postJson("/api/events/" + event.getId() + "/cancel", "admin", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        book("alice", event.getId(), 1).andExpect(status().isConflict());
    }

    @Test
    void s19_unknownBooking_returns404() throws Exception {
        mvc.perform(get("/api/bookings/999999").with(httpBasic("alice", "password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicCanListEvents() throws Exception {
        mvc.perform(get("/api/events")).andExpect(status().isOk());
    }

    private Event newEvent(String price, int seats) {
        return eventRepository.save(new Event("Test event", new BigDecimal(price), seats));
    }

    private int seatsLeft(Event event) {
        return eventRepository.findById(event.getId()).orElseThrow().getSeatsLeft();
    }

    private ResultActions book(String user, Long eventId, int quantity) throws Exception {
        return postJson("/api/bookings", user, "{\"eventId\":" + eventId + ",\"quantity\":" + quantity + "}");
    }

    private Long createBooking(String user, Long eventId, int quantity) throws Exception {
        String json = book(user, eventId, quantity)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private ResultActions postJson(String url, String user, String body) throws Exception {
        return mvc.perform(post(url)
                .with(httpBasic(user, "password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}

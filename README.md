# Event Tickets API

Spring Boot 3.5 / Java 21 / Gradle 8.14 (Kotlin DSL) / H2 in-memory. Design, rules, and scenarios: [docs/DESIGN.md](docs/DESIGN.md). Defense slides: [PPTX](docs/Event-Tickets-API-Defense.pptx) · [HTML](docs/Event-Tickets-API-Defense.html) (open in any browser; fonts load from Google Fonts).

## Run

```bash
./gradlew bootRun           # http://localhost:8080
./gradlew test              # 20 tests, incl. concurrency test
./gradlew bootJar           # build/libs/event-tickets-api-0.0.1-SNAPSHOT.jar
```

Port 8080 already taken (e.g. by Jenkins)? Run on another port: `./gradlew bootRun --args='--server.port=8081'`, and use that port in the URLs below.

Gradle 8.14 runs on JDK 17–24. If `JAVA_HOME` points to a newer JDK, set it to Java 21 first, e.g. `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`.

**Demo users (HTTP Basic):** `alice`/`password`, `bob`/`password` (CUSTOMER), `admin`/`password` (ADMIN).

**Seed events:** 1 Rock Concert 25.00 (100 seats, ACTIVE), 2 Jazz Night 40.00 (CANCELLED), 3 Tech Talk 10.00 (20 seats, ACTIVE).

## Endpoints

| Method | Path | Who |
|--------|------|-----|
| GET  | /api/events | public |
| GET  | /api/events/{id} | public |
| POST | /api/events/{id}/cancel | ADMIN |
| POST | /api/bookings | CUSTOMER |
| GET  | /api/bookings/me | CUSTOMER |
| GET  | /api/bookings/{id} | owner |
| POST | /api/bookings/{id}/cancel | owner |

Errors are RFC 7807 `ProblemDetail` JSON. There is no page at `/`, so opening the root URL returns a 404.

## Swagger UI

Browse and try every endpoint at http://localhost:8080/swagger-ui.html (OpenAPI JSON: `/v3/api-docs`). Both are public. For protected endpoints, click **Authorize** and log in with one of the demo users above.

## curl examples, one per rule

```bash
# List events (public) -> 200
curl -s localhost:8080/api/events

# Happy path: alice books 2 tickets for event 1 -> 201, totalPrice 50.00
curl -s -u alice:password -H 'Content-Type: application/json' \
  -d '{"eventId":1,"quantity":2}' localhost:8080/api/bookings

# R1 No overbooking: more tickets than seats left -> 409
#   (create a small event state first, e.g. book event 3 down to few seats, then ask for more than left)
curl -s -u alice:password -H 'Content-Type: application/json' \
  -d '{"eventId":3,"quantity":4}' localhost:8080/api/bookings   # repeat until seats < 4 -> 409

# R2 Seats stay correct: cancel booking 1 -> 200, event 1 seatsLeft back to 100
curl -s -u alice:password -X POST localhost:8080/api/bookings/1/cancel
curl -s localhost:8080/api/events/1

# R3 Max 4 per booking: quantity 5 (or 0) -> 400
curl -s -u alice:password -H 'Content-Type: application/json' \
  -d '{"eventId":1,"quantity":5}' localhost:8080/api/bookings

# R4 Price from server: fake client price ignored -> 201, totalPrice 50.00
curl -s -u alice:password -H 'Content-Type: application/json' \
  -d '{"eventId":1,"quantity":2,"price":0.01,"totalPrice":0.01}' localhost:8080/api/bookings

# R5 No booking a cancelled event: event 2 is CANCELLED -> 409
curl -s -u alice:password -H 'Content-Type: application/json' \
  -d '{"eventId":2,"quantity":1}' localhost:8080/api/bookings

# R6 Only your own bookings: bob reads / cancels alice's booking -> 403
curl -s -u bob:password localhost:8080/api/bookings/1
curl -s -u bob:password -X POST localhost:8080/api/bookings/1/cancel

# My bookings -> 200, only alice's
curl -s -u alice:password localhost:8080/api/bookings/me

# Admin cancels event 3 -> 200 (customer trying it -> 403)
curl -s -u admin:password -X POST localhost:8080/api/events/3/cancel
```

## How overbooking is prevented

Seats are taken with one conditional statement:

```sql
UPDATE Event e SET e.seatsLeft = e.seatsLeft - :q
WHERE e.id = :id AND e.seatsLeft >= :q AND e.status = 'ACTIVE'
```

Check and write happen atomically under the DB row lock. 0 rows updated means 404 / 409, decided by reloading the event. `ConcurrentBookingTest` fires 10 parallel requests at 1 seat: exactly 1 × 201, 9 × 409, seatsLeft 0.

## AI workflow

Built with [Claude Code](https://claude.com/claude-code), spec first:

1. Homework and the 6 business rules given to Claude Code.
2. Claude Code wrote [docs/DESIGN.md](docs/DESIGN.md) (rules, order of checks, atomic `UPDATE`); reviewed before any code.
3. Claude Code wrote the code and tests from that design.
4. Every change verified with `./gradlew test` and a diff review before commit.

Claude Code also wrote the README, Swagger setup, and defense slides. Every commit carries a `Co-Authored-By: Claude` trailer. Details: slides 12–13 of the defense deck.

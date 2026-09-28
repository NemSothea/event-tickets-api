# Event Tickets API — Design

## 1. What

Small REST API where customers book tickets for events. One Spring Boot module, H2 in-memory DB.

**Entities**

| Entity  | Fields |
|---------|--------|
| Event   | id, name, price (BigDecimal), totalSeats, seatsLeft, status `ACTIVE`/`CANCELLED`, version |
| Booking | id, eventId, ownerUsername, quantity, totalPrice (BigDecimal), status `CONFIRMED`/`CANCELLED`, createdAt |

**Users** (in-memory, HTTP Basic): `alice`/`password`, `bob`/`password` (CUSTOMER), `admin`/`password` (ADMIN).

**Endpoints**

| Method | Path | Who | Purpose |
|--------|------|-----|---------|
| GET  | /api/events | public | list events |
| GET  | /api/events/{id} | public | one event |
| POST | /api/events/{id}/cancel | ADMIN | cancel event |
| POST | /api/bookings | CUSTOMER | book `{eventId, quantity}` |
| GET  | /api/bookings/me | CUSTOMER | my bookings |
| GET  | /api/bookings/{id} | owner | one booking |
| POST | /api/bookings/{id}/cancel | owner | cancel booking, seats returned |

## 2. Why

| Rule | Why it matters |
|------|----------------|
| R1 No overbooking | Selling seats that don't exist = refunds, angry customers, lost trust. |
| R2 Seats stay correct | seatsLeft is the source of truth for R1; drift (lost updates, forgotten refunds) breaks everything downstream. |
| R3 Max 4 per booking | Fairness, anti-scalping; 0/negative quantities are nonsense input. |
| R4 Price from server | Client can send anything. Trusting client price = customer pays 0.01. |
| R5 No booking cancelled event | Event won't happen; taking money for it is wrong. |
| R6 Own bookings only | Privacy + prevents one customer cancelling another's tickets (IDOR). |

## 3. When — order of checks in booking flow

`POST /api/bookings`:

1. **Authentication** — no/invalid credentials → `401`.
2. **Authorization** — role not CUSTOMER → `403`.
3. **Validation** (R3) — `quantity` null, <1, >4, or `eventId` null → `400`. Happens before any DB work.
4. **Atomic seat reservation** (R1, R2, R5) — one conditional `UPDATE`.
5. If 0 rows updated, reload event to explain why:
   - event missing → `404`
   - status CANCELLED → `409` (R5)
   - otherwise not enough seats → `409` (R1)
6. **Price** (R4) — `totalPrice = event.price × quantity`, computed server-side.
7. Save booking (CONFIRMED) → `201`.

`POST /api/bookings/{id}/cancel`: 401 → 404 (missing) → 403 (not owner, R6) → 409 (already cancelled) → mark CANCELLED + return seats → `200`.

`GET /api/bookings/{id}`: 401 → 404 → 403 (not owner) → 200.

## 4. How

- **R1/R2 (booking):** single atomic statement in a `@Transactional` service:
  ```sql
  UPDATE Event e SET e.seatsLeft = e.seatsLeft - :q
  WHERE e.id = :id AND e.seatsLeft >= :q AND e.status = 'ACTIVE'
  ```
  DB row lock makes check + decrement one step; no read-check-write race. `seatsLeft` never goes negative.
- **R2 (cancel):** booking status → CANCELLED and `UPDATE Event SET seatsLeft = seatsLeft + :q` in same transaction. Booking row loaded with pessimistic write lock so a double cancel can't return seats twice.
- **R3:** Bean Validation on request DTO: `@NotNull @Min(1) @Max(4) Integer quantity`.
- **R4:** request DTO has no price field; unknown JSON fields ignored. Service computes `event.getPrice().multiply(BigDecimal.valueOf(quantity))`.
- **R5:** `status = 'ACTIVE'` in the conditional update; explained as 409 on failure.
- **R6:** service compares `booking.ownerUsername` with authenticated principal name → `AccessDeniedException`-style `ForbiddenException` → 403.
- **Errors:** `@RestControllerAdvice` returns RFC 7807 `ProblemDetail` for 400/403/404/409.
- **Layers:** controller → service → repository; DTO records in/out, entities never serialized.

## 5. Requirements

**Functional**
- F1 List/view events publicly.
- F2 Customer books 1–4 tickets on ACTIVE event with enough seats.
- F3 Customer lists own bookings, views/cancels own booking.
- F4 Admin cancels event.
- F5 All 6 business rules enforced with exact status codes.

**Non-functional**
- N1 Consistency: seatsLeft correct under concurrent bookings/cancels; never negative.
- N2 Money in BigDecimal, never double.
- N3 Validation at edge; server never trusts client price.
- N4 Errors in ProblemDetail JSON.
- N5 Authn via HTTP Basic, authz by role + ownership.

## 6. Scenarios

Seed: Event 1 "Rock Concert" price 25.00, 100 seats, ACTIVE. Event 2 "Jazz Night" price 40.00, 50 seats, CANCELLED. Event 3 "Tech Talk" price 10.00, 20 seats, ACTIVE. Tests create their own events where needed.

| ID | Given | When | Then |
|----|-------|------|------|
| S01 | ACTIVE event, price 25.00, 10 seats | alice books 2 | 201, totalPrice 50.00, seatsLeft 8 |
| S02 | ACTIVE event, 3 seats left | alice books 3 (exact last seats) | 201, seatsLeft 0 |
| S03 | ACTIVE event, 3 seats left | alice books 4 | 409, seatsLeft still 3 |
| S04 | any event | quantity 0 | 400 |
| S05 | any event | quantity 5 | 400 |
| S06 | any event | quantity missing/null | 400 |
| S07 | event price 25.00 | body includes `"price": 0.01, "totalPrice": 0.01`, quantity 2 | 201, totalPrice 50.00 |
| S08 | CANCELLED event | alice books 1 | 409, seats unchanged |
| S09 | eventId does not exist | alice books 1 | 404 |
| S10 | alice owns booking | bob GET /api/bookings/{id} | 403 |
| S11 | alice owns booking | bob cancels it | 403, booking still CONFIRMED, seats unchanged |
| S12 | alice owns booking of 2, seatsLeft 8 | alice cancels | 200, status CANCELLED, seatsLeft 10 |
| S13 | booking already CANCELLED | alice cancels again | 409, seats not returned twice |
| S14 | alice has bookings, bob has bookings | alice GET /api/bookings/me | only alice's bookings |
| S15 | no credentials | POST /api/bookings | 401 |
| S16 | customer alice | POST /api/events/{id}/cancel | 403 |
| S17 | admin | POST /api/events/{id}/cancel, then alice books | 200 then 409 |
| S18 | event with 1 seat left | 10 parallel requests book 1 each | exactly 1×201, 9×409, seatsLeft 0 |
| S19 | booking id does not exist | alice GET /api/bookings/{id} | 404 |

## 7. Implementation plan

- [ ] Gradle project (Kotlin DSL) + wrapper, Spring Boot 3.x, Java 21
- [ ] Entities, enums, repositories (atomic update queries)
- [ ] DTO records, exceptions, `@RestControllerAdvice` with ProblemDetail
- [ ] Security config: in-memory users, HTTP Basic, route rules, 401/403 handlers
- [ ] EventService + EventController
- [ ] BookingService + BookingController
- [ ] Seed data on startup
- [ ] MockMvc tests S01–S19 + concurrency test S18
- [ ] `./gradlew test` green, app starts
- [ ] README with curl examples

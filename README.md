# myCarPlanner

Service booking for the Hussarya workshop in Łódź. Owners of a Hussarya Thunder, Bastion or Zefir
register their car, request a service, and follow the job from appointment to pickup. Workshop
staff confirm appointments, claim and complete jobs, and every step is emailed to the customer,
ending with a PDF invoice.

- [Who uses it](#who-uses-it)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Demo walkthrough](#demo-walkthrough)
- [How it works](#how-it-works)
- [API](#api)
- [Configuration](#configuration)
- [Development](#development)
- [Project layout](#project-layout)

---

## Who uses it

| Who | Signs in at | Can |
|-----|-------------|-----|
| **Client** | `/login` (email) | Register and verify email; add and remove vehicles from the Hussarya catalog; request service with a drop-off time; cancel open bookings; download invoices; change password; delete the account |
| **Client service consultant** | `/employee-login` | Review incoming requests; accept with an appointment time (within workshop hours, checked against free mechanic capacity), reject, or cancel; see workshop progress and the archive |
| **Mechanic / apprentice mechanic** | `/employee-login` | Claim confirmed jobs; update the work plan and estimate; complete with a final cost (generates the invoice); cancel their own job |
| **Management** (`ADMIN`, `CEO`, `COO`) | `/employee-login` | Create workers, change their roles, delete them; search the full booking history (paged, 20 per page) |

Workshop: **Sportowa 31, Łódź, Poland**, open **06:00–20:00** for drop-off and pickup.

---

## Tech stack

| Part | Technology |
|------|------------|
| API | Java 21, Spring Boot 4, Spring Security (stateless JWT), Spring Data JPA, Flyway |
| Database | PostgreSQL 16 |
| Rate limiting | Redis 7, sliding window per IP and per user |
| Frontend | React 19, Vite 8, React Router 7, Axios |
| Emails | Clojure service (Ring + Jetty) that renders HTML and plain text, sent over SMTP |
| Invoices | PDFBox 3 |
| Local mail | MailHog |
| CI | GitHub Actions |

---

## Getting started

**Requirements:** Docker with Compose, and JDK 21 to build the API jar. Node and the Clojure CLI
are only needed if you work on those parts outside Docker.

```bash
./mvnw package -DskipTests
docker compose up --build
```

The API image runs the jar from `target/`, so build it first. Once the stack is up:

| Service | Address |
|---------|---------|
| Web app | http://localhost:5173 |
| API | http://localhost:8080 |
| MailHog (all outgoing mail) | http://localhost:8025 |
| Email renderer | http://localhost:3000 |
| PostgreSQL | `localhost:5432`, database `mycarplanner_db`, user/password `postgres` |
| Redis | `localhost:6379` |

`docker compose ps` should list six running containers; `db-init` exits after it creates
`mycarplanner_db` and `mycarplanner_test`.

### Demo accounts and data

On an empty database the API creates ready-to-use accounts, so a demo can start on any screen:

| Who | Signs in at | Login | Password |
|-----|-------------|-------|----------|
| Admin | `/employee-login` | `admin` | `admin` |
| CEO (management) | `/employee-login` | `ceo` | `secret123` |
| Client service consultant | `/employee-login` | `consultant1` | `secret123` |
| Mechanic | `/employee-login` | `mechanic1` | `secret123` |
| Apprentice mechanic | `/employee-login` | `apprentice1` | `secret123` |
| Client with a Thunder and a Bastion | `/login` | `anna.nowak@example.com` | `secret123` |
| Client with a Zefir | `/login` | `jan.kowalski@example.com` | `secret123` |

Both clients are already verified, and there is one booking in every status, dated around the day
the database was created:

| Status | Booking |
|--------|---------|
| `SCHEDULED` | Anna's Bastion, waiting for the consultant to accept |
| `READY_FOR_WORK` | Jan's Zefir, appointment tomorrow at 10:00, waiting for a mechanic to claim it |
| `IN_PROGRESS` | Anna's Thunder, today, assigned to `mechanic1` |
| `COMPLETED` | Anna's Thunder two weeks ago, final cost 1375.50 EUR, invoice ready to download |
| `CANCELLED` | One rejected by the workshop (Jan) and one cancelled by the client (Anna) |

The demo data is written straight to the database, so it sends no emails. It is only added while
there are no workers and no customers; if your database already has data, reset it (below) to get
it. Set `APP_DEMO_DATA=false` to start with only the `admin` account.

### Resetting the database

```bash
docker compose down        # stop Postgres first, or the data directory can be left corrupted
rm -rf pgdata
./mvnw package -DskipTests
docker compose up --build
```

Day to day, `docker compose stop` and `docker compose start` keep your data.

---

## Demo walkthrough

**Quick tour with the demo data:** sign in as `consultant1` and accept Anna's Bastion request;
sign in as `mechanic1` and claim it, or complete the Thunder job already in progress; then sign
in as `anna.nowak@example.com` to see the bookings, the archive and the invoice.

**Full flow from scratch**, including registration and every email:

1. Register a client at http://localhost:5173/register.
2. Open the verification email in MailHog (http://localhost:8025) and follow the link.
3. Sign in at http://localhost:5173/login.
4. **Vehicles → Add vehicle**: pick a model, body, engine, transmission and year, then enter a VIN.
5. **Request service**: choose the car, describe the issue and pick a drop-off date and a time
   between 06:00 and 20:00.
6. Sign out and sign in at http://localhost:5173/employee-login as `admin` / `admin`. Use the
   demo staff, or create your own **client service consultant** and **mechanic**.
7. Sign in as the consultant and accept the request with an appointment time.
   The client gets *appointment confirmed*.
8. Sign in as the mechanic and claim the job. The client gets *drop off your vehicle*.
   Optionally update the work plan, then complete it with a final cost.
9. The client gets *service completed* with the invoice attached, and can download it again
   from **Bookings → Archive**.

Also try **Forgot password** on the sign-in page, **Account → Delete account** (confirmed by email),
and the light/dark switch in the top bar.

---

## How it works

```
React app ──Bearer JWT──► Spring Boot API ──► PostgreSQL
                                │
                                ├──► Redis (rate-limit counters)
                                ├──► Clojure email renderer ──► subject, HTML and text
                                └──► SMTP (MailHog locally) ──► email, plus invoice PDF
```

### Booking lifecycle

```
SCHEDULED ──consultant accepts──► READY_FOR_WORK ──mechanic claims──► IN_PROGRESS ──completes──► COMPLETED
    │                                   │                                  │
    │ reject / cancel                   │ cancel                           │ cancel
    └───────────────────────────────────┴──────────────────────────────────┴──► CANCELLED
```

| Status | Meaning |
|--------|---------|
| `SCHEDULED` | Client request waiting for a consultant |
| `READY_FOR_WORK` | Appointment confirmed; waiting for a mechanic to claim it |
| `IN_PROGRESS` | Claimed by a mechanic or apprentice |
| `COMPLETED` | Done; invoice available |
| `CANCELLED` | Rejected by the workshop, or cancelled by the client, consultant or mechanic. A reason is always required |

Claims and accepts lock the booking row (`SELECT … FOR UPDATE`), so two mechanics claiming the
same job at once cannot both succeed.

### Emails and invoices

Every state change writes a `PENDING` row to `email_outbox` in the same transaction. After the
commit the API asks the Clojure renderer (`POST /render/booking-email` or
`/render/customer-email`) for the subject, HTML and text, then sends the email. If rendering or
SMTP fails, or the API crashes, a poller retries the row with increasing delays until it reaches
`max_attempts` and is marked `FAILED`.

| Event | Sent when |
|-------|-----------|
| `customer_registered`, `email_verification` | Sign-up, or a new verification link is requested |
| `password_reset`, `password_changed` | Reset requested, or password changed |
| `account_deletion`, `account_deleted` | Deletion requested, then confirmed |
| `vehicle_added`, `vehicle_removed` | A car is added or removed |
| `created` | Client submits a request |
| `appointment_scheduled` | Consultant confirms the appointment |
| `technician_assigned` | Mechanic claims the job; asks the client to drop the car off |
| `work_plan_updated` | Mechanic changes the planned work or estimate |
| `booking_rejected`, `booking_cancelled` | Workshop declines, or anyone cancels |
| `booking_completed` | Job done; invoice PDF attached (if it cannot be generated, the email points to the download instead), pickup hours included |

Emails and the invoice use the same look as the web app: white cards on `#f5f5f7`, `#1d1d1f`
text, blue `#0071e3` pill buttons and the Hussarya logo (`email/logo.png`, kept identical in
`src/main/resources` and the renderer's `resources`). Mail clients that support it get a dark
version. The invoice is generated by `InvoicePdfService`, both for the completion email and
`GET /bookings/{id}/invoice`.

The invoice follows the Polish VAT invoice layout (*faktura VAT*):

- **Number.** Completing a job assigns the next number from a database sequence, `FV/2026/000042`.
  The number and issue date are stored on the booking, so a re-downloaded invoice is identical.
- **Seller and buyer.** The seller block shows the company name, address and NIP; the buyer block
  shows the client's name and email.
- **Dates.** The issue date and the date of service both use the completion date.
- **Amounts.** The final cost is VAT-inclusive. The invoice shows net, VAT (23% by default) and
  gross, with net rounded to the cent and VAT taken as the difference, so the two always add up to
  the total.

### Security

- Passwords are hashed with BCrypt. Email, reset and deletion links use one-time tokens, and only
  their SHA-256 hashes are stored.
- Clients cannot sign in until their email is verified.
- The API is stateless: each request carries `Authorization: Bearer <JWT>`. The acting user always
  comes from the token, never from ids in the request body, and "me" routes such as
  `/customers/me/bookings` are preferred.
- A token stops working as soon as its account is deleted, whether a client deleted their own
  account or management deleted a staff member.
- Rate limits are set in `application.properties`. Every `/auth` endpoint is strict (3–10 requests
  per minute): per IP for anonymous callers, per account when a token is sent, so a token cannot
  be used to escape the limit. Everything else falls back to 600 per minute per caller.
  If Redis is down, requests are allowed through.
- Route guards in the React app only improve the experience; the API checks access on every call.

---

## API

Public routes: the `/auth` endpoints below (except change password and account deletion request),
`GET /vehicles/catalog` and `GET /hello`. Everything else needs a token.

### Accounts

| Endpoint | Who | Does |
|----------|-----|------|
| `POST /auth/register` | Public | Creates an unverified client and emails a verification link |
| `POST /auth/login` | Public | Client sign-in (`login` = email, `password`); needs a verified email |
| `POST /auth/employee-login` | Public | Staff sign-in (`login` = login name, `password`) |
| `POST /auth/verify-email` | Public | Verifies the email with a one-time token |
| `POST /auth/resend-verification` | Public | Sends a new verification link |
| `POST /auth/forgot-password` | Public | Emails a reset link (same response whether or not the email exists) |
| `POST /auth/reset-password` | Public | Sets a new password with a one-time token |
| `POST /auth/change-password` | Signed in | Changes the password (current password required, minimum 8 characters) |
| `POST /auth/request-account-deletion` | Client | Emails a confirmation link; open bookings must be closed first |
| `POST /auth/confirm-account-deletion` | Public | Deletes the account with a one-time token |

Sign-in returns `{ token, role, id, name }`.

### Bookings

| Endpoint | Who | Does |
|----------|-----|------|
| `POST /bookings` | Client | Requests service for one of their cars; the drop-off must be in the future, between 06:00 and 20:00 |
| `GET /customers/me/bookings` | Client | Their bookings |
| `POST /bookings/{id}/accept` | Consultant | Sets the appointment (future, 06:00–20:00, a mechanic free), moves to `READY_FOR_WORK` |
| `POST /bookings/{id}/reject` | Consultant | Declines a `SCHEDULED` request |
| `POST /bookings/{id}/claim` | Mechanic | Takes a `READY_FOR_WORK` job, moves to `IN_PROGRESS` |
| `POST /bookings/{id}/work-plan` | Assigned mechanic | Updates services and estimate |
| `POST /bookings/{id}/complete` | Assigned mechanic | Sets the final cost, moves to `COMPLETED`, issues the invoice number |
| `POST /bookings/{id}/cancel` | Client, consultant, assigned mechanic | Cancels with a reason |
| `GET /bookings/{id}/invoice` | Owning client | Invoice PDF for a completed booking |
| `GET /workers/me/bookings`, `/workers/me/awaiting-workshop`, `/workers/me/consultant-archive` | Staff | Role-specific queues |
| `GET /workers/me/workshop-capacity` | Consultant | Free mechanics at a proposed appointment time; never available outside 06:00–20:00 |

Vehicles live under `/vehicles/me` (list, add, history, `vin-available`), and management under
`/admins` (`/admins/workers`, `/admins/bookings`).

`GET /admins/bookings` is paged and searchable: `?page=0&size=20&q=thunder`. `q` matches the
booking number (`#42`), customer, email, model, VIN, assigned worker, description, cancellation
reason or status (`in progress`). Results are newest first:

```json
{ "items": [ { "id": 42, "status": "COMPLETED" } ], "page": 0, "size": 20, "totalItems": 57, "totalPages": 3 }
```

### Example with curl

```bash
token() {  # usage: token <endpoint> <login> <password>
  curl -s -X POST "http://localhost:8080/auth/$1" -H 'Content-Type: application/json' \
    -d "{\"login\":\"$2\",\"password\":\"$3\"}" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])"
}

CLIENT=$(token login you@example.com yourpassword)
CONSULTANT=$(token employee-login consultant1 secret123)
MECHANIC=$(token employee-login mechanic1 secret123)

curl -s -X POST http://localhost:8080/bookings \
  -H "Authorization: Bearer $CLIENT" -H 'Content-Type: application/json' \
  -d '{"vehicleId":1,"customerDescription":"Noise when braking","estimatedDropOffTime":"2027-06-15T10:30:00","availabilityNotes":"Tuesday mornings"}'

curl -s -X POST http://localhost:8080/bookings/1/accept \
  -H "Authorization: Bearer $CONSULTANT" -H 'Content-Type: application/json' \
  -d '{"scheduledDateTime":"2027-06-16T14:00:00"}'

curl -s -X POST http://localhost:8080/bookings/1/claim \
  -H "Authorization: Bearer $MECHANIC" -H 'Content-Type: application/json' \
  -d '{"estimatedCost":350.00,"serviceTypes":["Brake inspection"]}'

curl -s -X POST http://localhost:8080/bookings/1/complete \
  -H "Authorization: Bearer $MECHANIC" -H 'Content-Type: application/json' \
  -d '{"finalCost":375.50}'
```

---

## Configuration

Defaults live in `src/main/resources/application.properties`; `docker-compose.yml` sets the
container values.

| Variable | Default | Purpose |
|----------|---------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/mycarplanner_db` | Database |
| `APP_JWT_SECRET` | development value | JWT signing key, at least 32 characters. **Change it outside local demos** |
| `APP_JWT_EXPIRATION_MS` | `86400000` (24 h) | Token lifetime |
| `APP_FRONTEND_URL` | `http://localhost:5173` | Base for links in emails |
| `EMAIL_RENDERER_URL` | `http://localhost:3000` | Clojure renderer |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` | `mailhog`, `1025`, `noreply@mycarplanner.local` | SMTP |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` | `localhost`, `6379` | Redis |
| `APP_PASSWORD_RESET_TTL_SECONDS` | `3600` | Reset link lifetime |
| `APP_EMAIL_VERIFICATION_TTL_SECONDS` | `86400` | Verification link lifetime |
| `APP_EMAIL_OUTBOX_POLL_MS`, `APP_EMAIL_OUTBOX_BATCH_SIZE` | `5000`, `20` | Outbox retry poller |
| `APP_DEMO_DATA` | `true` | Seed demo staff, clients, cars and bookings into an empty database |
| `APP_INVOICE_SELLER_NAME` | `Hussarya Service sp. z o.o.` | Seller name on invoices |
| `APP_INVOICE_SELLER_NIP` | `123-456-32-18` | Seller NIP on invoices (the default is the standard example number) |
| `APP_INVOICE_VAT_RATE` | `23` | VAT rate in percent; prices are treated as gross |

Behind a reverse proxy, set `app.rate-limit.trusted-header` (e.g. `X-Real-Ip`) so limits apply to
the real client IP.

The demo `admin` account (`DemoAuthDataLoader`) and the demo data (`DemoDataLoader`, password
`secret123` for everyone) are for local demos only; turn them off before any real deployment.

---

## Development

### Rebuilding containers

| Changed | Run |
|---------|-----|
| Java API | `./mvnw package -DskipTests && docker compose up -d --build --force-recreate app` |
| Email templates | `docker compose up -d --build --force-recreate email-renderer` |
| Frontend code | Nothing; `mycarplanner-frontend/` is mounted and hot-reloads |
| Frontend dependencies or Dockerfile | `docker compose up -d --build frontend` |

### Frontend

```bash
cd mycarplanner-frontend
npm install
npm run dev     # http://localhost:5173, expects the API on :8080
npm run lint
npm run build
```

The design follows apple.com and targets WCAG 2.2 AA and valid HTML. There are light and dark
themes (light by default; the choice is saved in `localStorage`). See
[`mycarplanner-frontend/README.md`](mycarplanner-frontend/README.md) for theming, brand assets and
accessibility rules.

Unknown URLs show a "Page not found" page. A top-level error boundary catches rendering errors
and shows a reload screen instead of a blank page.

### Database

Flyway applies `src/main/resources/db/migration/V*.sql` on startup; Hibernate only validates the
mapping. Add a new `V<n>__description.sql` for every schema change rather than editing old ones.
`MigrationChecksumTest` fails if a released migration changes; add the new file's hash there too.

### Vehicle catalog

Models, body styles, engines and production years come from
`src/main/resources/hussarya-catalog.json`. When a client adds a car, its specs are copied onto
the vehicle so later catalog edits don't change past bookings. The marketing copy on the landing
page (`mycarplanner-frontend/src/constants/models.js`) mirrors this file.

### Tests

```bash
./mvnw test
```

The backend tests are Spring Boot integration tests against the `mycarplanner_test` database on
`localhost:5432` (created by `docker compose`; Redis is not needed). They cover authorization
between roles (including tokens of deleted accounts), the booking lifecycle (workshop hours,
invoice numbering and the PDF contents), the paged booking history search, concurrent claims,
account flows and deletion, password changes, VIN uniqueness, the email outbox and rate limiting.
`InvoiceVatTest` checks the net/VAT split, `BookingNotificationDeliveryTest` checks that the
completion email only claims an attached invoice when there is one, and `MigrationChecksumTest`
fails if a released Flyway migration is edited.
The test profile keeps the connection pool at 3, because the local Postgres allows only 20
connections.

```bash
# Email renderer, without installing the Clojure CLI
docker run --rm -v "$PWD/clojure/email-renderer:/app" -w /app \
  clojure:temurin-21-tools-deps clojure -M:test
```

CI (`.github/workflows/ci.yml`) runs the Maven tests and package against Postgres and Redis, plus
frontend lint and build, on every push and pull request.

---

## Project layout

```
myCarPlanner/
├── src/main/java/com/sanproject/service_planner/
│   ├── catalog/        Hussarya catalog loader and option models
│   ├── config/         CORS, async, error handling, demo accounts and data
│   ├── controller/     REST endpoints
│   ├── domain/         JPA entities and enums
│   ├── dto/            Request and response bodies
│   ├── email/          Outbox, renderer client, SMTP delivery
│   ├── ratelimit/      Redis sliding-window limiter and filter
│   ├── repository/     Spring Data repositories
│   ├── security/       JWT, password hashing, AuthSupport
│   └── service/        Business logic and the invoice PDF
├── src/main/resources/
│   ├── application.properties
│   ├── db/migration/   Flyway SQL
│   ├── email/logo.png  Logo used by the invoice
│   └── hussarya-catalog.json
├── src/test/           Integration tests
├── clojure/email-renderer/
│   ├── src/            Layout and templates for booking and account emails
│   ├── resources/      Email logo
│   └── test/
├── mycarplanner-frontend/
│   ├── public/         Logo, car photos, favicon (web-sized copies in img/)
│   └── src/            pages/, components/, theme/, services/, hooks/, constants/, utils/
├── .github/workflows/  CI
├── Dockerfile          API image (runs the jar from target/)
└── docker-compose.yml  Full local stack
```

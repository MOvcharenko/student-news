# Rationale — Student News

This page records the six decisions that give Student News its shape, each with the drivers it
serves, the alternatives that were turned down and what it costs. Per-view reasoning stays in
each view's §5. No ADD design session was run for this system, so these records are the
canonical decision log; they describe decisions already visible in the code.

## ADR-1: One container per tier, composed with Docker Compose

- **Status**: accepted · 2026-11-11
- **Drivers addressed**: `CON-2`, `CON-3`, `DQA-1`, `BG-1`

**Context.** The application has a static UI, an API and a database, and students must be able
to start all of it on a laptop with one command (`CON-2`). The course teaches containers and
Compose (`BG-1`).

**Decision.** Each tier is its own image and container — frontend (nginx), backend (Spring
Boot), db (PostgreSQL) — composed in `docker-compose.yml` with a health-checked start order.

**Rationale.** One command starts a stack whose structure matches the lecture diagrams, and
each image can be built and tested on its own in CI (`DQA-1`).

**Alternatives considered.** Serving the built UI from Spring Boot's static resources would save
one container but hide the frontend's own build and server from students. Running the
services directly on the host needs a local JDK, Node and PostgreSQL, which breaks `CON-2`.

**Consequences.** Three images to build and keep current. The browser has to reach two
published ports, which leads to ADR-2.

## ADR-2: The browser calls the API directly, with CORS open

- **Status**: accepted · 2026-11-11
- **Drivers addressed**: `CON-1`, `CON-2`, `DQA-1`

**Context.** The Web UI is served from port 5173 and the API listens on port 8080, so the
browser treats API calls as cross-origin.

**Decision.** The Web UI calls `http://localhost:8080/api/news` directly; the base URL is fixed
when the frontend is built (`VITE_API_URL`). `CorsConfig` allows every origin on `/api/**`.

**Rationale.** nginx keeps its default configuration and the frontend image stays a plain static
server, so the stack has fewer moving parts to explain and to break (`CON-2`, `DQA-1`).

**Alternatives considered.** An nginx reverse proxy for `/api` would make the UI and API one
origin, remove the need for CORS and publish only one port. It needs a custom nginx
configuration and would be the first change for any shared or production deployment.

**Consequences.** An open CORS policy is safe here only because nothing is protected (`CON-1`);
adding authentication would make this decision wrong. Changing the API address needs a
rebuilt frontend image.

## ADR-3: Flyway owns the schema; Hibernate only validates it

- **Status**: accepted · 2026-11-11
- **Drivers addressed**: `TQA-1`, `MQA-1`, `RQA-1`

**Context.** The same schema has to exist in the Compose database, in every Testcontainers test
and on a developer's database, and must change without losing data.

**Decision.** SQL scripts in `db/migration` define the schema; Flyway applies them at API
startup. Hibernate runs with `ddl-auto=validate` and fails the startup on any mismatch.

**Rationale.** Schema changes are reviewed as code and applied the same way everywhere, so the
integration tests exercise the real schema (`TQA-1`) and existing data survives upgrades
(`RQA-1`).

**Alternatives considered.** `ddl-auto=update` lets Hibernate alter tables silently, without
history and without a way to express data migrations.

**Consequences.** Every entity change needs a matching migration script. `baseline-on-migrate`
is enabled so an older database without Flyway history can still be adopted.

## ADR-4: Three backend layers with records at the HTTP boundary

- **Status**: accepted · 2026-11-11
- **Drivers addressed**: `TQA-1`, `MQA-1`, `UC-1`

**Context.** The API is small, but it is teaching material: the code should show where
validation, business rules and persistence belong.

**Decision.** `controller` → `service` → `repository`, dependencies only downward; requests and
responses are Java records (`NewsRequest`, `NewsResponse`); the entity never leaves the
service.

**Rationale.** The service can be unit-tested with a mocked repository and no database
(`TQA-1`); the JSON shape and the table can change independently (`MQA-1`); validation sits on
the request record, before business code runs (`UC-1`).

**Alternatives considered.** A controller that talks to the repository directly would be
shorter, but would mix HTTP, rules and persistence and leave nothing to unit-test without a
database.

**Consequences.** One mapping method (`toResponse`) to keep in step with the entity.

## ADR-5: Small, non-root images with a cached dependency layer

- **Status**: accepted · 2026-11-11
- **Drivers addressed**: `SQA-1`, `MQA-1`, `DQA-1`

**Context.** The images are what students build and run in the Docker lecture; they should show
the practices taught there and rebuild quickly during development.

**Decision.** Multi-stage builds: dependencies resolved in their own layer before the source is
copied; a JRE-only Alpine runtime for the backend and nginx-unprivileged for the frontend; both
run as non-root users; `.dockerignore` keeps local build output out of the build context;
credentials come from the environment, with a committed `.env.example`.

**Rationale.** A one-line Java change rebuilds in seconds instead of about 76 s (`MQA-1`); no
application process runs as root and no image contains a password (`SQA-1`); the stack still
starts with no configuration file (`DQA-1`).

**Alternatives considered.** A single-stage image with a full JDK and Maven would be larger and
would run as root. Docker secrets would protect credentials better than environment variables
but add setup that `CON-2` rules out for a demo.

**Consequences.** Environment variables are visible through `docker inspect`; they keep
credentials out of images and Git, not away from someone with access to the Docker host.

## ADR-6: Tests split by what they need, across parallel CI jobs

- **Status**: accepted · 2026-11-11
- **Drivers addressed**: `TQA-1`, `CON-4`

**Context.** Unit tests need nothing, integration tests need PostgreSQL, and E2E tests need the
whole stack and a browser. Running them all in one job would make every pull request wait for
the slowest.

**Decision.** `ci.yml` runs lint, unit tests, integration tests (Testcontainers) and the
frontend build in parallel; image packaging waits for all four; one `CI status` job summarises
the run. `e2e.yml` runs the Playwright suite against the Compose stack in parallel with it.

**Rationale.** A pull request gets a result in about 2 minutes (`TQA-1`), and branch protection
can require one stable check (`CON-4`).

**Alternatives considered.** Leaving the integration tests out of CI, as an earlier `ci.yml`
did, meant the ten HTTP-to-database tests ran nowhere; one sequential job would be simpler to
read but several times slower.

**Consequences.** Two workflows and six jobs to maintain; the same backend is compiled in three
places (unit, integration and the image build).

---

**Navigation**: [docs README](README.md) · [architecture package](../README.md)

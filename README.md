# Student News Demo

[![CI](https://github.com/rtym/student-news/actions/workflows/ci.yml/badge.svg)](https://github.com/rtym/student-news/actions/workflows/ci.yml)
[![E2E (Playwright)](https://github.com/rtym/student-news/actions/workflows/e2e.yml/badge.svg)](https://github.com/rtym/student-news/actions/workflows/e2e.yml)

Demo application for managing student news without authentication.

Supported operations:
- list news
- create news drafts
- edit existing news
- publish news
- archive news
- delete news

---

## Architecture

The solution follows the [C4 model](https://c4model.com/) at **context (C1)**, **container (C2)**, and **component (C3)** levels. Diagrams use [Mermaid C4 syntax](https://mermaid.js.org/syntax/c4.html) (experimental in some renderers; use a recent Mermaid-compatible viewer if a diagram does not render).

**C1 — System context:** who uses the system and what the system is responsible for, without internal technology.

**C2 — Containers:** runnable/deployable units (web app, API, database) and how they communicate.

**C3 — Components:** major building blocks *inside* each container (here: backend Spring Boot and frontend React).

### C1 — System context

```mermaid
C4Context
    title Student News — System context (C1)
    Person(user, "Demo user", "Uses a browser to create, edit, publish, archive, and delete student news")
    System(studentNews, "Student News", "Demo system for managing student news (no authentication)")
    Rel(user, studentNews, "Uses", "HTTPS")
```

### C2 — Containers

```mermaid
C4Container
    title Student News — Containers (C2)
    Person(user, "Demo user", "Operates the app from a browser")
    System_Boundary(sn, "Student News") {
        Container(web, "Web application", "React, Vite, Nginx", "Single-page UI; static assets served to the browser")
        Container(api, "API application", "Java 21, Spring Boot", "REST API, validation, OpenAPI, Flyway on startup")
        ContainerDb(db, "Database", "PostgreSQL 16", "Stores news and Flyway schema history")
    }
    Rel(user, web, "Uses", "HTTPS")
    Rel(web, api, "Calls", "JSON / REST")
    Rel(api, db, "Reads and writes", "JDBC")
```

Local deployment: **Docker Compose** builds and runs these three containers together (see `docker-compose.yml`).

### C3 — Components (backend: API application)

```mermaid
C4Component
    title API application — Components (C3)
    Container_Boundary(api, "API application (Spring Boot)") {
        Component(ctrl, "News REST layer", "Spring Web MVC", "HTTP endpoints under /api/news")
        Component(svc, "News service", "Spring", "Create, update, publish, archive, delete, list logic")
        Component(repo, "News repository", "Spring Data JPA", "Persistence for NewsItem entities")
        Component(migrate, "Schema migrations", "Flyway", "SQL scripts in classpath db/migration")
    }
    ContainerDb(db, "PostgreSQL", "PostgreSQL 16", "Tables e.g. news_items, flyway_schema_history")
    Rel(ctrl, svc, "Uses")
    Rel(svc, repo, "Uses")
    Rel(repo, db, "JPA / JDBC")
    Rel(migrate, db, "Applies migrations on startup")
```

### C3 — Components (frontend: web application)

```mermaid
C4Component
    title Web application — Components (C3)
    Container_Boundary(web, "Web application (React + Nginx)") {
        Component(ui, "Student News UI", "React", "List, filters, forms, publish/archive/delete actions")
        Component(client, "HTTP client", "fetch API", "JSON requests to configured API base URL")
    }
    System_Ext(api, "API application", "Spring Boot REST backend")
    Rel(ui, client, "Uses")
    Rel(client, api, "REST", "JSON over HTTP")
```

---

## Tech stack

- PostgreSQL 16
- Java 21 + Spring Boot 3
- Spring Data JPA + Hibernate
- React 18 + Vite
- Docker + Docker Compose

---

## Project structure

```text
.
├── backend/              # Spring Boot API
├── frontend/             # React UI
├── e2e/                  # Playwright UI E2E tests
├── docs/                 # BDD scenarios
└── docker-compose.yml    # Local deployment
```

---

## Prerequisites

- Docker Desktop installed and running
- Docker Compose v2 (`docker compose`)

---

## Quick start

From the project root:

```bash
docker compose up --build
```

When containers are ready:

- Frontend: [http://localhost:5173](http://localhost:5173)
- Backend API: [http://localhost:8080/api/news](http://localhost:8080/api/news)
- Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- PostgreSQL: `localhost:5432`

Stop everything:

```bash
docker compose down
```

Stop and remove DB volume (clean reset):

```bash
docker compose down -v
```

---

## API

Base URL: `http://localhost:8080/api/news`

OpenAPI specification is auto-generated from code and available at:
- `GET /v3/api-docs` (JSON)
- Swagger UI: `/swagger-ui/index.html`

### Endpoints

- `GET /api/news` - list all news
- `GET /api/news?status=DRAFT|PUBLISHED|ARCHIVED` - list by status
- `POST /api/news` - create draft news
- `PUT /api/news/{id}` - edit news title/content
- `PATCH /api/news/{id}/publish` - mark as published
- `PATCH /api/news/{id}/archive` - mark as archived
- `DELETE /api/news/{id}` - delete news

### Create/update payload

```json
{
  "title": "Exam schedule announced",
  "content": "The exam period starts on June 3."
}
```

### Example requests

Create draft:

```bash
curl -X POST http://localhost:8080/api/news \
  -H "Content-Type: application/json" \
  -d '{"title":"Campus open day","content":"Open day starts at 10:00"}'
```

Publish news with id `1`:

```bash
curl -X PATCH http://localhost:8080/api/news/1/publish
```

Archive news with id `1`:

```bash
curl -X PATCH http://localhost:8080/api/news/1/archive
```

Delete news with id `1`:

```bash
curl -X DELETE http://localhost:8080/api/news/1
```

---

## Database configuration

Default DB settings used by Docker Compose:

- database: `student_news`
- user: `student`
- password: `student`

The backend reads:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

---

## Database migrations (Flyway)

Schema changes are managed with Flyway SQL migrations.

- migration folder: `backend/src/main/resources/db/migration`
- naming format: `V{number}__{description}.sql`
- example: `V2__add_news_summary_column.sql`

Current setup:
- Flyway runs automatically on backend startup
- Hibernate uses `validate` mode (it validates schema, but does not create/update tables)

To add a new migration:
1. Create a new SQL file in `db/migration` with next version number.
2. Put DDL/DML changes in this file.
3. Rebuild/restart backend (`docker compose up --build`).

If you already have a non-empty database without Flyway history, `baseline-on-migrate=true` is enabled for easier local onboarding.

---

## Testing (backend)

From `backend/`:

```bash
mvn test
```

- **Unit tests** (`NewsServiceTest`): Mockito-based; no database.
- **E2E REST tests** (`NewsApiE2ETest`): `@SpringBootTest` + `@AutoConfigureMockMvc` + **Testcontainers** PostgreSQL. They exercise the full stack (HTTP → controller → service → JPA → real DB, with Flyway migrations). **Docker must be running** so Testcontainers can start Postgres.

Shared Testcontainers configuration lives in `AbstractPostgreSQLIntegrationTest`.

### UI E2E tests (Playwright)

Browser tests drive the real React UI (clicks, typing) against a **Docker Compose** stack (Postgres + API + Nginx frontend), so the full path **UI → REST → DB** is exercised.

From the repository root:

```bash
cd e2e
npm ci
npx playwright install chromium
npm test
```

Default behaviour:

- **Global setup** runs `docker compose up -d --build` from the repo root, then waits until `http://localhost:8080/api/news` and `http://localhost:5173/` respond.
- **Global teardown** runs `docker compose down -v` (also mirrored in CI with `if: always()`).

If the stack is already running and you want to **skip** Compose management:

```bash
cd e2e
E2E_SKIP_DOCKER=1 npm test
```

Optional environment variables (defaults suit local Docker Compose port mapping):

| Variable | Purpose |
|----------|---------|
| `E2E_SKIP_DOCKER` | Set to `1` to skip `docker compose up` / `down` in Playwright global hooks |
| `PLAYWRIGHT_BASE_URL` | UI base URL (default `http://localhost:5173`) |
| `E2E_API_NEWS_URL` | REST base for test cleanup via `APIRequestContext` (default `http://localhost:8080/api/news`) |
| `E2E_API_HEALTH_URL` | URL polled in global setup (default `http://localhost:8080/api/news`) |
| `E2E_WEB_HEALTH_URL` | URL polled in global setup (default `http://localhost:5173/`) |

HTML report (after a run): `cd e2e && npx playwright show-report`

### CI (GitHub Actions)

Workflow [`.github/workflows/e2e.yml`](.github/workflows/e2e.yml) runs on pushes and pull requests to `main` / `master`: installs Playwright Chromium, runs `npm test` in `e2e/` (which starts Docker via global setup), uploads the Playwright HTML report on failure, and always runs `docker compose down -v`.

---

## Notes

- This project is intentionally simple and does **not** include authentication/authorization.
- It is designed for demo and local development purposes.

---

## Functional requirements (BDD)

BDD scenarios are documented in separate files:

- [API testing scenarios (BDD)](docs/api-testing-scenarios-bdd.md)
- [Frontend user scenarios (BDD)](docs/frontend-user-scenarios-bdd.md)

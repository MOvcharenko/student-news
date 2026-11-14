# Mapping between views — Student News

These tables connect each code unit in the module views to the runtime component it becomes in
`view-cnc-client-server.md` and to the container it runs in, per `view-allocation-deployment.md`.
They also show which element realises each driver. Use them to follow one element through
every view.

## Module → runtime component (C&C)

| Module (`view-module-decomposition.md`, `view-module-layered.md`) | Runtime component (`view-cnc-client-server.md`) | Notes |
|----------------------------------|------------------------------------------|-------|
| `com.example.studentnews` web, service and persistence layers | News API | Many to one: all three layers run in one process |
| `com.example.studentnews.config` (`CorsConfig`, `OpenApiConfig`) | News API | Sets the CORS policy and the OpenAPI document of the same process |
| `backend` `db/migration` | Schema migrator | Flyway runs inside the News API process at startup |
| `frontend/src` | Web UI, and the content of the Static web server | Built once by Vite; served by nginx, executed by the browser |
| `backend` `src/test` | none | Runs only in CI: unit and integration jobs (`view-allocation-ci.md`) |
| `e2e` | none | Runs only in CI or by hand; drives the Web UI through Chromium |
| `.github`, `docker-compose.yml` | none | Configuration: decides which containers start and which jobs run |

## Runtime component (C&C) → deployment node (Allocation)

| Runtime component | Deployment node (`view-allocation-deployment.md`) | Notes |
|-------------------|-----------------------------------------------|-------|
| Web UI | Web browser on the host | Downloaded from the frontend container on every page load |
| Static web server | frontend container | One instance; stateless; runs as uid 101 |
| News API | backend container | One instance; stateless; runs as uid 10001 |
| Schema migrator | backend container | Same JVM as the News API, at startup only |
| News database | db container, data in volume `postgres_data` | One instance; the only stateful element |

## Driver → element realising it

| Driver | Realised by |
|--------|-------------|
| `UC-1`, `UC-3` | `NewsController.create` and `update`, `NewsService`, `NewsRepository`; News API; backend container |
| `UC-2` | `NewsService.list`, `NewsRepository.findAllByStatusOrderByUpdatedAtDesc`; News API and News database |
| `UC-4`, `UC-5` | `NewsService.publish`, `archive` and `delete`; News API; backend container |
| `UC-6` | springdoc in the News API; `/v3/api-docs` and Swagger UI on port 8080 |
| `CON-1` | No authentication layer; `CorsConfig` open to every origin |
| `CON-2`, `DQA-1` | `docker-compose.yml` with defaults for every variable; three containers on one host |
| `MQA-1` | `backend/Dockerfile` dependency layer (`mvn dependency:go-offline` before `COPY src`); backend image build |
| `TQA-1`, `CON-4` | `src/test` and `e2e`; the unit, integration, package and E2E jobs (`view-allocation-ci.md`) |
| `SQA-1` | `USER` in both runtime images; `127.0.0.1:5432` binding; `POSTGRES_*` passed as environment variables; frontend, backend and db containers |
| `RQA-1` | Named volume `postgres_data` attached to the db container |

---

**Navigation**: [docs README](README.md) · [architecture package](../README.md)

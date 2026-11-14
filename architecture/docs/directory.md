# Directory — Student News

Where each element of this description is defined, what the terms and acronyms mean, and which
ID prefixes the package uses.

## Index — where elements are defined

| Element | Defined in | Also appears in |
|---------|-----------|-----------------|
| BG-1, UC-1 to UC-6, CON-1 to CON-4, quality scenarios | `system-overview.md` | every view's §5, `rationale.md`, `mapping-between-views.md` |
| `backend`, `frontend`, `e2e`, `.github` | `view-module-decomposition.md` §2.1 | `view-allocation-ci.md`, `mapping-between-views.md` |
| `NewsController`, `NewsService`, `NewsRepository`, `NewsItem`, `NewsStatus`, `NewsRequest`, `NewsResponse`, `NotFoundException` | `view-module-layered.md` §2.1 | `view-cnc-client-server.md` §2.4 |
| Web UI, Static web server, News API, Schema migrator, News database | `view-cnc-client-server.md` §2.1 | `view-allocation-deployment.md`, `mapping-between-views.md` |
| REST interface `/api/news` | `view-cnc-client-server.md` §2.3 | `view-module-layered.md` §2.3 |
| NewsItem status lifecycle | `view-cnc-client-server.md` §2.4 | `view-module-layered.md` (`NewsStatus`) |
| frontend, backend and db containers, `postgres_data` | `view-allocation-deployment.md` §2.1 | `mapping-between-views.md` |
| CI jobs (lint, unit, integration, build, package, CI status, E2E) | `view-allocation-ci.md` §2.1 | `rationale.md` ADR-6 |
| ADR-1 to ADR-6 | `rationale.md` | every view's §5 |

## Glossary

| Term | Meaning |
|------|---------|
| Views and Beyond | The SEI method used here: describe the architecture as a set of views, plus documentation that ties them together. |
| View | One picture of the system's elements and relations of one kind, chosen to answer a stakeholder's question. |
| Viewtype | The family a view belongs to: Module (code units), Component-and-Connector (runtime parts), Allocation (mapping to hardware, files or teams). |
| Connector | The runtime interaction between components, here HTTP and JDBC. |
| Driver | Anything that shapes the architecture: a business goal, use case, constraint or quality attribute scenario. |
| Quality attribute scenario | A measurable requirement in six parts: source, stimulus, artifact, environment, response, response measure. |
| Single-page application | A web UI loaded once that updates the page itself instead of loading new pages from the server. |
| Record | A Java class that only carries data, here used for request and response bodies. |
| Entity | A Java class mapped to a database table by JPA; here `NewsItem`. |
| Migration | A versioned SQL script that Flyway applies once, in order. |
| Named volume | Storage managed by Docker that outlives the containers using it. |
| Compose service name | The name of a service in `docker-compose.yml`, usable as a host name by the other containers. |
| Testcontainers | A test library that starts real dependencies, here PostgreSQL, in throwaway containers. |
| Fork / join | UML activity notation for steps that run in parallel and for waiting until all of them finish. |

## Acronyms

| Acronym | Expansion |
|---------|-----------|
| API | Application Programming Interface |
| ADR | Architecture Decision Record |
| CI/CD | Continuous Integration / Continuous Delivery |
| CORS | Cross-Origin Resource Sharing |
| C&C | Component-and-Connector |
| DNS | Domain Name System |
| E2E | End-to-end (tests) |
| JDBC | Java Database Connectivity |
| JPA | Jakarta Persistence API |
| JRE | Java Runtime Environment |
| SEI | Software Engineering Institute (Carnegie Mellon University) |
| SPA | Single-page application |
| SQL | Structured Query Language |
| UML | Unified Modeling Language |
| uid | User identifier (Linux) |

## Driver ID prefixes used here

`BG-` business goal · `UC-` use case · `CON-` constraint · `DQA-` deployability · `MQA-`
modifiability · `TQA-` testability · `SQA-` security · `RQA-` reliability · `ADR-` architecture
decision record.

---

**Navigation**: [docs README](README.md) · [architecture package](../README.md)

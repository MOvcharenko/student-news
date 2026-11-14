# Architecture package — Student News

- **Date**: 2026-11-11
- **Owner**: Roman Tymoshuk
- **Design purpose**: brownfield — the existing system documented as built
- **Stakeholders**: listed with their concerns in [docs/README.md](docs/README.md#view-selection--stakeholder-concerns--views)

This folder holds the architecture description of Student News, made with the SEI method. The
table lists each activity of the method, its output and whether it has been done; only the
Views-and-Beyond description exists so far.

## Activities and outputs

| Activity | Command | Output | Status |
|----------|---------|--------|--------|
| Architectural drivers | `/drivers` | summarised in [docs/system-overview.md](docs/system-overview.md) | not run as a separate register |
| Quality attribute scenarios | `/quality-attributes` | summarised in [docs/system-overview.md](docs/system-overview.md#driving-quality-attribute-scenarios) | not run as a separate activity |
| Quality Attribute Workshop | `/qaw` | — | not run |
| ADD 3.0 design | `/add` | — | not run |
| ATAM evaluation | `/atam` | — | not run |
| Views and Beyond documentation | `/architecture-docs` | [docs/README.md](docs/README.md) | done 2026-11-11 |
| Deliverable reports | `build-report.sh` | — | not run |

## Change log

| Date | Activity | Change |
|------|----------|--------|
| 2026-11-11 | `/architecture-docs` | package created; Views-and-Beyond description with UML diagrams, drivers summarised in the system overview |

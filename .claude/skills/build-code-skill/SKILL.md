---
name: build-code-skill
description: Use whenever writing or modifying backend (Java/Spring Boot) or frontend (Angular) code in this repo. Covers package layout, dependency-injection style, REST response shapes, and component/service conventions. Load this before implementing any feature spec.
---

# Build Code Skill — ServiceForge stack conventions

## Backend (Java 17 + Spring Boot 3)

**Package layout — package by layer, not by feature:**
```
com.serviceforge.model        - plain data classes (Technician, Job, JobStatus, ...)
com.serviceforge.data         - in-memory mock data stores
com.serviceforge.service      - business logic
com.serviceforge.controller   - REST endpoints
com.serviceforge.dto          - request/response shapes that aren't raw models
```

**Dependency injection:** constructor injection only. No `@Autowired` on fields.

**REST conventions:**
- Every endpoint returns `ResponseEntity<T>`.
- Not-found → `404` with an `ApiError` body (`{ "message": "..." }`).
- Bad input → `400` with an `ApiError` body.
- Successful creation → `201` with the created resource.

**Mock data, not a database:** there is no JDBC/JPA dependency in this repo, and none should be added without a recorded decision in `pipeline/decisions/`. All state lives in `MockDataStore`, seeded at startup, in-memory only (state resets on restart — this is expected and fine for this repo's purpose).

## Frontend (Angular)

**Structure:** one component per screen area under `src/app/`; a `services/` folder for anything that talks to the backend; a `models/` folder for TypeScript interfaces mirroring the backend DTOs.

**Rule:** components render and delegate. No `HttpClient` calls inside a component — always go through a service.

**HTTP base URL:** read from `environment.apiBaseUrl`, never hardcoded in a component or service body.

## Both sides

Do not introduce a new external dependency (a database driver, an auth library, a new frontend UI kit) without checking whether it should first be a recorded decision — see `.claude/skills/migration-safety-skill/SKILL.md` for the data-model case specifically.

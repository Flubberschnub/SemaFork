# Semafork

Semafork is a small web app for deciding where a group should eat. A host creates a party and shares a six-character code. Participants join without accounts, submit restaurant ideas, and cast one vote. The result is finalized automatically when everyone votes, or manually by the host when someone is unavailable.

## MVP scope

The MVP intentionally includes only the complete group decision flow:

- Anonymous party creation and joining
- Shareable party codes and invite links
- Participant-authenticated suggestions and votes
- Host-authenticated start and finalize actions
- One vote per participant, enforced by PostgreSQL
- Random tie breaking among the top choices
- Mobile-friendly browser UI served by Spring Boot
- Flyway migrations, integration tests, health checks, and container packaging

It intentionally does not include user accounts, maps, restaurant APIs, recommendations, WebSockets, or native mobile apps.

## Run locally

Requirements:

- Java 21
- Docker

Start the application:

```bash
./mvnw spring-boot:run
```

Spring Boot starts the PostgreSQL service from `compose.yaml`. Open `http://localhost:8080`.

Run the tests:

```bash
./mvnw verify
```

The integration tests use Testcontainers and therefore require Docker.

## Run with containers

Set a database password and launch both the app and PostgreSQL:

```bash
POSTGRES_PASSWORD=replace-this docker compose -f compose.prod.yaml up --build
```

Open `http://localhost:8080`. For an internet deployment, place the application behind HTTPS and retain the PostgreSQL volume.

## API overview

The browser client uses these endpoints:

- `POST /api/parties` — create a party and host session
- `POST /api/parties/join` — join by party code
- `GET /api/parties/code/{joinCode}` — read party state
- `GET|POST /api/parties/{partyId}/suggestions` — list or add suggestions
- `POST /api/parties/{partyId}/voting/start` — host starts voting
- `POST /api/parties/{partyId}/voting/votes` — participant casts a vote
- `POST /api/parties/{partyId}/voting/finalize` — host finishes with current votes
- `GET /api/parties/{partyId}/voting` — read voting status and final results

Host and participant tokens are anonymous, opaque session credentials stored in the browser. They are sent using `X-Host-Token` and `X-Member-Token` headers.

Swagger UI is available at `/swagger-ui.html` during development.

## Definition of done

Semafork reaches MVP when:

1. A host can create a party and share an invite link.
2. Multiple people can join from separate browsers without accounts.
3. Joined participants can submit unique restaurant suggestions.
4. Only the host can start or manually finish voting.
5. Each participant can vote exactly once and cannot act as another participant.
6. The party always reaches a result, even when not every participant votes.
7. Ties produce a valid winner and final vote totals are shown only after finalization.
8. The complete flow works from the included mobile-friendly UI.
9. Database migrations and integration tests pass on a clean PostgreSQL instance.
10. The application can be started using either Maven plus Docker or the production Compose file.

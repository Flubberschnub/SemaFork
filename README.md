# Semafork

Semafork is a small web app for deciding where a group should eat. A host creates a party and shares a six-character code. Participants join without accounts, pick restaurants on Google Maps or submit names, and cast one vote. The result is finalized automatically when everyone votes, or manually by the host when someone is unavailable.

## Product scope

- Anonymous party creation and joining, with invite links
- Participant-authenticated suggestions and votes; host-only start and finish
- One vote per participant enforced by PostgreSQL; random tie breaking
- Google Maps restaurant autocomplete and selection by clicking business labels
- Numbered candidate markers during suggestions, voting and results
- Candidate cards with photos and credits, rating, rating count, dollar-sign price level and address when Google provides them
- Expandable review excerpts with authors, extra photos and directions links
- Manual suggestions still work without Maps configuration or during a provider outage
- Mobile-friendly browser UI, served by Spring Boot without a separate frontend build
- Flyway migrations, PostgreSQL integration tests, mocked browser regressions, and container packaging

No accounts, recommendation engine, WebSockets, or native mobile applications.

## Run locally

Requirements: Java 21 and Docker. Start the application:

```bash
# On Unix, use `sh mvnw` if the wrapper is not executable.
sh mvnw spring-boot:run
```

On Windows use `mvnw.cmd spring-boot:run`. Spring Boot starts PostgreSQL from `compose.yaml`. Open `http://localhost:8080`.

### Enable Google Maps (optional)

Create a billing-enabled Google Cloud project, enable **Maps JavaScript API** and **Places API (New)**, and create a dedicated browser key restricted to those APIs and your actual website origins. Do not use an unrestricted or server credential.

```bash
export GOOGLE_MAPS_BROWSER_KEY="your-restricted-browser-key"
export GOOGLE_MAPS_MAP_ID="your-javascript-map-id"
sh mvnw spring-boot:run
```

PowerShell: set `$env:GOOGLE_MAPS_BROWSER_KEY` and `$env:GOOGLE_MAPS_MAP_ID` before running `mvnw.cmd spring-boot:run`.

`GOOGLE_MAPS_MAP_ID` defaults to `DEMO_MAP_ID` for development. The browser key is necessarily visible to visitors; website and API restrictions protect it. No key is included in this repository. The application reads configuration at startup, so restart after changing it. A missing key leaves the manual flow available.

See [Google Maps implementation, setup and acceptance checklist](docs/google-maps.md) for data handling, billing/quotas, attribution and live testing. Public hosting and Google billing/key activation are operator setup, not provisioned by this repository.

## Tests

```bash
sh mvnw verify
```

Backend integration tests use Testcontainers and require Docker. Browser tests run the actual static application with a mocked party API and a Google SDK test double; they do not require Google credentials or incur Maps usage:

```bash
python -m pip install -r tests/requirements.txt
python -m playwright install chromium
python -m unittest discover -s tests -p 'test_*.py' -v
```

The test double exists only under `tests/` and is not packaged with the app. Passing these regressions does not establish that a real Google key is authorized; perform the live checklist before release. GitHub Actions runs both suites.

## Run with containers

```bash
POSTGRES_PASSWORD=replace-this docker compose -f compose.prod.yaml up --build
```

Compose also passes `GOOGLE_MAPS_BROWSER_KEY` and `GOOGLE_MAPS_MAP_ID` from the host environment or an ignored `.env` file. Maven does not read `.env` automatically. Open `http://localhost:8080`. For internet deployment use HTTPS, a non-default database password, persistent PostgreSQL storage, and a restricted Maps key with quotas. Review the included privacy/terms pages against the deployment operator's real policies.

## API overview

- `POST /api/parties` — create a party and host session
- `POST /api/parties/join` — join by party code
- `GET /api/parties/code/{joinCode}` — read party state
- `GET|POST /api/parties/{partyId}/suggestions` — list or add suggestions
- `POST /api/parties/{partyId}/voting/start` — host starts voting
- `POST /api/parties/{partyId}/voting/votes` — participant casts a vote
- `POST /api/parties/{partyId}/voting/finalize` — host finishes with current votes
- `GET /api/parties/{partyId}/voting` — read status and final results
- `GET /api/config/maps` — public browser-only Maps configuration, with no-store caching

Host and participant tokens are anonymous credentials stored in the browser and sent using `X-Host-Token` and `X-Member-Token`. A suggestion POST takes exactly one of `{"name":"Manual restaurant label"}` or `{"googlePlaceId":"ChIJ..."}`. A Maps candidate returns `name: null`; resolve its current display information from its Place ID. Use `winnerSuggestionId` to find the winning candidate, including when `winnerSuggestionName` is null for a Maps pick.

Only stable Place IDs are saved for Maps picks. Google names, coordinates, ratings, review text and photos are not persisted by Semafork. Ordinary polling never refetches unchanged Google details; summaries are loaded once per page display session, and reviews/extra photos only when opened. Different branches are distinct Place IDs; the same Place ID cannot be added twice to one party.

Swagger UI is at `/swagger-ui.html` during development.

## Definition of done

A group can create and join a party, add manual or Maps candidates, compare available details and all mapped locations, vote once each, and get a winner with host-controlled early finalization. Browser refreshes, polling and Maps failures must not break the core party flow. Schema migrations and backend/browser tests must pass; a real restricted Google key must separately pass the documented live acceptance checklist before public release.

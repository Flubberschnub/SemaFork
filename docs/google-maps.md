# Google Maps candidates

## Scope and implementation plan

Keep the existing Spring Boot application, PostgreSQL database and plain JavaScript frontend. No new service, frontend framework, account system or restaurant database.

1. Add an optional Google Place ID to a suggestion. Preserve existing name-only suggestions. Identify duplicate Maps candidates by place ID within a party, not by restaurant name, so different branches remain distinct.
2. Publish only a restricted **browser** Maps key and map ID through a small runtime configuration endpoint. Load the Maps JavaScript API and Places (New) only on the party screen. A missing key or provider failure must not block the existing party/voting flow.
3. Add restaurant autocomplete and selection by clicking business labels on the map. Preview the selected business before adding it through the existing participant-authenticated suggestion endpoint.
4. Show numbered candidate markers throughout suggestions, voting and results. Keep the map DOM stable while polling. Candidate cards show Google photos, rating, rating count, price level and address; expanded details load review excerpts and more photos on demand. Missing information is explicitly unavailable, never synthesized.
5. Preserve Google/map, photo-author, review-author and third-party attributions. Persist place IDs only for Maps candidates; resolve provider metadata in the browser for display, not in PostgreSQL or browser persistent storage. Keep request promises in page memory to avoid billing the same fields on each poll.
6. Add backend integration and browser regression coverage. Document credential restrictions, enabled APIs, billing, and a live-key acceptance checklist.

## Definition of done

- A participant can select a business with autocomplete or a map POI, inspect it, then add it.
- Other participants see that same Place ID as a distinct candidate and can vote for it.
- All resolvable Maps candidates have numbered markers linked to their cards in OPEN, VOTING and FINALIZED phases, including after a participant has voted.
- Cards expose photos/credits, star rating, rating count, price level and address; reviews are available on demand with author credit. Unsupported fields have clear placeholders.
- Refresh, ordinary polling, remote suggestions and phase transitions do not reset map position, search input or a pending selection. Polling does not repeat Places requests for unchanged candidates.
- Duplicate Place IDs produce 409, different branches are allowed, invalid payloads produce 400, participant auth and phase rules still apply, and existing name-only candidates still work.
- No Google metadata, image bytes or review text is persisted. API keys are not committed. External text is safely rendered and links are limited to HTTPS.
- CI passes backend tests and mocked browser flows. Live Google acceptance remains a separate deployment check requiring a billing-enabled, restricted key; mocked tests are not proof of live provider authorization.

## Google setup

Enable billing, **Maps JavaScript API** and **Places API (New)** in one Google Cloud project. Create a dedicated browser API key with both website/referrer restrictions (only the development and deployed origins you actually use) and API restrictions to those two APIs. This key is intentionally visible in the browser; never put a server/service-account key in this setting.

Set `GOOGLE_MAPS_BROWSER_KEY` in the application's environment. Optionally set `GOOGLE_MAPS_MAP_ID` to your JavaScript map ID. The default `DEMO_MAP_ID` is for development; use your own map ID for deployment. Restart the app after changing configuration. No rebuild is required. Docker Compose reads these values from the host environment or its ignored `.env` file; Maven reads exported environment variables, not `.env` automatically.

Review Google Maps Platform quotas and billing alerts before public use. Maps, autocomplete, place details, reviews and photo usage can incur charges. Field masks are explicit; reviews and extra photos load only when opened, and ordinary party polling never refreshes Places data. Refreshing the page starts a new display session and fetches details again. Budget alerts are not a hard spending cap; configure API quotas as appropriate.

## Data contract

POST `/api/parties/{partyId}/suggestions`, with the existing `X-Member-Token`:

- Manual candidate: `{"name":"A restaurant suggested by a member"}`
- Maps candidate: `{"googlePlaceId":"ChIJ..."}`

Exactly one source is required. For Maps candidates `name` is null in the API response, and `googlePlaceId` identifies the source of current display data. Names, coordinates, ratings, reviews, photos and Google URLs are not saved. Do not start storing those in the session object or database later without reviewing Google's content-storage terms. The API validates the ID's shape, not the existence or business type of the remote place; failed/removed places display an explicit unavailable state and a Google Maps link. Name-only suggestions are never silently geocoded to an arbitrary branch.

## Live acceptance (requires configured Google account)

Use two separate browser profiles or devices. Create and join a party, search for a real restaurant, click another restaurant's map label, check photo/review author links, add both, and verify both numbered markers appear on the second device. Add two branches of one chain and reject the exact same branch twice. Start voting, inspect cards and map after submitting one vote, finalize, and check the winner. Refresh the page and confirm Place IDs resolve again. Check narrow-screen layout, keyboard access, denied geolocation, an invalid/restricted key, missing photos/price/reviews, and a network interruption. Confirm Google console usage and restrictions before public launch.

## References

- https://developers.google.com/maps/documentation/javascript/place-autocomplete-new
- https://developers.google.com/maps/documentation/javascript/reference/place
- https://developers.google.com/maps/documentation/javascript/place-photos
- https://developers.google.com/maps/documentation/javascript/place-reviews
- https://developers.google.com/maps/documentation/javascript/policies
- https://developers.google.com/maps/api-security-best-practices

The included public privacy/terms pages describe the integration; the deployment owner must review them for their actual hosting, contact and retention practices before public launch.

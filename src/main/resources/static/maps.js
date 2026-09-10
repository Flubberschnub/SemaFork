import { summaryMarkup, reviewsMarkup, photoMarkup, wirePhotoFallbacks } from './place-view.js';

const SUMMARY_FIELDS = ['displayName', 'formattedAddress', 'location', 'rating', 'userRatingCount',
    'priceLevel', 'photos', 'googleMapsURI', 'businessStatus'];
let sdkPromise;

function loadGoogleMaps(key) {
    if (window.google?.maps?.importLibrary) return Promise.resolve();
    if (sdkPromise) return sdkPromise;
    sdkPromise = new Promise((resolve, reject) => {
        let settled = false;
        const finish = error => {
            if (settled) return;
            settled = true;
            clearTimeout(timer);
            error ? reject(error) : resolve();
        };
        const timer = setTimeout(() => finish(new Error('Google Maps timed out.')), 20000);
        window.semaforkMapsReady = () => finish();
        window.gm_authFailure = () => {
            const error = new Error('Google Maps could not authorize this website. Check the key, API restrictions and billing.');
            finish(error);
            window.dispatchEvent(new CustomEvent('semafork-maps-error', { detail: error.message }));
        };
        const script = document.createElement('script');
        script.async = true;
        script.src = `https://maps.googleapis.com/maps/api/js?${new URLSearchParams({
            key, v: 'quarterly', loading: 'async', callback: 'semaforkMapsReady', libraries: 'maps,marker,places'
        })}`;
        script.onerror = () => finish(new Error('Google Maps could not be reached.'));
        document.head.append(script);
    });
    return sdkPromise;
}

/** One map per party page. Polling updates candidates without rebuilding the map or search widget. */
export class RestaurantMap {
    constructor(root, { onAdd, onPlaceLoaded = () => {} }) {
        this.root = root;
        this.onAdd = onAdd;
        this.onPlaceLoaded = onPlaceLoaded;
        this.entries = new Map(); // Display-session memory only. Never localStorage or the server.
        this.markers = new Map();
        this.candidates = [];
        this.allowAdd = false;
        this.ready = false;
        this.selectionVersion = 0;
        this.candidateVersion = 0;
        this.adding = false;
        root.innerHTML = `<h2>Explore the candidates</h2>
            <p id="map-help" class="help">Search for a restaurant or click its business label on the map. Use a city in your search to explore another area.</p>
            <div class="map-toolbar"><div id="place-search"></div>
                <button class="button secondary" id="map-locate" type="button" disabled>Use my location</button>
                <button class="button secondary" id="map-fit" type="button" disabled>Show all candidates</button></div>
            <p id="map-message" class="notice" role="status">Loading Google Maps…</p>
            <div id="candidate-map" class="candidate-map" aria-label="Restaurant candidate locations" hidden></div>
            <p id="map-coverage" class="help"></p>
            <section id="place-selection" class="place-selection" aria-label="Selected restaurant" hidden></section>`;
        this.canvas = root.querySelector('#candidate-map');
        this.selection = root.querySelector('#place-selection');
        root.querySelector('#map-fit').addEventListener('click', () => this.fitCandidates());
        root.querySelector('#map-locate').addEventListener('click', () => this.locate());
        this.authFailure = event => this.fail(event.detail);
        window.addEventListener('semafork-maps-error', this.authFailure);
        this.initialized = this.init();
    }

    async init() {
        try {
            const response = await fetch('/api/config/maps', { signal: AbortSignal.timeout(15000) });
            if (!response.ok) throw new Error('Map configuration is unavailable.');
            const config = await response.json();
            if (!config.enabled || !config.browserKey) {
                this.fail('Google Maps is not configured yet. You can still add restaurant names and vote.');
                return;
            }
            await loadGoogleMaps(config.browserKey);
            const [maps, markers, places] = await Promise.all([
                google.maps.importLibrary('maps'), google.maps.importLibrary('marker'), google.maps.importLibrary('places')
            ]);
            this.Place = places.Place;
            this.AdvancedMarker = markers.AdvancedMarkerElement;
            this.Bounds = maps.LatLngBounds || google.maps.LatLngBounds;
            this.canvas.hidden = false;
            this.map = new maps.Map(this.canvas, {
                center: { lat: 39, lng: -98 }, zoom: 4, mapId: config.mapId || 'DEMO_MAP_ID',
                mapTypeControl: false, streetViewControl: false, gestureHandling: 'cooperative'
            });
            this.autocomplete = new places.PlaceAutocompleteElement({
                includedPrimaryTypes: ['restaurant', 'cafe', 'bakery', 'bar', 'meal_takeaway']
            });
            this.autocomplete.setAttribute('aria-label', 'Search restaurants');
            this.root.querySelector('#place-search').append(this.autocomplete);
            this.autocomplete.addEventListener('gmp-select', event => {
                if (this.allowAdd && event.placePrediction) this.selectPlace(event.placePrediction.toPlace());
            });
            this.autocomplete.addEventListener('gmp-error', () => this.message('Restaurant search is unavailable. You can still use manual suggestions.'));
            this.map.addListener('bounds_changed', () => {
                const bounds = this.map.getBounds();
                if (bounds) this.autocomplete.locationBias = bounds;
            });
            this.map.addListener('dragstart', () => { this.hasFitted = true; });
            this.map.addListener('click', event => {
                if (this.allowAdd && event.placeId) {
                    event.stop(); // Replace Google's default POI window with our candidate preview.
                    this.selectPlace(new this.Place({ id: event.placeId }));
                }
            });
            this.ready = true;
            this.message('');
            this.root.querySelector('#map-locate').disabled = false;
            this.updateActions();
            await this.syncMarkers();
        } catch (error) { this.fail(error.message); }
    }

    fail(message) {
        this.ready = false;
        this.message(`${message} Manual suggestions and voting remain available.`);
        this.root.querySelector('#map-locate').disabled = true;
        this.root.querySelector('#map-fit').disabled = true;
        this.updateActions();
    }

    message(text) {
        const element = this.root.querySelector('#map-message');
        element.textContent = text;
        element.hidden = !text;
    }

    getPlace(id) { return this.entries.get(id)?.place; }

    async ensurePlace(id, suppliedPlace) {
        if (this.entries.has(id)) return this.entries.get(id).promise;
        if (!this.ready) throw new Error('Google Maps details are unavailable.');
        const entry = { status: 'pending' };
        this.entries.set(id, entry);
        entry.promise = (async () => {
            const place = suppliedPlace || new this.Place({ id });
            await place.fetchFields({ fields: SUMMARY_FIELDS });
            entry.place = place;
            entry.status = 'ready';
            this.onPlaceLoaded(id, place);
            return place;
        })().catch(error => {
            entry.status = 'error';
            this.onPlaceLoaded(id, null);
            throw error;
        });
        return entry.promise; // Failed promises are retained until explicit retry, not retried by polling.
    }

    setState(candidates, allowAdd) {
        this.allowAdd = allowAdd;
        const changed = JSON.stringify(candidates.map(c => [c.id, c.googlePlaceId])) !==
            JSON.stringify(this.candidates.map(c => [c.id, c.googlePlaceId]));
        this.candidates = candidates;
        this.updateActions();
        if (changed && this.ready) this.syncMarkers();
    }

    updateActions() {
        this.root.querySelector('#place-search').hidden = !this.allowAdd || !this.ready;
        this.root.querySelector('#map-help').textContent = this.allowAdd
            ? 'Search for a restaurant or click its business label on the map. Add a city to search another area.'
            : 'Numbered markers match the candidate cards. Click a marker for photos and reviews; voting is on the cards below.';
        if (this.map) this.map.setOptions({ clickableIcons: this.allowAdd && this.ready });
        const button = this.selection.querySelector('[data-add-place]');
        if (button) {
            const exists = this.candidates.some(c => c.googlePlaceId === this.selectedId);
            button.disabled = !this.ready || !this.allowAdd || exists || this.adding;
            button.textContent = this.adding ? 'Adding…' : exists ? 'Already a candidate' : !this.allowAdd ? 'Suggestions are closed' : 'Add this candidate';
        }
    }

    async syncMarkers() {
        const version = ++this.candidateVersion;
        const mapped = this.candidates.filter(c => c.googlePlaceId);
        for (const [id, marker] of this.markers) {
            if (!mapped.some(c => c.id === id)) { marker.map = null; this.markers.delete(id); }
        }
        await Promise.allSettled(mapped.map(async candidate => {
            const place = await this.ensurePlace(candidate.googlePlaceId);
            if (version !== this.candidateVersion || !place.location || !this.ready) return;
            const number = this.candidates.findIndex(c => c.id === candidate.id) + 1;
            if (!this.markers.has(candidate.id)) {
                const badge = document.createElement('span');
                badge.className = 'map-pin';
                badge.textContent = String(number);
                const marker = new this.AdvancedMarker({
                    map: this.map, position: place.location, title: `${number}. ${place.displayName || 'Candidate'}`
                });
                marker.append(badge);
                marker.addListener('click', () => this.showCandidate(candidate.googlePlaceId));
                this.markers.set(candidate.id, marker);
            }
        }));
        if (version !== this.candidateVersion) return;
        const unlocated = this.candidates.length - this.markers.size;
        this.root.querySelector('#map-coverage').textContent = this.candidates.length
            ? `${this.markers.size} of ${this.candidates.length} candidates marked.${unlocated ? ' Name-only or unavailable places have no map marker.' : ''}`
            : 'No candidates yet. Select a restaurant to get started.';
        this.root.querySelector('#map-fit').disabled = !this.ready || this.markers.size === 0;
        // Fit only the first resolved candidate set; do not pull users away from their search during polling.
        if (!this.hasFitted && this.markers.size) {
            this.fitCandidates();
            this.hasFitted = true;
        }
    }

    fitCandidates() {
        if (!this.ready || !this.markers.size) return;
        const positions = [...this.markers.values()].map(marker => marker.position);
        if (positions.length === 1) {
            this.map.setCenter(positions[0]); this.map.setZoom(15);
        } else {
            const bounds = new this.Bounds();
            positions.forEach(position => bounds.extend(position));
            this.map.fitBounds(bounds, 48);
        }
    }

    async showCandidate(id) {
        await this.initialized;
        if (!this.ready) { this.message('Maps details are unavailable. You can still vote on the candidate cards.'); return; }
        // Explicit clicks can retry a failed lookup; polling cannot.
        const entry = this.entries.get(id);
        if (entry?.status === 'error') this.entries.delete(id);
        await this.selectPlace(new this.Place({ id }));
        await this.syncMarkers();
    }

    async selectPlace(place) {
        const version = ++this.selectionVersion;
        this.selectedId = null;
        this.selection.hidden = false;
        this.selection.textContent = 'Loading restaurant details…';
        try {
            if (this.entries.get(place.id)?.status === 'error') this.entries.delete(place.id);
            const loaded = await this.ensurePlace(place.id, place);
            if (version !== this.selectionVersion || !this.ready) return;
            this.selectedId = place.id;
            this.selection.innerHTML = `${summaryMarkup(loaded)}
                <details class="more-place-details"><summary>Reviews and more photos</summary><div class="review-content"></div></details>
                <p class="error" data-place-error role="alert" hidden></p>
                <button class="button" type="button" data-add-place>Add this candidate</button>`;
            wirePhotoFallbacks(this.selection);
            const details = this.selection.querySelector('details');
            details.addEventListener('toggle', () => {
                if (details.open && !details.dataset.loaded) this.loadReviews(place.id, details);
            });
            this.selection.querySelector('[data-add-place]').addEventListener('click', () => this.addSelected());
            if (loaded.location) {
                this.hasFitted = true;
                this.map.panTo(loaded.location);
                this.map.setZoom(Math.max(this.map.getZoom() || 0, 15));
                if (this.previewMarker) this.previewMarker.map = null;
                if (!this.candidates.some(c => c.googlePlaceId === place.id)) {
                    this.previewMarker = new this.AdvancedMarker({ map: this.map, position: loaded.location, title: 'Selected restaurant' });
                }
            } else {
                this.selection.querySelector('[data-place-error]').textContent = 'Location unavailable for this place.';
                this.selection.querySelector('[data-place-error]').hidden = false;
            }
            this.updateActions();
        } catch {
            if (version !== this.selectionVersion) return;
            this.selection.textContent = 'Could not load this restaurant. Select it again to retry, or use a manual suggestion.';
        }
    }

    async loadReviews(id, details) {
        details.dataset.loaded = 'loading';
        const target = details.querySelector('.review-content');
        target.textContent = 'Loading Google reviews…';
        const entry = this.entries.get(id);
        try {
            if (!entry.reviewsPromise) entry.reviewsPromise = entry.place.fetchFields({ fields: ['reviews'] });
            await entry.reviewsPromise;
            if (!details.isConnected) return;
            target.innerHTML = `<div class="photo-gallery">${(entry.place.photos || []).slice(1, 4)
                .map(photo => photoMarkup(photo, entry.place.displayName || 'Restaurant')).join('')}</div>${reviewsMarkup(entry.place)}`;
            wirePhotoFallbacks(target);
            details.dataset.loaded = 'yes';
        } catch {
            entry.reviewsPromise = null;
            delete details.dataset.loaded;
            target.textContent = 'Reviews could not load. Close and reopen this section to retry.';
        }
    }

    async addSelected() {
        if (!this.ready || !this.allowAdd || !this.selectedId || this.adding) return;
        const id = this.selectedId;
        const errorBox = this.selection.querySelector('[data-place-error]');
        if (errorBox) errorBox.hidden = true;
        this.adding = true;
        this.updateActions();
        try {
            await this.onAdd(id);
            if (this.selectedId === id && this.previewMarker) { this.previewMarker.map = null; this.previewMarker = null; }
        } catch (error) {
            if (errorBox?.isConnected) { errorBox.textContent = error.message; errorBox.hidden = false; }
        } finally { this.adding = false; this.updateActions(); }
    }

    locate() {
        if (!navigator.geolocation) { this.message('Location is unsupported. Search for a restaurant and city instead.'); return; }
        const button = this.root.querySelector('#map-locate');
        button.disabled = true;
        navigator.geolocation.getCurrentPosition(position => {
            button.disabled = !this.ready;
            if (!this.ready) return;
            this.hasFitted = true;
            this.map.setCenter({ lat: position.coords.latitude, lng: position.coords.longitude });
            this.map.setZoom(14);
            this.message('');
        }, () => {
            button.disabled = !this.ready;
            this.message('Location was denied or unavailable. Search with a city name or move the map instead.');
        }, { timeout: 10000, maximumAge: 60000 });
    }
}

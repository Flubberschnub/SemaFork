// Browser-test double only. Never included in src/main/resources or the application JAR.
window.__maps = { count: 0, markers: [], calls: {}, failures: {}, delays: {} };
const fixture = {
    place_a: { displayName: 'Cava Downtown', formattedAddress: '10 Main Street', location: { lat: 32.9, lng: -96.9 }, rating: 4.7,
        userRatingCount: 321, priceLevel: 'MODERATE', photos: true, reviews: true },
    place_b: { displayName: 'Cava Uptown', formattedAddress: '20 Main Street', location: { lat: 32.95, lng: -96.94 } },
    place_slow: { displayName: 'Slow Sushi', location: { lat: 33, lng: -96 } },
    place_retry: { displayName: 'Retry Cafe', location: { lat: 33, lng: -96 } },
    place_fast: { displayName: 'Fast Noodles', location: { lat: 32, lng: -97 } }
};
class FakeBounds {
    constructor() { this.points = []; }
    extend(position) { this.points.push(position); return this; }
}
class FakeMap {
    constructor(element, options) {
        this.element = element; this.center = options.center; this.zoom = options.zoom; this.events = {};
        window.__maps.count++; window.__maps.map = this;
        element.style.background = '#e2e8df';
        const label = document.createElement('p'); label.textContent = 'Google Maps test double'; element.append(label);
    }
    addListener(type, callback) { this.events[type] = callback; return { remove() {} }; }
    setCenter(position) { this.center = position; }
    panTo(position) { this.center = position; }
    setZoom(zoom) { this.zoom = zoom; }
    getZoom() { return this.zoom; }
    getBounds() { return new FakeBounds(); }
    fitBounds(bounds) { this.fits = (this.fits || 0) + 1; this.bounds = bounds; }
    setOptions(options) { this.options = options; }
}
class FakeMarker extends HTMLElement {
    constructor(options) {
        super(); this.position = options.position; this.title = options.title; this.map = options.map;
        window.__maps.markers.push(this); this.tabIndex = 0;
    }
    set map(map) { this._map = map; if (map) map.element.append(this); else this.remove(); }
    get map() { return this._map; }
    addListener(type, callback) { this.addEventListener(type, callback); return { remove() {} }; }
}
customElements.define('fake-map-marker', FakeMarker);
class FakePlace {
    constructor({ id }) { this.id = id; }
    async fetchFields({ fields }) {
        const kind = fields.includes('reviews') ? 'reviews' : 'summary';
        const counts = window.__maps.calls[this.id] ||= { summary: 0, reviews: 0 };
        counts[kind]++;
        await new Promise(resolve => setTimeout(resolve, window.__maps.delays[this.id] || 1));
        if (window.__maps.failures[this.id]) throw new Error('Places unavailable');
        const source = fixture[this.id];
        if (!source) throw new Error('Unknown place');
        if (kind === 'summary') {
            Object.assign(this, source);
            this.googleMapsURI = `https://www.google.com/maps/search/?api=1&query=restaurant&query_place_id=${this.id}`;
            this.attributions = [{ provider: 'Fixture provider', providerURI: 'https://example.com/credit' }];
            this.photos = source.photos ? [0, 1, 2].map(index => ({
                getURI: () => `https://maps.googleapis.com/mock-photo-${index}.png`,
                authorAttributions: [{ displayName: 'Photographer', uri: 'https://www.google.com/maps/contrib/photo-author' }]
            })) : [];
            delete this.reviews;
        } else {
            this.reviews = source.reviews ? [{ rating: 5, text: '<img src=x onerror="window.__pwned=1"> Great food!',
                relativePublishTimeDescription: 'A week ago', authorAttribution: { displayName: 'Reviewer', uri: 'https://www.google.com/maps/contrib/reviewer' } }] : [];
        }
        return { place: this };
    }
}
class FakeAutocomplete extends HTMLElement {
    constructor() {
        super(); const root = this.attachShadow({ mode: 'open' });
        this.input = document.createElement('input'); this.input.setAttribute('aria-label', 'Restaurant search');
        root.append(this.input); window.__maps.autocomplete = this;
    }
    choose(id) {
        const event = new Event('gmp-select'); event.placePrediction = { toPlace: () => new FakePlace({ id }) };
        this.dispatchEvent(event);
    }
}
customElements.define('gmp-place-autocomplete', FakeAutocomplete);
window.google = { maps: { LatLngBounds: FakeBounds, importLibrary: async name => ({
    maps: { Map: FakeMap, LatLngBounds: FakeBounds }, marker: { AdvancedMarkerElement: FakeMarker },
    places: { Place: FakePlace, PlaceAutocompleteElement: FakeAutocomplete }
})[name] } };

// All external text is escaped; provider URLs are never injected as executable markup.
export function escapeHtml(value) {
    return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#039;');
}

export function httpsUrl(value) {
    try {
        const url = new URL(value);
        return url.protocol === 'https:' ? url.href : '';
    } catch { return ''; }
}

export function mapsUrl(id, directions = false) {
    const params = new URLSearchParams(directions
        ? { api: '1', destination: 'restaurant', destination_place_id: id }
        : { api: '1', query: 'restaurant', query_place_id: id });
    return `https://www.google.com/maps/${directions ? 'dir' : 'search'}/?${params}`;
}

export function priceLabel(level) {
    return { FREE: 'Free', INEXPENSIVE: '$', MODERATE: '$$', EXPENSIVE: '$$$', VERY_EXPENSIVE: '$$$$' }[level]
        || 'Price unavailable';
}

function link(text, href) {
    const safe = httpsUrl(href);
    return safe ? `<a href="${escapeHtml(safe)}" target="_blank" rel="noopener noreferrer">${escapeHtml(text)}</a>`
        : escapeHtml(text);
}

export function photoMarkup(photo, name) {
    if (!photo) return '<p class="photo-placeholder">No photo available</p>';
    let url = '';
    try { url = httpsUrl(photo.getURI({ maxWidth: 640, maxHeight: 420 })); } catch { /* Missing photo. */ }
    if (!url) return '<p class="photo-placeholder">Photo unavailable</p>';
    const credits = (photo.authorAttributions || []).map(a => link(a.displayName || 'Contributor', a.uri)).join(', ');
    return `<figure class="place-photo"><img src="${escapeHtml(url)}" alt="${escapeHtml(name)}" loading="lazy" decoding="async">
        <figcaption>Photo: ${credits || 'Google Maps'}${photo.googleMapsURI ? ` · ${link('View photo', photo.googleMapsURI)}` : ''}</figcaption></figure>`;
}

export function attributionsMarkup(place) {
    const credits = (place.attributions || []).map(a =>
        typeof a === 'string' ? escapeHtml(a) : link(a.provider || 'Data provider', a.providerURI));
    return `<p class="provider-attribution"><span class="google-attribution" translate="no">Google Maps</span>${credits.length ? ` · ${credits.join(' · ')}` : ''}</p>`;
}

export function summaryMarkup(place) {
    const rating = typeof place.rating === 'number' ? `★ ${place.rating.toFixed(1)}` : 'Rating unavailable';
    const count = typeof place.userRatingCount === 'number'
        ? `${place.userRatingCount.toLocaleString()} ratings` : 'Rating count unavailable';
    const closed = { CLOSED_PERMANENTLY: 'Permanently closed', CLOSED_TEMPORARILY: 'Temporarily closed' }[place.businessStatus];
    return `<div class="place-summary">
        ${photoMarkup(place.photos?.[0], place.displayName || 'Restaurant')}
        <div><h3>${escapeHtml(place.displayName || 'Unnamed place')}</h3>
        <p class="place-facts"><strong>${escapeHtml(rating)}</strong> <span>(${escapeHtml(count)})</span>
        <strong class="price-level">${escapeHtml(priceLabel(place.priceLevel))}</strong></p>
        <p>${escapeHtml(place.formattedAddress || 'Address unavailable')}</p>
        ${closed ? `<p class="notice">${closed}</p>` : ''}
        <p class="place-links">${link('Open in Google Maps', place.googleMapsURI || mapsUrl(place.id))}
            · ${link('Directions', mapsUrl(place.id, true))}</p>
        ${attributionsMarkup(place)}</div></div>`;
}

export function reviewsMarkup(place) {
    const reviews = place.reviews || [];
    return `<p class="help">Google supplies up to five relevant reviews, not the full review history.</p>
        ${reviews.length ? reviews.map(review => `<article class="place-review">
            <p><strong>${link(review.authorAttribution?.displayName || 'Google Maps contributor', review.authorAttribution?.uri)}</strong>
            · ${typeof review.rating === 'number' ? `★ ${review.rating}` : 'No rating'}
            <span class="muted">${escapeHtml(review.relativePublishTimeDescription || '')}</span></p>
            <p class="review-text">${escapeHtml(review.text || 'This reviewer left a rating without text.')}</p>
            ${review.googleMapsURI ? `<p>${link('View review', review.googleMapsURI)}</p>` : ''}
        </article>`).join('') : '<p>No review excerpts available for this place.</p>'}
        ${attributionsMarkup(place)}`;
}

export function wirePhotoFallbacks(root) {
    root.querySelectorAll('.place-photo img').forEach(img => {
        img.addEventListener('error', () => {
            const message = document.createElement('p');
            message.className = 'photo-placeholder';
            message.textContent = 'Photo unavailable';
            img.replaceWith(message); // Keep the photographer credit in the figure.
        }, { once: true });
    });
}

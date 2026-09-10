import { RestaurantMap } from './maps.js';
import { escapeHtml, mapsUrl, summaryMarkup, wirePhotoFallbacks } from './place-view.js';

const app = document.getElementById('app');
const code = (new URLSearchParams(location.search).get('party') || '').trim().toUpperCase();
const $ = id => document.getElementById(id);
const cards = new Map();
let state;
let restaurantMap;
let refreshPromise;
let pollTimer;
let mutationInFlight = false;

if (code) refreshParty(); else renderHome();

function renderHome() {
    app.innerHTML = `<section class="hero"><h1>Stop debating. Start eating.</h1>
        <p>Explore restaurants on the map, compare your options, and choose together. No account required.</p></section>
        <section class="grid"><article class="card"><h2>Create a party</h2>
            <form id="create-form"><label>Party name<input name="name" maxlength="80" placeholder="Friday dinner" required></label>
            <label>Your name<input name="hostName" maxlength="80" placeholder="Alex" required></label>
            <p class="error" role="alert" id="create-error" hidden></p><button class="button">Create party</button></form></article>
        <article class="card"><h2>Join a party</h2>
            <form id="join-form"><label>Party code<input name="joinCode" maxlength="8" autocomplete="off" autocapitalize="characters" placeholder="ABC123" required></label>
            <label>Your name<input name="memberName" maxlength="80" placeholder="Sam" required></label>
            <p class="error" role="alert" id="join-error" hidden></p><button class="button secondary">Join party</button></form></article></section>`;
    $('create-form').addEventListener('submit', event => {
        event.preventDefault();
        const form = event.currentTarget;
        submit(form.querySelector('button'), 'create-error', async () => {
            const data = await api('/api/parties', { method: 'POST', body: JSON.stringify({
                name: form.elements.name.value, hostName: form.elements.hostName.value
            }) });
            saveSession(data.joinCode, { partyId: data.partyId, memberId: data.memberId,
                memberToken: data.memberToken, hostToken: data.hostToken, voted: false });
            navigate(data.joinCode);
        });
    });
    $('join-form').addEventListener('submit', event => {
        event.preventDefault();
        const form = event.currentTarget;
        submit(form.querySelector('button'), 'join-error', () => join(form.elements.joinCode.value, form.elements.memberName.value));
    });
}

async function join(joinCode, memberName) {
    const data = await api('/api/parties/join', { method: 'POST', body: JSON.stringify({ joinCode, memberName }) });
    saveSession(data.joinCode, { partyId: data.partyId, memberId: data.memberId,
        memberToken: data.memberToken, hostToken: null, voted: false });
    navigate(data.joinCode);
}

function navigate(joinCode) { location.href = `/?party=${encodeURIComponent(joinCode.toUpperCase())}`; }

function refreshParty() {
    if (refreshPromise) return refreshPromise;
    clearTimeout(pollTimer);
    refreshPromise = (async () => {
        try {
            const party = await api(`/api/parties/code/${encodeURIComponent(code)}`);
            const [suggestions, results] = await Promise.all([
                api(`/api/parties/${party.id}/suggestions`), api(`/api/parties/${party.id}/voting`)
            ]);
            party.status = results.status; // The later read wins if the phase changed between requests.
            state = { party, suggestions, results };
            if (!$('party-shell')) mountParty();
            $('network-warning').hidden = true;
            renderPartyState();
        } catch (error) {
            if (state) showError('network-warning', `Connection interrupted: ${error.message} Retrying automatically…`);
            else {
                app.innerHTML = `<section class="card"><h1>Could not open this party</h1>
                    <p>${escapeHtml(error.message)}</p><button class="button" id="retry-party">Retry</button>
                    <a class="button secondary" href="/">Return home</a></section>`;
                $('retry-party').addEventListener('click', refreshParty);
            }
        } finally {
            refreshPromise = null;
            if (state) pollTimer = setTimeout(refreshParty, 3000);
        }
    })();
    return refreshPromise;
}

async function refreshAfterMutation() {
    if (refreshPromise) await refreshPromise;
    await refreshParty();
}

function mountParty() {
    app.innerHTML = `<section id="party-shell" class="stack">
        <article class="card party-heading"><div><span class="status" id="phase-label"></span>
            <h1 id="party-name"></h1><div class="code" id="party-code"></div></div>
            <div class="button-row"><button class="button secondary" id="copy-link">Copy invite link</button>
            <button class="button secondary" id="forget-session" hidden>Forget this device</button></div></article>
        <p class="notice" role="status" id="network-warning" hidden></p>
        <article class="card" id="join-current" hidden><h2>Join this party</h2>
            <form id="join-current-form"><label>Your name<input name="memberName" maxlength="80" required></label>
            <p class="error" role="alert" id="join-current-error" hidden></p><button class="button">Join party</button></form></article>
        <section class="maps-layout"><article class="card" id="maps-panel"></article>
            <aside class="stack"><article class="card"><h2 id="people-title">People</h2><ul class="list" id="people"></ul></article>
            <article class="card" id="manual-card"><details open><summary>Add a name without a map</summary>
                <p class="help">For places not on Google Maps. These candidates have no location, photos or Google reviews.</p>
                <form id="suggestion-form"><label>Restaurant name<input name="name" maxlength="120" required></label>
                <p class="error" role="alert" id="suggestion-error" hidden></p><button class="button secondary">Add suggestion</button></form></details></article>
            <article class="card" id="host-controls" hidden><h2>Host controls</h2>
                <button class="button" id="start-voting">Start voting</button>
                <button class="button secondary" id="finalize-voting">Finish with current votes</button>
                <p class="help" id="host-help"></p><p class="error" role="alert" id="host-error" hidden></p></article></aside></section>
        <article class="card"><section class="winner" id="winner" hidden><span>The party chose</span><strong id="winner-name"></strong></section>
            <h2>Restaurant candidates</h2><p class="notice" id="vote-notice" role="status"></p>
            <p class="error" role="alert" id="vote-error" hidden></p>
            <div class="candidate-grid" id="candidates"></div><p class="muted" id="empty-candidates">No suggestions yet. Pick a restaurant on the map or add a name.</p>
            <a class="button secondary" id="new-party" href="/" hidden>Start another party</a></article></section>`;
    restaurantMap = new RestaurantMap($('maps-panel'), {
        onAdd: async googlePlaceId => {
            const session = loadSession();
            if (!session?.memberToken || state.party.status !== 'OPEN') throw new Error('Join an open party before adding a candidate.');
            await api(`/api/parties/${state.party.id}/suggestions`, {
                method: 'POST', headers: { 'X-Member-Token': session.memberToken }, body: JSON.stringify({ googlePlaceId })
            });
            await refreshAfterMutation();
        },
        onPlaceLoaded: id => { paintPlace(id); updateWinner(); }
    });
    restaurantMap.initialized.then(() => state.suggestions.filter(s => s.googlePlaceId).forEach(s => paintPlace(s.googlePlaceId)));
    $('copy-link').addEventListener('click', async event => {
        const link = `${location.origin}/?party=${encodeURIComponent(code)}`;
        try {
            await navigator.clipboard.writeText(link);
            event.target.textContent = 'Copied!';
            setTimeout(() => event.target.textContent = 'Copy invite link', 1400);
        } catch { window.prompt('Copy this invite link:', link); }
    });
    $('forget-session').addEventListener('click', () => {
        if (!window.confirm('Forget your identity on this device? This does not remove you from the party. Host controls cannot be recovered without the saved session.')) return;
        localStorage.removeItem(sessionKey(code));
        renderPartyState();
    });
    $('join-current-form').addEventListener('submit', event => {
        event.preventDefault();
        const form = event.currentTarget;
        submit(form.querySelector('button'), 'join-current-error', () => join(code, form.elements.memberName.value));
    });
    $('suggestion-form').addEventListener('submit', event => {
        event.preventDefault();
        const form = event.currentTarget;
        submit(form.querySelector('button'), 'suggestion-error', async () => {
            await api(`/api/parties/${state.party.id}/suggestions`, {
                method: 'POST', headers: { 'X-Member-Token': loadSession().memberToken },
                body: JSON.stringify({ name: form.elements.name.value })
            });
            form.reset(); await refreshAfterMutation();
        });
    });
    $('start-voting').addEventListener('click', event => hostAction(event.currentTarget, 'start'));
    $('finalize-voting').addEventListener('click', event => hostAction(event.currentTarget, 'finalize'));
}

function renderPartyState() {
    const { party, suggestions, results } = state;
    const session = loadSession();
    const open = party.status === 'OPEN';
    $('party-name').textContent = party.name;
    $('party-code').textContent = `Code: ${party.joinCode}`;
    $('phase-label').textContent = { OPEN: 'Adding ideas', VOTING: 'Voting', FINALIZED: 'Decided' }[party.status];
    $('forget-session').hidden = !session;
    $('join-current').hidden = Boolean(session) || !open;
    $('manual-card').hidden = !session?.memberToken || !open;
    $('people-title').textContent = `People (${party.members.length})`;
    $('people').innerHTML = party.members.map(member => `<li class="list-item"><strong>${escapeHtml(member.memberName)}</strong>
        ${member.id === session?.memberId ? '<span class="muted">You</span>' : ''}</li>`).join('');
    $('host-controls').hidden = !session?.hostToken || party.status === 'FINALIZED';
    $('start-voting').hidden = !open;
    $('start-voting').disabled = mutationInFlight || suggestions.length < 2;
    $('finalize-voting').hidden = party.status !== 'VOTING';
    $('finalize-voting').disabled = mutationInFlight;
    $('host-help').textContent = open ? 'At least two candidates are required. Joining and suggestions close when voting starts.'
        : 'Finish early when someone is unavailable. At least one vote is required.';
    $('vote-notice').textContent = open ? 'Compare the restaurants, then the host starts voting.'
        : party.status === 'FINALIZED' ? 'The vote is final. Photos, reviews and directions are still available.'
        : session?.voted ? 'Your vote is in. You can still explore every candidate while others vote.'
        : session?.memberToken ? 'One vote per person. Your choice cannot be changed. Totals stay hidden until voting finishes.'
        : 'Voting is underway. Only participants who joined earlier can vote.';
    $('empty-candidates').hidden = suggestions.length > 0;
    $('new-party').hidden = party.status !== 'FINALIZED';
    const ids = new Set(suggestions.map(s => s.id));
    for (const [id, card] of cards) if (!ids.has(id)) { card.remove(); cards.delete(id); }
    suggestions.forEach((suggestion, index) => {
        let card = cards.get(suggestion.id);
        if (!card) {
            card = document.createElement('article');
            card.className = 'candidate-card'; card.id = `candidate-${suggestion.id}`; card.tabIndex = -1;
            card.innerHTML = `<p class="candidate-number"></p><div class="candidate-summary"></div>
                <p class="help candidate-author"></p><div class="button-row">
                <button class="button secondary candidate-details" type="button">Map & reviews</button>
                <button class="button candidate-vote" type="button">Vote for this place</button>
                <span class="vote-count" hidden></span></div>`;
            cards.set(suggestion.id, card); $('candidates').append(card);
            card.querySelector('.candidate-author').textContent = `Suggested by ${suggestion.memberName}`;
            card.querySelector('.candidate-details').hidden = !suggestion.googlePlaceId;
            card.querySelector('.candidate-details').addEventListener('click', () => {
                restaurantMap.showCandidate(suggestion.googlePlaceId);
                $('maps-panel').scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
            card.querySelector('.candidate-vote').addEventListener('click', event => vote(suggestion.id, event.currentTarget));
            if (suggestion.googlePlaceId) paintPlace(suggestion.googlePlaceId);
            else card.querySelector('.candidate-summary').innerHTML = `<h3>${escapeHtml(suggestion.name)}</h3><p class="muted">Name-only candidate · location and Google details unavailable</p>`;
        }
        card.querySelector('.candidate-number').textContent = `Candidate ${index + 1}`;
        const button = card.querySelector('.candidate-vote');
        button.hidden = party.status !== 'VOTING';
        button.disabled = mutationInFlight || !session?.memberToken || session.voted;
        const count = card.querySelector('.vote-count');
        count.hidden = party.status !== 'FINALIZED';
        count.textContent = `${Number(results.counts?.[suggestion.id] || 0)} votes`;
        card.classList.toggle('winning-candidate', results.winnerSuggestionId === suggestion.id);
    });
    restaurantMap.setState(suggestions, Boolean(session?.memberToken) && open);
    updateWinner();
}

function paintPlace(id) {
    const place = restaurantMap.getPlace(id);
    for (const suggestion of state.suggestions.filter(s => s.googlePlaceId === id)) {
        const body = cards.get(suggestion.id)?.querySelector('.candidate-summary');
        if (!body) continue;
        body.innerHTML = place ? summaryMarkup(place) : `<h3>Google Maps candidate</h3>
            <p class="muted">${restaurantMap.ready ? 'Restaurant details are loading or unavailable. Use Map & reviews to retry.' : 'Maps details are unavailable until Google Maps is configured and connected.'}</p>
            <a href="${escapeHtml(mapsUrl(id))}" target="_blank" rel="noopener noreferrer">View the selected place in Google Maps</a>`;
        wirePhotoFallbacks(body);
    }
}

function updateWinner() {
    const winner = state.suggestions.find(s => s.id === state.results.winnerSuggestionId);
    $('winner').hidden = state.party.status !== 'FINALIZED';
    $('winner-name').textContent = winner ? (restaurantMap.getPlace(winner.googlePlaceId)?.displayName || winner.name
        || `Candidate ${state.suggestions.indexOf(winner) + 1} (Google Maps details unavailable)`) : 'No winner';
}

async function vote(id, button) {
    await submit(button, 'vote-error', async () => {
        const session = loadSession();
        try {
            await api(`/api/parties/${state.party.id}/voting/votes`, { method: 'POST',
                headers: { 'X-Member-Token': session.memberToken }, body: JSON.stringify({ suggestionId: id }) });
        } catch (error) { if (error.status !== 409) throw error; }
        session.voted = true; saveSession(code, session); await refreshAfterMutation();
    });
}

function hostAction(button, action) {
    return submit(button, 'host-error', async () => {
        await api(`/api/parties/${state.party.id}/voting/${action}`, {
            method: 'POST', headers: { 'X-Host-Token': loadSession().hostToken }
        });
        await refreshAfterMutation();
    });
}

async function submit(button, errorId, action) {
    if (mutationInFlight) return;
    mutationInFlight = true;
    $(errorId).hidden = true;
    const label = button.textContent;
    button.textContent = 'Working…'; button.disabled = true;
    if (state) renderPartyState();
    try { await action(); } catch (error) { showError(errorId, error.message); }
    finally {
        mutationInFlight = false; button.textContent = label; button.disabled = false;
        if (state) renderPartyState();
    }
}

async function api(path, options = {}) {
    const response = await fetch(path, { ...options, signal: AbortSignal.timeout(15000),
        headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers } });
    if (!response.ok) {
        let body;
        try { body = await response.json(); } catch { /* Non-JSON gateway response. */ }
        const error = new Error(body?.message || `Request failed (${response.status}).`);
        error.status = response.status; throw error;
    }
    // Successful commands (including the 201 vote response) can have no body.
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

function sessionKey(joinCode) { return `semafork.session.${joinCode.toUpperCase()}`; }
function saveSession(joinCode, session) { localStorage.setItem(sessionKey(joinCode), JSON.stringify(session)); }
function loadSession() { try { return JSON.parse(localStorage.getItem(sessionKey(code)) || 'null'); } catch { return null; } }
function showError(id, message) { const box = $(id); if (box) { box.textContent = message; box.hidden = false; } }

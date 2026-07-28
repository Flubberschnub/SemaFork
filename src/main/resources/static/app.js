const app = document.getElementById("app");
const params = new URLSearchParams(window.location.search);
let currentCode = (params.get("party") || "").trim().toUpperCase();
let pollTimer = null;
let state = { party: null, suggestions: [], results: null };

init();

async function init() {
    if (currentCode) {
        await refreshParty(false);
    } else {
        renderHome();
    }
}

function renderHome() {
    stopPolling();
    app.innerHTML = `
        <section class="hero">
            <h1>Stop debating. Start eating.</h1>
            <p>Create a party, collect restaurant ideas, and let everyone vote. No account required.</p>
        </section>
        <section class="grid">
            <article class="card">
                <h2>Create a party</h2>
                <p class="muted">You will be the host and can start or finish voting.</p>
                <form id="create-party-form">
                    <label>
                        Party name
                        <input name="name" maxlength="80" placeholder="Friday dinner" required>
                    </label>
                    <label>
                        Your name
                        <input name="hostName" maxlength="80" placeholder="Alex" required>
                    </label>
                    <div class="error" id="create-error" hidden></div>
                    <button class="button" type="submit">Create party</button>
                </form>
            </article>
            <article class="card">
                <h2>Join a party</h2>
                <p class="muted">Enter the six-character code shared by the host.</p>
                <form id="join-party-form">
                    <label>
                        Party code
                        <input name="joinCode" maxlength="8" autocomplete="off" autocapitalize="characters" placeholder="ABC123" required>
                    </label>
                    <label>
                        Your name
                        <input name="memberName" maxlength="80" placeholder="Sam" required>
                    </label>
                    <div class="error" id="join-error" hidden></div>
                    <button class="button secondary" type="submit">Join party</button>
                </form>
            </article>
        </section>
    `;

    document.getElementById("create-party-form").addEventListener("submit", createParty);
    document.getElementById("join-party-form").addEventListener("submit", joinPartyFromHome);
}

async function createParty(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector("button");
    setBusy(button, true, "Creating…");
    hideError("create-error");

    try {
        const data = await api("/api/parties", {
            method: "POST",
            body: JSON.stringify({
                name: form.elements.name.value,
                hostName: form.elements.hostName.value
            })
        });

        saveSession(data.joinCode, {
            partyId: data.partyId,
            memberId: data.memberId,
            memberToken: data.memberToken,
            hostToken: data.hostToken,
            voted: false
        });
        navigateToParty(data.joinCode);
    } catch (error) {
        showError("create-error", error.message);
        setBusy(button, false, "Create party");
    }
}

async function joinPartyFromHome(event) {
    event.preventDefault();
    const form = event.currentTarget;
    await joinParty(form.elements.joinCode.value, form.elements.memberName.value, "join-error", form.querySelector("button"));
}

async function joinParty(joinCode, memberName, errorId, button) {
    setBusy(button, true, "Joining…");
    hideError(errorId);

    try {
        const data = await api("/api/parties/join", {
            method: "POST",
            body: JSON.stringify({ joinCode, memberName })
        });

        saveSession(data.joinCode, {
            partyId: data.partyId,
            memberId: data.memberId,
            memberToken: data.memberToken,
            hostToken: null,
            voted: false
        });
        navigateToParty(data.joinCode);
    } catch (error) {
        showError(errorId, error.message);
        setBusy(button, false, "Join party");
    }
}

function navigateToParty(joinCode) {
    window.location.href = `/?party=${encodeURIComponent(joinCode.toUpperCase())}`;
}

async function refreshParty(silent = true) {
    if (!silent) {
        renderLoading();
    }

    try {
        const party = await api(`/api/parties/code/${encodeURIComponent(currentCode)}`);
        const [suggestions, results] = await Promise.all([
            api(`/api/parties/${party.id}/suggestions`),
            api(`/api/parties/${party.id}/voting`)
        ]);
        state = { party, suggestions, results };
        renderParty();
        startPolling();
    } catch (error) {
        stopPolling();
        renderFatalError(error.message);
    }
}

function renderParty() {
    const { party, suggestions, results } = state;
    const session = loadSession(party.joinCode);
    const isHost = Boolean(session?.hostToken);
    const canParticipate = Boolean(session?.memberToken);

    app.innerHTML = `
        <section class="stack">
            <article class="card party-heading">
                <div>
                    <span class="status">${escapeHtml(statusLabel(party.status))}</span>
                    <h1>${escapeHtml(party.name)}</h1>
                    <div class="code">Code: ${escapeHtml(party.joinCode)}</div>
                </div>
                <div class="button-row">
                    <button class="button secondary" id="copy-link" type="button">Copy invite link</button>
                    ${session ? '<button class="button secondary" id="forget-session" type="button">Leave on this device</button>' : ""}
                </div>
            </article>

            ${!session && party.status === "OPEN" ? joinCard(party.joinCode) : ""}

            <section class="party-layout">
                <div class="stack">
                    ${mainPartyCard(party, suggestions, results, session, isHost, canParticipate)}
                </div>
                <aside class="stack">
                    <article class="card">
                        <h2>People (${party.members.length})</h2>
                        <ul class="list">
                            ${party.members.map(member => `
                                <li class="list-item">
                                    <strong>${escapeHtml(member.memberName)}</strong>
                                    ${member.id === session?.memberId ? '<span class="muted">You</span>' : ""}
                                </li>
                            `).join("")}
                        </ul>
                    </article>
                    <article class="card">
                        <h2>How it works</h2>
                        <p class="muted">Add ideas while the party is open. The host starts voting, everyone gets one vote, and the app chooses randomly if the top choices tie.</p>
                    </article>
                </aside>
            </section>
        </section>
    `;

    attachPartyHandlers(party, suggestions, session, isHost);
}

function joinCard(joinCode) {
    return `
        <article class="card">
            <h2>Join this party</h2>
            <form id="join-current-party-form">
                <label>
                    Your name
                    <input name="memberName" maxlength="80" placeholder="Your name" required>
                </label>
                <div class="error" id="join-current-error" hidden></div>
                <button class="button" type="submit">Join party</button>
            </form>
            <p class="help">Party code: <strong>${escapeHtml(joinCode)}</strong></p>
        </article>
    `;
}

function mainPartyCard(party, suggestions, results, session, isHost, canParticipate) {
    if (party.status === "FINALIZED") {
        return resultCard(suggestions, results);
    }

    if (party.status === "VOTING") {
        return votingCard(party, suggestions, session, isHost, canParticipate);
    }

    return suggestionsCard(suggestions, session, isHost, canParticipate);
}

function suggestionsCard(suggestions, session, isHost, canParticipate) {
    return `
        <article class="card">
            <h2>Restaurant ideas</h2>
            ${canParticipate ? `
                <form id="suggestion-form">
                    <label>
                        Add a restaurant
                        <input name="name" maxlength="120" placeholder="Restaurant name" required>
                    </label>
                    <div class="error" id="suggestion-error" hidden></div>
                    <button class="button" type="submit">Add suggestion</button>
                </form>
            ` : '<p class="notice">Join the party to add a suggestion.</p>'}

            <div style="height: 1rem"></div>
            ${suggestions.length ? `
                <ul class="list">
                    ${suggestions.map(suggestion => `
                        <li class="list-item">
                            <div>
                                <strong>${escapeHtml(suggestion.name)}</strong>
                                <span class="muted">Suggested by ${escapeHtml(suggestion.memberName)}</span>
                            </div>
                        </li>
                    `).join("")}
                </ul>
            ` : '<p class="muted">No suggestions yet. Add the first one.</p>'}

            ${isHost ? `
                <div style="height: 1rem"></div>
                <div class="button-row">
                    <button class="button" id="start-voting" type="button" ${suggestions.length < 2 ? "disabled" : ""}>Start voting</button>
                </div>
                ${suggestions.length < 2 ? '<p class="help">At least two suggestions are required.</p>' : ""}
            ` : ""}
        </article>
    `;
}

function votingCard(party, suggestions, session, isHost, canParticipate) {
    const hasVoted = Boolean(session?.voted);
    return `
        <article class="card">
            <h2>Cast your vote</h2>
            ${!canParticipate ? '<p class="notice">Voting is underway. Only people who joined before voting started can vote.</p>' : ""}
            ${canParticipate && hasVoted ? '<p class="notice">Your vote is in. Waiting for the rest of the party…</p>' : ""}
            ${canParticipate && !hasVoted ? `
                <div class="list" id="vote-options">
                    ${suggestions.map(suggestion => `
                        <button class="vote-option" type="button" data-suggestion-id="${suggestion.id}">
                            <span>
                                <strong>${escapeHtml(suggestion.name)}</strong>
                                <span class="muted">Suggested by ${escapeHtml(suggestion.memberName)}</span>
                            </span>
                            <span>Vote</span>
                        </button>
                    `).join("")}
                </div>
                <div class="error" id="vote-error" hidden></div>
            ` : ""}
            ${isHost ? `
                <div style="height: 1rem"></div>
                <div class="button-row">
                    <button class="button secondary" id="finalize-voting" type="button">Finish with current votes</button>
                </div>
                <p class="help">Use this if someone is unavailable. At least one vote is required.</p>
                <div class="error" id="finalize-error" hidden></div>
            ` : ""}
        </article>
    `;
}

function resultCard(suggestions, results) {
    const winner = results.winnerSuggestionName || "No winner";
    return `
        <article class="card">
            <h2>The party chose</h2>
            <div class="winner">
                <span>Winner</span>
                <strong>${escapeHtml(winner)}</strong>
            </div>
            <div style="height: 1rem"></div>
            <h3>Final results</h3>
            <ul class="list">
                ${suggestions.map(suggestion => `
                    <li class="list-item">
                        <div>
                            <strong>${escapeHtml(suggestion.name)}</strong>
                            <span class="muted">Suggested by ${escapeHtml(suggestion.memberName)}</span>
                        </div>
                        <span class="vote-count">${Number(results.counts?.[suggestion.id] || 0)}</span>
                    </li>
                `).join("")}
            </ul>
            <div style="height: 1rem"></div>
            <a class="button" href="/">Start another party</a>
        </article>
    `;
}

function attachPartyHandlers(party, suggestions, session, isHost) {
    document.getElementById("copy-link")?.addEventListener("click", copyInviteLink);
    document.getElementById("forget-session")?.addEventListener("click", () => {
        localStorage.removeItem(sessionKey(party.joinCode));
        renderParty();
    });

    document.getElementById("join-current-party-form")?.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        await joinParty(party.joinCode, form.elements.memberName.value, "join-current-error", form.querySelector("button"));
    });

    document.getElementById("suggestion-form")?.addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const button = form.querySelector("button");
        setBusy(button, true, "Adding…");
        hideError("suggestion-error");
        try {
            await api(`/api/parties/${party.id}/suggestions`, {
                method: "POST",
                headers: { "X-Member-Token": session.memberToken },
                body: JSON.stringify({ name: form.elements.name.value })
            });
            form.reset();
            await refreshParty(true);
        } catch (error) {
            showError("suggestion-error", error.message);
            setBusy(button, false, "Add suggestion");
        }
    });

    document.getElementById("start-voting")?.addEventListener("click", async event => {
        const button = event.currentTarget;
        setBusy(button, true, "Starting…");
        try {
            await api(`/api/parties/${party.id}/voting/start`, {
                method: "POST",
                headers: { "X-Host-Token": session.hostToken }
            });
            await refreshParty(true);
        } catch (error) {
            window.alert(error.message);
            setBusy(button, false, "Start voting");
        }
    });

    document.querySelectorAll("[data-suggestion-id]").forEach(button => {
        button.addEventListener("click", async () => {
            document.querySelectorAll("[data-suggestion-id]").forEach(option => option.disabled = true);
            hideError("vote-error");
            try {
                await api(`/api/parties/${party.id}/voting/votes`, {
                    method: "POST",
                    headers: { "X-Member-Token": session.memberToken },
                    body: JSON.stringify({ suggestionId: Number(button.dataset.suggestionId) })
                });
                session.voted = true;
                saveSession(party.joinCode, session);
                await refreshParty(true);
            } catch (error) {
                if (error.status === 409) {
                    session.voted = true;
                    saveSession(party.joinCode, session);
                    await refreshParty(true);
                    return;
                }
                showError("vote-error", error.message);
                document.querySelectorAll("[data-suggestion-id]").forEach(option => option.disabled = false);
            }
        });
    });

    document.getElementById("finalize-voting")?.addEventListener("click", async event => {
        const button = event.currentTarget;
        setBusy(button, true, "Finishing…");
        hideError("finalize-error");
        try {
            await api(`/api/parties/${party.id}/voting/finalize`, {
                method: "POST",
                headers: { "X-Host-Token": session.hostToken }
            });
            await refreshParty(true);
        } catch (error) {
            showError("finalize-error", error.message);
            setBusy(button, false, "Finish with current votes");
        }
    });
}

async function copyInviteLink(event) {
    const button = event.currentTarget;
    const url = `${window.location.origin}/?party=${encodeURIComponent(currentCode)}`;
    try {
        await navigator.clipboard.writeText(url);
        button.textContent = "Copied";
        window.setTimeout(() => button.textContent = "Copy invite link", 1400);
    } catch {
        window.prompt("Copy this invite link:", url);
    }
}

function startPolling() {
    if (pollTimer) return;
    pollTimer = window.setInterval(() => {
        if (document.activeElement?.matches("input")) return;
        refreshParty(true);
    }, 3000);
}

function stopPolling() {
    if (pollTimer) {
        window.clearInterval(pollTimer);
        pollTimer = null;
    }
}

async function api(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (options.body) {
        headers["Content-Type"] = "application/json";
    }

    const response = await fetch(path, { ...options, headers });
    if (!response.ok) {
        let body = null;
        try {
            body = await response.json();
        } catch {
            body = null;
        }
        const error = new Error(body?.message || "Something went wrong");
        error.status = response.status;
        throw error;
    }

    if (response.status === 204) return null;
    return response.json();
}

function sessionKey(joinCode) {
    return `semafork.session.${joinCode.toUpperCase()}`;
}

function saveSession(joinCode, session) {
    localStorage.setItem(sessionKey(joinCode), JSON.stringify(session));
}

function loadSession(joinCode) {
    try {
        return JSON.parse(localStorage.getItem(sessionKey(joinCode)) || "null");
    } catch {
        localStorage.removeItem(sessionKey(joinCode));
        return null;
    }
}

function statusLabel(status) {
    return {
        OPEN: "Adding ideas",
        VOTING: "Voting",
        FINALIZED: "Decided"
    }[status] || status;
}

function setBusy(button, busy, busyText) {
    if (!button) return;
    if (!button.dataset.defaultText) button.dataset.defaultText = button.textContent;
    button.disabled = busy;
    button.textContent = busy ? busyText : button.dataset.defaultText;
}

function showError(id, message) {
    const element = document.getElementById(id);
    if (!element) return;
    element.textContent = message;
    element.hidden = false;
}

function hideError(id) {
    const element = document.getElementById(id);
    if (element) element.hidden = true;
}

function renderLoading() {
    app.innerHTML = `
        <section class="card loading-card">
            <div>
                <div class="spinner" aria-hidden="true"></div>
                <p>Loading party…</p>
            </div>
        </section>
    `;
}

function renderFatalError(message) {
    app.innerHTML = `
        <section class="hero">
            <h1>We could not open that party.</h1>
            <p>${escapeHtml(message)}</p>
        </section>
        <section class="card">
            <a class="button" href="/">Return home</a>
        </section>
    `;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

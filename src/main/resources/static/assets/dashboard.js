(() => {
    "use strict";

    const STORAGE_KEY = "persons-finder.dashboard.v1";
    const STORAGE_VERSION = 1;
    const DEFAULT_CENTER = Object.freeze({
        latitude: -36.8485,
        longitude: 174.7633,
    });
    const DEFAULT_RADIUS_KM = 10;
    const NEARBY_DEBOUNCE_MS = 275;
    const NEARBY_LIST_PAGE_SIZE = 250;
    const NEARBY_MARKER_BATCH_SIZE = 500;
    const MAX_SESSION_PEOPLE = 500;
    const MAX_BIO_CODE_POINTS = 732;
    const TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
    const TILE_ATTRIBUTION =
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
    const UUID_PATTERN =
        /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

    const PROFILE_LIMITS = Object.freeze({
        name: 80,
        jobTitle: 80,
        hobby: 60,
        hobbies: 10,
    });

    const state = {
        map: null,
        tileLayer: null,
        searchCenter: { ...DEFAULT_CENTER },
        radiusKm: DEFAULT_RADIUS_KM,
        mode: "add",
        centerMarker: null,
        geofence: null,
        personMarkers: new Map(),
        nearbyMarkers: new Map(),
        nearbyMarkerLayer: null,
        nearbyRenderer: null,
        nearbyMarkerColors: null,
        nearbyMarkerFrame: null,
        nearbyMarkerRenderSequence: 0,
        renderedNearbyPeople: null,
        renderedNearbyTrackedIds: "",
        sessionPeople: new Map(),
        nearbyPeople: [],
        nearbyStatus: "idle",
        nearbyAbortController: null,
        nearbyTimer: null,
        nearbySequence: 0,
        nearbyListOffset: 0,
        selectedPersonId: null,
        detailReturnFocus: null,
        pendingLocationIds: new Set(),
        pendingCreateCoordinates: null,
        toastTimer: null,
        storageAvailable: true,
    };

    class RequestError extends Error {
        constructor(userMessage, status = null) {
            super("Dashboard request failed");
            this.name = "RequestError";
            this.userMessage = userMessage;
            this.status = status;
        }
    }

    const byId = (id) => document.getElementById(id);

    const firstById = (...ids) => {
        for (const id of ids) {
            const element = byId(id);
            if (element) {
                return element;
            }
        }
        return null;
    };

    const ui = {};

    function collectDom() {
        Object.assign(ui, {
            map: byId("map"),
            mapStatus: byId("map-status"),
            tileWarning: byId("tile-warning"),
            appStatus: byId("app-status"),
            apiStatusLabel: byId("api-status-label"),
            modeAdd: byId("mode-add"),
            modeCenter: byId("mode-center"),
            mapModeHelp: byId("map-mode-help"),
            addAtCenter: firstById("add-at-center", "open-create-person"),
            radiusInput: byId("radius-input"),
            radiusRange: byId("radius-range"),
            radiusOutput: byId("radius-output"),
            refreshNearby: byId("refresh-nearby"),
            centerLatitude: firstById("centre-latitude", "center-latitude"),
            centerLongitude: firstById("centre-longitude", "center-longitude"),
            applyCenter: firstById("apply-centre", "apply-center"),
            createDialog: byId("create-dialog"),
            createForm: byId("create-form"),
            personName: byId("person-name"),
            personJob: byId("person-job"),
            nameCount: byId("name-count"),
            jobCount: byId("job-count"),
            hobbiesList: byId("hobbies-list"),
            addHobby: byId("add-hobby"),
            hobbyCount: byId("hobby-count"),
            createLatitude: byId("create-latitude"),
            createLongitude: byId("create-longitude"),
            createError: byId("create-error"),
            createProblemTitle: byId("create-problem-title"),
            createProblemDetail: byId("create-problem-detail"),
            createProblemList: byId("create-problem-list"),
            createCancel: byId("create-cancel"),
            createClose: byId("close-create-dialog"),
            createSubmit: byId("create-submit"),
            nearbyList: byId("nearby-list"),
            nearbyCount: byId("nearby-count"),
            nearbyState: byId("nearby-state"),
            nearbyEmpty: byId("nearby-empty"),
            nearbyPagination: byId("nearby-pagination"),
            nearbyPrevious: byId("nearby-previous"),
            nearbyNext: byId("nearby-next"),
            nearbyPageStatus: byId("nearby-page-status"),
            sessionList: byId("session-list"),
            sessionCount: byId("session-count"),
            sessionState: byId("session-state"),
            detailDialog: byId("person-detail-dialog"),
            detailClose: byId("close-person-detail"),
            detailContent: byId("detail-content"),
            forgetSession: byId("forget-session"),
            toastRegion: byId("toast-region"),
        });
    }

    function textElement(tagName, className, text) {
        const element = document.createElement(tagName);
        if (className) {
            element.className = className;
        }
        element.textContent = text;
        return element;
    }

    function clearElement(element) {
        if (element) {
            element.replaceChildren();
        }
    }

    function setText(element, value) {
        if (element) {
            element.textContent = value;
        }
    }

    function setHidden(element, hidden) {
        if (element) {
            element.hidden = hidden;
        }
    }

    function safeDisplayText(value, fallback = "", maximum = 320) {
        if (typeof value !== "string") {
            return fallback;
        }

        const cleaned = Array.from(value)
            .map((character) => {
                if (character === "\u200c" || character === "\u200d") {
                    return character;
                }
                return /[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u.test(character)
                    ? " "
                    : character;
            })
            .join("")
            .replace(/\s+/gu, " ")
            .trim();
        if (!cleaned) {
            return fallback;
        }
        return Array.from(cleaned).slice(0, maximum).join("");
    }

    function codePointCount(value) {
        return Array.from(value.normalize("NFC")).length;
    }

    function trimUnicodeWhitespace(value) {
        return value.trim();
    }

    function canonicalProfileText(value) {
        return trimUnicodeWhitespace(value).normalize("NFC");
    }

    function isWellFormedUtf16(value) {
        for (let index = 0; index < value.length; index += 1) {
            const codeUnit = value.charCodeAt(index);
            if (codeUnit >= 0xd800 && codeUnit <= 0xdbff) {
                if (index + 1 >= value.length) {
                    return false;
                }
                const next = value.charCodeAt(index + 1);
                if (next < 0xdc00 || next > 0xdfff) {
                    return false;
                }
                index += 1;
            } else if (codeUnit >= 0xdc00 && codeUnit <= 0xdfff) {
                return false;
            }
        }
        return true;
    }

    function containsForbiddenProfileCodePoint(value) {
        const withoutAllowedJoiners = value.replace(/[\u200c\u200d]/gu, "");
        return /[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u.test(withoutAllowedJoiners);
    }

    function validateProfileText(input, label, maximum) {
        const value = canonicalProfileText(input.value);
        let message = "";

        if (!value) {
            message = `${label} is required.`;
        } else if (!isWellFormedUtf16(value) || containsForbiddenProfileCodePoint(value)) {
            message = `${label} contains unsupported control or formatting characters.`;
        } else if (codePointCount(value) > maximum) {
            message = `${label} must be ${maximum} characters or fewer.`;
        }

        input.setCustomValidity(message);
        return { value, message };
    }

    function setCounter(counter, input, maximum) {
        if (!counter || !input) {
            return;
        }
        const count = codePointCount(input.value);
        counter.textContent = `${count} / ${maximum}`;
        counter.dataset.overLimit = String(count > maximum);
        counter.classList.toggle("is-over-limit", count > maximum);
    }

    function isFiniteCoordinate(latitude, longitude) {
        return (
            Number.isFinite(latitude) &&
            Number.isFinite(longitude) &&
            latitude >= -90 &&
            latitude <= 90 &&
            longitude >= -180 &&
            longitude <= 180
        );
    }

    function wrapLongitude(longitude) {
        const wrapped = ((((longitude + 180) % 360) + 360) % 360) - 180;
        return wrapped === -180 && longitude > 0 ? 180 : wrapped;
    }

    function fromLeafletLatLng(latLng) {
        return {
            latitude: Math.max(-90, Math.min(90, latLng.lat)),
            longitude: wrapLongitude(latLng.lng),
        };
    }

    function parseCoordinates(latitudeValue, longitudeValue) {
        if (
            String(latitudeValue).trim() === "" ||
            String(longitudeValue).trim() === ""
        ) {
            return null;
        }
        const latitude = Number(latitudeValue);
        const longitude = Number(longitudeValue);
        if (!isFiniteCoordinate(latitude, longitude)) {
            return null;
        }
        return { latitude, longitude };
    }

    function formatCoordinate(value, precision = 6) {
        return Number(value).toFixed(precision);
    }

    function formatRadius(radiusKm) {
        return Number(radiusKm.toFixed(2)).toString();
    }

    function formatTimestamp(value) {
        if (typeof value !== "string") {
            return "Unavailable";
        }
        const timestamp = Date.parse(value);
        if (!Number.isFinite(timestamp)) {
            return safeDisplayText(value, "Unavailable", 80);
        }
        return new Intl.DateTimeFormat(undefined, {
            dateStyle: "medium",
            timeStyle: "short",
        }).format(new Date(timestamp));
    }

    function timestampsDiffer(first, second) {
        if (typeof first !== "string" || typeof second !== "string") {
            return false;
        }
        const firstMillis = Date.parse(first);
        const secondMillis = Date.parse(second);
        if (Number.isFinite(firstMillis) && Number.isFinite(secondMillis)) {
            return firstMillis !== secondMillis;
        }
        return first !== second;
    }

    function nearbyPersonById(personId) {
        return state.nearbyPeople.find((person) => person.id === personId) || null;
    }

    function sessionPersonIsStale(person) {
        if (state.nearbyStatus !== "success") {
            return false;
        }
        const nearbyPerson = nearbyPersonById(person.id);
        return Boolean(
            nearbyPerson &&
                timestampsDiffer(
                    person.lastKnownLocationAt,
                    nearbyPerson.lastKnownLocationAt,
                ),
        );
    }

    function showAppStatus(message, tone = "neutral") {
        if (!ui.appStatus) {
            return;
        }
        setText(ui.apiStatusLabel || ui.appStatus, message);
        ui.appStatus.dataset.state =
            tone === "error"
                ? "error"
                : tone === "warning"
                  ? "warning"
                  : tone === "loading"
                    ? "loading"
                    : "ready";
    }

    function showToast(message, tone = "neutral") {
        if (!ui.toastRegion) {
            showAppStatus(message, tone);
            return;
        }

        window.clearTimeout(state.toastTimer);
        const toast = textElement("div", "toast", message);
        toast.dataset.state = tone;
        ui.toastRegion.replaceChildren(toast);
        ui.toastRegion.hidden = false;
        state.toastTimer = window.setTimeout(() => {
            ui.toastRegion.hidden = true;
            ui.toastRegion.textContent = "";
        }, 5000);
    }

    function setCreateError(message) {
        if (!ui.createError) {
            if (message) {
                showToast(message, "error");
            }
            return;
        }
        if (ui.createProblemDetail) {
            ui.createProblemDetail.textContent = message;
            clearElement(ui.createProblemList);
        } else {
            ui.createError.textContent = message;
        }
        ui.createError.hidden = !message;
    }

    function setNearbyState(message, tone = "neutral") {
        if (ui.nearbyState) {
            ui.nearbyState.textContent = message;
            ui.nearbyState.dataset.state = tone;
        }
    }

    function setNearbyCount(value) {
        if (!ui.nearbyCount) {
            return;
        }
        const count = Number(value);
        const hasCount =
            value !== null &&
            value !== undefined &&
            Number.isInteger(count) &&
            count >= 0;
        ui.nearbyCount.textContent = hasCount ? String(count) : "—";
        ui.nearbyCount.setAttribute(
            "aria-label",
            hasCount
                ? `${count} nearby ${count === 1 ? "person" : "people"}`
                : "Nearby count unavailable",
        );
    }

    function fieldLabel(field) {
        const labels = {
            name: "Name",
            jobTitle: "Job",
            hobbies: "Hobbies",
            profile: "Profile",
            location: "Location",
            "location.latitude": "Latitude",
            "location.longitude": "Longitude",
            latitude: "Latitude",
            longitude: "Longitude",
            lat: "Search latitude",
            lon: "Search longitude",
            radius: "Radius",
        };
        return labels[field] || safeDisplayText(field, "Request", 60);
    }

    function violationLabel(code) {
        const labels = {
            REQUIRED: "is required",
            INVALID_TYPE: "has the wrong type",
            INVALID_FORMAT: "has an invalid format",
            UNKNOWN_FIELD: "is not accepted",
            OUT_OF_RANGE: "is out of range",
            TOO_LONG: "is too long",
            TOO_MANY_ITEMS: "has too many items",
            DUPLICATE_ITEM: "contains a duplicate",
        };
        return labels[code] || "is invalid";
    }

    async function responseErrorMessage(response, fallback) {
        let problem = null;
        try {
            problem = await response.json();
        } catch (_ignored) {
            return `${fallback} (HTTP ${response.status}).`;
        }

        const detail = safeDisplayText(problem?.detail, "", 320);
        const title = safeDisplayText(problem?.title, "", 120);
        const violations = Array.isArray(problem?.violations)
            ? problem.violations.slice(0, 12)
            : [];
        const violationSummary = violations
            .map((violation) => {
                const field = fieldLabel(violation?.field);
                const reason = violationLabel(violation?.code);
                return `${field} ${reason}.`;
            })
            .join(" ");

        return [detail || title || `${fallback} (HTTP ${response.status}).`, violationSummary]
            .filter(Boolean)
            .join(" ");
    }

    async function requestJson(url, options, fallback) {
        let response;
        try {
            response = await window.fetch(url, {
                credentials: "same-origin",
                ...options,
                headers: {
                    Accept: "application/json, application/problem+json",
                    ...(options?.headers || {}),
                },
            });
        } catch (error) {
            if (error?.name === "AbortError") {
                throw error;
            }
            throw new RequestError("The server could not be reached. Try again.");
        }

        if (!response.ok) {
            throw new RequestError(
                await responseErrorMessage(response, fallback),
                response.status,
            );
        }

        try {
            return await response.json();
        } catch (_ignored) {
            throw new RequestError("The server returned an unreadable response.");
        }
    }

    function userMessage(error, fallback) {
        return error instanceof RequestError ? error.userMessage : fallback;
    }

    function normalizeSessionPerson(value) {
        if (
            !value ||
            typeof value !== "object" ||
            typeof value.id !== "string" ||
            !UUID_PATTERN.test(value.id) ||
            typeof value.name !== "string" ||
            typeof value.jobTitle !== "string" ||
            !Array.isArray(value.hobbies) ||
            typeof value.bio !== "string" ||
            !isFiniteCoordinate(Number(value.latitude), Number(value.longitude))
        ) {
            return null;
        }

        const name = canonicalProfileText(value.name);
        const jobTitle = canonicalProfileText(value.jobTitle);
        const hobbies = value.hobbies
            .filter((hobby) => typeof hobby === "string")
            .map(canonicalProfileText)
            .filter(Boolean)
            .slice(0, PROFILE_LIMITS.hobbies);
        if (
            !name ||
            !jobTitle ||
            codePointCount(name) > PROFILE_LIMITS.name ||
            codePointCount(jobTitle) > PROFILE_LIMITS.jobTitle ||
            hobbies.length < 1 ||
            hobbies.some((hobby) => codePointCount(hobby) > PROFILE_LIMITS.hobby)
        ) {
            return null;
        }

        return {
            id: value.id,
            name,
            jobTitle,
            hobbies,
            bio: safeDisplayText(
                value.bio,
                "Bio unavailable.",
                MAX_BIO_CODE_POINTS,
            ),
            createdAt:
                typeof value.createdAt === "string" ? value.createdAt : "",
            lastKnownLocationAt:
                typeof value.lastKnownLocationAt === "string"
                    ? value.lastKnownLocationAt
                    : "",
            latitude: Number(value.latitude),
            longitude: Number(value.longitude),
        };
    }

    function loadSessionPeople() {
        try {
            const raw = window.sessionStorage.getItem(STORAGE_KEY);
            if (!raw) {
                return;
            }
            const stored = JSON.parse(raw);
            if (
                stored?.version !== STORAGE_VERSION ||
                !Array.isArray(stored.people)
            ) {
                window.sessionStorage.removeItem(STORAGE_KEY);
                return;
            }

            stored.people
                .slice(0, MAX_SESSION_PEOPLE)
                .map(normalizeSessionPerson)
                .filter(Boolean)
                .forEach((person) => state.sessionPeople.set(person.id, person));
        } catch (_ignored) {
            state.storageAvailable = false;
            showAppStatus(
                "Tab storage is unavailable. Map positions will last only until this page reloads.",
                "warning",
            );
        }
    }

    function persistSessionPeople() {
        if (!state.storageAvailable) {
            return;
        }

        const people = Array.from(state.sessionPeople.values()).map((person) => ({
            id: person.id,
            name: person.name,
            jobTitle: person.jobTitle,
            hobbies: [...person.hobbies],
            bio: person.bio,
            createdAt: person.createdAt,
            lastKnownLocationAt: person.lastKnownLocationAt,
            latitude: person.latitude,
            longitude: person.longitude,
        }));
        try {
            window.sessionStorage.setItem(
                STORAGE_KEY,
                JSON.stringify({ version: STORAGE_VERSION, people }),
            );
        } catch (_ignored) {
            state.storageAvailable = false;
            showAppStatus(
                "Tab storage is full or unavailable. New map positions will not survive a reload.",
                "warning",
            );
        }
    }

    function forgetSessionPeople() {
        state.sessionPeople.clear();
        state.selectedPersonId = null;
        state.pendingLocationIds.clear();
        try {
            window.sessionStorage.removeItem(STORAGE_KEY);
        } catch (_ignored) {
            state.storageAvailable = false;
        }
        renderAll();
        showToast(
            "Tab map data cleared. No person was deleted from the backend.",
            "success",
        );
    }

    function customPersonIcon(person) {
        let membership = "unverified";
        if (state.nearbyStatus === "success") {
            membership = nearbyPersonById(person.id) ? "included" : "excluded";
        }
        const stale = sessionPersonIsStale(person);
        const selected = state.selectedPersonId === person.id;
        const saving = state.pendingLocationIds.has(person.id);
        const classNames = [
            "person-map-marker",
            membership === "included" ? "is-nearby" : "",
            stale ? "is-stale" : "",
            selected ? "is-selected" : "",
            saving ? "is-saving" : "",
        ]
            .filter(Boolean)
            .join(" ");

        return window.L.divIcon({
            className: "dashboard-person-map-icon",
            html: `<span class="${classNames}" aria-hidden="true">●</span>`,
            iconSize: [38, 44],
            iconAnchor: [19, 38],
        });
    }

    function customCenterIcon() {
        return window.L.divIcon({
            className: "dashboard-centre-map-icon",
            html: '<span class="centre-map-marker" aria-hidden="true"></span>',
            iconSize: [30, 30],
            iconAnchor: [15, 15],
        });
    }

    function setMarkerAccessibility(marker, label) {
        const element = marker.getElement();
        if (!element) {
            return;
        }
        element.setAttribute("aria-label", label);
        element.setAttribute("role", "button");
        element.setAttribute("tabindex", "0");
    }

    function markerAccessibilityLabel(person) {
        const status = state.pendingLocationIds.has(person.id)
            ? "Saving location."
            : "Drag to update location, or press Enter to view details.";
        const stale = sessionPersonIsStale(person)
            ? " The saved tab position may be stale."
            : "";
        return `${safeDisplayText(person.name, "Person", 80)}. ${status}${stale}`;
    }

    function addPersonMarker(person) {
        const marker = window.L.marker([person.latitude, person.longitude], {
            icon: customPersonIcon(person),
            draggable: true,
            keyboard: true,
            riseOnHover: true,
            title: safeDisplayText(person.name, "Person", 80),
        }).addTo(state.map);

        marker.on("add", () => {
            setMarkerAccessibility(marker, markerAccessibilityLabel(person));
        });
        marker.on("click", () => selectPerson(person.id, false));
        marker.on("keypress", (event) => {
            if (event.originalEvent?.key === "Enter") {
                selectPerson(person.id, false);
            }
        });
        marker.on("dragstart", () => {
            marker._dashboardStartLatLng = marker.getLatLng();
        });
        marker.on("dragend", () => {
            const previousLatLng =
                marker._dashboardStartLatLng ||
                window.L.latLng(person.latitude, person.longitude);
            const nextCoordinates = fromLeafletLatLng(marker.getLatLng());
            void updatePersonLocation(
                person.id,
                nextCoordinates,
                marker,
                previousLatLng,
                null,
            );
        });

        state.personMarkers.set(person.id, marker);
    }

    function renderPersonMarkers() {
        if (!state.map) {
            return;
        }

        for (const [personId, marker] of state.personMarkers.entries()) {
            if (!state.sessionPeople.has(personId)) {
                state.map.removeLayer(marker);
                state.personMarkers.delete(personId);
            }
        }

        for (const person of state.sessionPeople.values()) {
            let marker = state.personMarkers.get(person.id);
            if (!marker) {
                addPersonMarker(person);
                marker = state.personMarkers.get(person.id);
            }
            if (!state.pendingLocationIds.has(person.id)) {
                marker.setLatLng([person.latitude, person.longitude]);
            }
            marker.setIcon(customPersonIcon(person));
            if (state.pendingLocationIds.has(person.id)) {
                marker.dragging?.disable();
            } else {
                marker.dragging?.enable();
            }
            setMarkerAccessibility(marker, markerAccessibilityLabel(person));
            if (state.selectedPersonId === person.id) {
                marker.setZIndexOffset(1000);
            } else {
                marker.setZIndexOffset(0);
            }
        }
    }

    function cssColor(variable, fallback) {
        const value = window
            .getComputedStyle(document.documentElement)
            .getPropertyValue(variable)
            .trim();
        return value || fallback;
    }

    function applyNearbyMarkerStyle(marker, personId) {
        if (!marker) {
            return;
        }
        const colors = state.nearbyMarkerColors || {
            violet: "#5b5ce2",
            surface: "#fffdf8",
            emerald: "#0e8f73",
        };
        const selected = state.selectedPersonId === personId;
        marker.setRadius(selected ? 7 : 4);
        marker.setStyle({
            color: selected
                ? colors.violet
                : colors.surface,
            weight: selected ? 3 : 1,
            opacity: selected ? 1 : 0.9,
            fillColor: colors.emerald,
            fillOpacity: selected ? 1 : 0.72,
        });
    }

    function updateNearbyMarkerSelection(previousPersonId, nextPersonId) {
        if (previousPersonId && previousPersonId !== nextPersonId) {
            applyNearbyMarkerStyle(
                state.nearbyMarkers.get(previousPersonId),
                previousPersonId,
            );
        }
        if (nextPersonId) {
            applyNearbyMarkerStyle(
                state.nearbyMarkers.get(nextPersonId),
                nextPersonId,
            );
        }
    }

    function clearNearbyMarkers() {
        state.nearbyMarkerRenderSequence += 1;
        if (state.nearbyMarkerFrame !== null) {
            window.cancelAnimationFrame(state.nearbyMarkerFrame);
            state.nearbyMarkerFrame = null;
        }
        state.nearbyMarkerLayer?.clearLayers();
        state.nearbyMarkers.clear();
        if (ui.map) {
            ui.map.dataset.nearbyMarkerCount = "0";
        }
    }

    function renderNearbyMarkers() {
        if (!state.map || !state.nearbyMarkerLayer) {
            return;
        }

        const trackedIds = Array.from(state.sessionPeople.keys())
            .sort()
            .join(",");
        if (
            state.renderedNearbyPeople === state.nearbyPeople &&
            state.renderedNearbyTrackedIds === trackedIds
        ) {
            return;
        }
        state.renderedNearbyPeople = state.nearbyPeople;
        state.renderedNearbyTrackedIds = trackedIds;
        clearNearbyMarkers();

        if (
            state.nearbyStatus !== "success" ||
            state.nearbyPeople.length === 0
        ) {
            return;
        }

        const people = state.nearbyPeople.filter(
            (person) => !state.sessionPeople.has(person.id),
        );
        const sequence = state.nearbyMarkerRenderSequence;
        let offset = 0;

        const addBatch = () => {
            if (
                sequence !== state.nearbyMarkerRenderSequence ||
                !state.nearbyMarkerLayer
            ) {
                return;
            }

            const limit = Math.min(
                offset + NEARBY_MARKER_BATCH_SIZE,
                people.length,
            );
            for (; offset < limit; offset += 1) {
                const person = people[offset];
                const marker = window.L.circleMarker(
                    [person.latitude, person.longitude],
                    {
                        renderer: state.nearbyRenderer,
                        radius: 4,
                        bubblingMouseEvents: false,
                        interactive: true,
                    },
                );
                applyNearbyMarkerStyle(marker, person.id);
                marker.on("click", () => selectPerson(person.id, true));
                marker.addTo(state.nearbyMarkerLayer);
                state.nearbyMarkers.set(person.id, marker);
            }
            if (ui.map) {
                ui.map.dataset.nearbyMarkerCount = String(offset);
            }

            if (offset < people.length) {
                state.nearbyMarkerFrame =
                    window.requestAnimationFrame(addBatch);
            } else {
                state.nearbyMarkerFrame = null;
            }
        };

        addBatch();
    }

    function updateCenterVisuals() {
        setText(ui.radiusOutput, `${formatRadius(state.radiusKm)} km`);
        if (ui.radiusInput && document.activeElement !== ui.radiusInput) {
            ui.radiusInput.value = formatRadius(state.radiusKm);
        }
        if (ui.radiusRange && document.activeElement !== ui.radiusRange) {
            ui.radiusRange.value = formatRadius(state.radiusKm);
        }
        if (ui.centerLatitude && document.activeElement !== ui.centerLatitude) {
            ui.centerLatitude.value = formatCoordinate(
                state.searchCenter.latitude,
            );
        }
        if (ui.centerLongitude && document.activeElement !== ui.centerLongitude) {
            ui.centerLongitude.value = formatCoordinate(
                state.searchCenter.longitude,
            );
        }

        if (!state.map) {
            return;
        }
        const latLng = [
            state.searchCenter.latitude,
            state.searchCenter.longitude,
        ];
        state.centerMarker.setLatLng(latLng);
        state.geofence.setLatLng(latLng);
        state.geofence.setRadius(state.radiusKm * 1000);
        setMarkerAccessibility(
            state.centerMarker,
            "Nearby search centre. Drag to change the centre.",
        );
    }

    function initializeMap() {
        if (!ui.map) {
            showAppStatus(
                "The map container is missing, so map interactions are unavailable.",
                "error",
            );
            return;
        }
        ui.map.setAttribute("aria-label", "Persons and nearby search map");

        if (!window.L) {
            showAppStatus(
                "The map library could not load. Nearby queries still work through the coordinate controls.",
                "error",
            );
            return;
        }

        state.map = window.L.map(ui.map, {
            keyboard: true,
            zoomControl: true,
            preferCanvas: false,
        }).setView(
            [state.searchCenter.latitude, state.searchCenter.longitude],
            13,
        );
        const nearbyPeoplePane = state.map.createPane("nearbyPeoplePane");
        nearbyPeoplePane.style.zIndex = "410";
        state.nearbyRenderer = window.L.canvas({
            pane: "nearbyPeoplePane",
            padding: 0.5,
            tolerance: 5,
        });
        state.nearbyMarkerColors = {
            violet: cssColor("--violet", "#5b5ce2"),
            surface: cssColor("--surface", "#fffdf8"),
            emerald: cssColor("--emerald", "#0e8f73"),
        };
        state.nearbyMarkerLayer = window.L.layerGroup().addTo(state.map);

        const externalTilesEnabled =
            new URLSearchParams(window.location.search).get("tiles") === "osm";
        if (!externalTilesEnabled) {
            if (ui.tileWarning) {
                ui.tileWarning.textContent =
                    "Basemap disabled by default to prevent third-party location egress. Add ?tiles=osm to opt in; API tools and markers still work.";
            }
            setHidden(ui.tileWarning, false);
        } else {
            if (ui.tileWarning) {
                ui.tileWarning.textContent =
                    "OpenStreetMap basemap enabled by opt-in. The viewed area is sent to its public tile service.";
            }
            setHidden(ui.tileWarning, false);
            state.tileLayer = window.L.tileLayer(TILE_URL, {
                attribution: TILE_ATTRIBUTION,
                maxZoom: 19,
                minZoom: 2,
                updateWhenIdle: true,
                updateWhenZooming: false,
                keepBuffer: 0,
                detectRetina: false,
            });
            state.tileLayer.on("tileerror", () => {
                if (ui.tileWarning) {
                    ui.tileWarning.textContent =
                        "The opted-in OpenStreetMap basemap is unavailable. API tools and markers still work.";
                }
                setHidden(ui.tileWarning, false);
            });
            state.tileLayer.addTo(state.map);
        }

        state.geofence = window.L.circle(
            [state.searchCenter.latitude, state.searchCenter.longitude],
            {
                radius: state.radiusKm * 1000,
                className: "nearby-radius",
                color: "#5b5ce2",
                fillColor: "#5b5ce2",
                fillOpacity: 0.08,
                weight: 2,
                interactive: false,
            },
        ).addTo(state.map);

        state.centerMarker = window.L.marker(
            [state.searchCenter.latitude, state.searchCenter.longitude],
            {
                icon: customCenterIcon(),
                draggable: true,
                keyboard: true,
                zIndexOffset: 2000,
                title: "Nearby search centre",
            },
        ).addTo(state.map);
        state.centerMarker.on("add", updateCenterVisuals);
        state.centerMarker.on("dragend", () => {
            setSearchCenter(
                fromLeafletLatLng(state.centerMarker.getLatLng()),
                false,
            );
        });
        state.centerMarker.on("keypress", (event) => {
            if (event.originalEvent?.key === "Enter") {
                ui.centerLatitude?.focus();
            }
        });

        state.map.on("click", (event) => {
            const coordinates = fromLeafletLatLng(event.latlng);
            if (state.mode === "center") {
                setSearchCenter(coordinates, false);
            } else {
                openCreateDialog(coordinates);
            }
        });
        state.map.on("focus", updateMapInstructions);

        updateCenterVisuals();
        renderPersonMarkers();
    }

    function setMode(mode, focusMap = true) {
        if (mode !== "add" && mode !== "center") {
            return;
        }
        state.mode = mode;
        ui.modeAdd?.setAttribute("aria-pressed", String(mode === "add"));
        ui.modeCenter?.setAttribute("aria-pressed", String(mode === "center"));
        ui.modeAdd?.classList.toggle("is-active", mode === "add");
        ui.modeCenter?.classList.toggle("is-active", mode === "center");
        setText(
            ui.mapModeHelp,
            mode === "add"
                ? "Click the map to open a person form at that location."
                : "Click the map to move the nearby search centre.",
        );
        if (ui.map) {
            ui.map.dataset.mode = mode;
        }
        updateMapInstructions();
        if (focusMap && state.map) {
            state.map.getContainer().focus();
        }
    }

    function updateMapInstructions() {
        const instruction =
            state.mode === "add"
                ? "Add mode. Select a map location to open the new person form."
                : "Search centre mode. Select a map location or drag the centre pin to run a nearby search.";
        setText(ui.mapStatus, instruction);
    }

    function setSearchCenter(coordinates, panMap) {
        if (!isFiniteCoordinate(coordinates.latitude, coordinates.longitude)) {
            showToast("Search centre coordinates are out of range.", "error");
            return;
        }
        state.searchCenter = {
            latitude: coordinates.latitude,
            longitude: coordinates.longitude,
        };
        updateCenterVisuals();
        if (panMap && state.map) {
            state.map.panTo([
                state.searchCenter.latitude,
                state.searchCenter.longitude,
            ]);
        }
        scheduleNearbySearch();
    }

    function applyCenterInputs() {
        if (!ui.centerLatitude || !ui.centerLongitude) {
            return;
        }
        const coordinates = parseCoordinates(
            ui.centerLatitude.value,
            ui.centerLongitude.value,
        );
        if (!coordinates) {
            showToast(
                "Search latitude must be -90 to 90 and longitude -180 to 180.",
                "error",
            );
            updateCenterVisuals();
            return;
        }
        setSearchCenter(coordinates, true);
    }

    function applyRadiusInput(event) {
        const source = event?.currentTarget || ui.radiusInput || ui.radiusRange;
        if (!source) {
            return;
        }
        const radius = Number(source.value);
        if (!Number.isFinite(radius) || radius <= 0 || radius > 100) {
            source.setCustomValidity(
                "Radius must be greater than 0 and no more than 100 kilometres.",
            );
            source.reportValidity();
            return;
        }
        source.setCustomValidity("");
        state.radiusKm = radius;
        updateCenterVisuals();
        scheduleNearbySearch();
    }

    function addHobbyRow(initialValue = "") {
        if (!ui.hobbiesList) {
            return;
        }
        const existingRows = ui.hobbiesList.querySelectorAll(".hobby-row");
        if (existingRows.length >= PROFILE_LIMITS.hobbies) {
            return;
        }

        const template = byId("hobby-row-template");
        const row = template?.content?.firstElementChild
            ? template.content.firstElementChild.cloneNode(true)
            : document.createElement("div");
        row.classList.add("hobby-row");
        row.setAttribute("data-hobby-row", "");

        let input =
            row.querySelector("[data-hobby-input]") ||
            row.querySelector('input[name="hobbies"]');
        if (!input) {
            const field = document.createElement("div");
            field.className = "field-group";
            input = document.createElement("input");
            field.append(input);
            row.append(field);
        }
        input.classList.add("hobby-input");
        input.type = "text";
        input.name = "hobbies";
        input.autocomplete = "off";
        input.required = true;
        input.value = initialValue;
        input.setAttribute("aria-label", `Hobby ${existingRows.length + 1}`);
        const suggestionList = byId("hobby-suggestions");
        if (suggestionList) {
            input.setAttribute("list", suggestionList.id);
        }

        let counter = row.querySelector("[data-hobby-character-count]");
        if (!counter) {
            counter = textElement(
                "span",
                "character-count",
                `0 / ${PROFILE_LIMITS.hobby}`,
            );
            counter.setAttribute("data-hobby-character-count", "");
            row.append(counter);
        }
        counter.setAttribute("aria-hidden", "true");

        let remove = row.querySelector("[data-remove-hobby]");
        if (!remove) {
            remove = textElement("button", "icon-button remove-hobby", "×");
            remove.setAttribute("data-remove-hobby", "");
            row.append(remove);
        }
        remove.type = "button";
        remove.setAttribute(
            "aria-label",
            `Remove hobby ${existingRows.length + 1}`,
        );
        remove.addEventListener("click", () => {
            const rows = ui.hobbiesList.querySelectorAll(".hobby-row");
            if (rows.length <= 1) {
                input.value = "";
                input.focus();
                setCounter(counter, input, PROFILE_LIMITS.hobby);
                return;
            }
            row.remove();
            syncHobbyRows();
        });
        input.addEventListener("input", () => {
            input.setCustomValidity("");
            setCounter(counter, input, PROFILE_LIMITS.hobby);
        });

        ui.hobbiesList.append(row);
        setCounter(counter, input, PROFILE_LIMITS.hobby);
        syncHobbyRows();
        input.focus();
    }

    function syncHobbyRows() {
        if (!ui.hobbiesList) {
            return;
        }
        const rows = Array.from(
            ui.hobbiesList.querySelectorAll(".hobby-row"),
        );
        rows.forEach((row, index) => {
            const input =
                row.querySelector(".hobby-input") ||
                row.querySelector('input[name="hobbies"]');
            const remove =
                row.querySelector("[data-remove-hobby]") ||
                row.querySelector(".hobby-remove");
            const label = row.querySelector("[data-hobby-label]");
            input?.setAttribute("aria-label", `Hobby ${index + 1}`);
            if (input) {
                input.id = `hobby-${index}`;
                input.classList.add("hobby-input");
            }
            if (label) {
                label.htmlFor = `hobby-${index}`;
                label.textContent = `Hobby ${index + 1}`;
            }
            if (remove) {
                remove.disabled = rows.length === 1;
                remove.setAttribute(
                    "aria-label",
                    `Remove hobby ${index + 1}`,
                );
            }
        });
        if (ui.addHobby) {
            ui.addHobby.disabled = rows.length >= PROFILE_LIMITS.hobbies;
        }
        setText(
            ui.hobbyCount,
            `${rows.length} / ${PROFILE_LIMITS.hobbies}`,
        );
    }

    function resetHobbyRows() {
        if (!ui.hobbiesList) {
            return;
        }
        ui.hobbiesList.replaceChildren();
        addHobbyRow("");
    }

    function openDialog(dialog) {
        if (typeof dialog.showModal === "function") {
            dialog.showModal();
        } else {
            dialog.setAttribute("open", "");
        }
    }

    function closeDialog(dialog) {
        if (typeof dialog.close === "function") {
            dialog.close();
        } else {
            dialog.removeAttribute("open");
        }
    }

    function openCreateDialog(coordinates) {
        if (
            !ui.createDialog ||
            !ui.createForm ||
            !ui.personName ||
            !ui.personJob ||
            !ui.hobbiesList ||
            !ui.createLatitude ||
            !ui.createLongitude
        ) {
            showToast("The new person form is unavailable.", "error");
            return;
        }

        state.pendingCreateCoordinates = coordinates;
        ui.createForm.reset();
        resetHobbyRows();
        ui.createLatitude.value = formatCoordinate(coordinates.latitude);
        ui.createLongitude.value = formatCoordinate(coordinates.longitude);
        setCounter(ui.nameCount, ui.personName, PROFILE_LIMITS.name);
        setCounter(ui.jobCount, ui.personJob, PROFILE_LIMITS.jobTitle);
        setCreateError("");
        openDialog(ui.createDialog);
        window.setTimeout(() => ui.personName.focus(), 0);
    }

    function normalizedHobbiesFromForm() {
        const inputs = Array.from(
            ui.hobbiesList.querySelectorAll(".hobby-input"),
        );
        if (inputs.length < 1 || inputs.length > PROFILE_LIMITS.hobbies) {
            return {
                hobbies: [],
                invalidInput: inputs[0] || null,
                message: `Enter between 1 and ${PROFILE_LIMITS.hobbies} hobbies.`,
            };
        }

        const hobbies = [];
        const seen = new Set();
        for (let index = 0; index < inputs.length; index += 1) {
            const result = validateProfileText(
                inputs[index],
                `Hobby ${index + 1}`,
                PROFILE_LIMITS.hobby,
            );
            if (result.message) {
                return {
                    hobbies: [],
                    invalidInput: inputs[index],
                    message: result.message,
                };
            }
            if (!seen.has(result.value)) {
                seen.add(result.value);
                hobbies.push(result.value);
            }
        }
        return { hobbies, invalidInput: null, message: "" };
    }

    function validCreatePayload() {
        const name = validateProfileText(
            ui.personName,
            "Name",
            PROFILE_LIMITS.name,
        );
        const jobTitle = validateProfileText(
            ui.personJob,
            "Job",
            PROFILE_LIMITS.jobTitle,
        );
        const hobbyResult = normalizedHobbiesFromForm();
        const coordinates = parseCoordinates(
            ui.createLatitude.value,
            ui.createLongitude.value,
        );

        if (name.message) {
            ui.personName.reportValidity();
            ui.personName.focus();
            return null;
        }
        if (jobTitle.message) {
            ui.personJob.reportValidity();
            ui.personJob.focus();
            return null;
        }
        if (hobbyResult.message) {
            hobbyResult.invalidInput?.reportValidity();
            hobbyResult.invalidInput?.focus();
            setCreateError(hobbyResult.message);
            return null;
        }
        if (!coordinates) {
            const message =
                "Latitude must be -90 to 90 and longitude -180 to 180.";
            ui.createLatitude.setCustomValidity(message);
            ui.createLatitude.reportValidity();
            ui.createLatitude.focus();
            setCreateError(message);
            return null;
        }

        ui.createLatitude.setCustomValidity("");
        ui.createLongitude.setCustomValidity("");
        return {
            request: {
                name: name.value,
                jobTitle: jobTitle.value,
                hobbies: hobbyResult.hobbies,
                location: {
                    latitude: coordinates.latitude,
                    longitude: coordinates.longitude,
                },
            },
            coordinates,
        };
    }

    function normalizeCreatedPerson(response, coordinates) {
        return normalizeSessionPerson({
            ...response,
            latitude: coordinates.latitude,
            longitude: coordinates.longitude,
        });
    }

    async function createPerson(event) {
        event.preventDefault();
        setCreateError("");
        const payload = validCreatePayload();
        if (!payload) {
            return;
        }

        if (ui.createSubmit) {
            ui.createSubmit.disabled = true;
            ui.createSubmit.setAttribute("aria-busy", "true");
        }
        try {
            const response = await requestJson(
                "/persons",
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload.request),
                },
                "The person could not be created",
            );
            const person = normalizeCreatedPerson(
                response,
                payload.coordinates,
            );
            if (!person) {
                throw new RequestError(
                    "The server returned an incomplete person record.",
                );
            }

            state.sessionPeople.set(person.id, person);
            state.selectedPersonId = person.id;
            persistSessionPeople();
            closeDialog(ui.createDialog);
            renderAll();
            if (state.map) {
                state.map.panTo([person.latitude, person.longitude]);
            }
            showToast(`${person.name} was created.`, "success");
            scheduleNearbySearch(true);
        } catch (error) {
            setCreateError(
                userMessage(error, "The person could not be created. Try again."),
            );
        } finally {
            if (ui.createSubmit) {
                ui.createSubmit.disabled = false;
                ui.createSubmit.removeAttribute("aria-busy");
            }
        }
    }

    async function updatePersonLocation(
        personId,
        coordinates,
        marker,
        rollbackLatLng,
        inlineError,
    ) {
        const person = state.sessionPeople.get(personId);
        if (
            !person ||
            state.pendingLocationIds.has(personId) ||
            !isFiniteCoordinate(coordinates.latitude, coordinates.longitude)
        ) {
            marker?.setLatLng(rollbackLatLng);
            return;
        }

        state.pendingLocationIds.add(personId);
        if (inlineError) {
            inlineError.textContent = "";
            inlineError.hidden = true;
        }
        renderPersonMarkers();
        try {
            const response = await requestJson(
                `/persons/${encodeURIComponent(personId)}/location`,
                {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        latitude: coordinates.latitude,
                        longitude: coordinates.longitude,
                    }),
                },
                "The location could not be updated",
            );
            if (typeof response?.lastKnownLocationAt !== "string") {
                throw new RequestError(
                    "The server returned an incomplete location update.",
                );
            }

            const currentPerson = state.sessionPeople.get(personId);
            if (currentPerson) {
                currentPerson.latitude = coordinates.latitude;
                currentPerson.longitude = coordinates.longitude;
                currentPerson.lastKnownLocationAt =
                    response.lastKnownLocationAt;
                persistSessionPeople();
            }
            showToast(
                `${safeDisplayText(person.name, "Person", 80)} moved to the new location.`,
                "success",
            );
            scheduleNearbySearch(true);
        } catch (error) {
            marker?.setLatLng(rollbackLatLng);
            const message = userMessage(
                error,
                "The location could not be updated. The marker was restored.",
            );
            if (inlineError) {
                inlineError.textContent = message;
                inlineError.hidden = false;
            }
            showToast(message, "error");
        } finally {
            state.pendingLocationIds.delete(personId);
            renderAll();
        }
    }

    function validNearbyLocation(value) {
        return Boolean(
            value &&
                typeof value === "object" &&
                typeof value.latitude === "number" &&
                typeof value.longitude === "number" &&
                isFiniteCoordinate(value.latitude, value.longitude),
        );
    }

    function validNearbyPerson(value) {
        return Boolean(
            value &&
                typeof value === "object" &&
                typeof value.id === "string" &&
                UUID_PATTERN.test(value.id) &&
                typeof value.name === "string" &&
                typeof value.jobTitle === "string" &&
                Array.isArray(value.hobbies) &&
                typeof value.bio === "string" &&
                typeof value.lastKnownLocationAt === "string" &&
                validNearbyLocation(value.location) &&
                Number.isFinite(Number(value.distanceKm)),
        );
    }

    function normalizeNearbyPerson(value) {
        return {
            id: value.id,
            name: safeDisplayText(value.name, "Unnamed person", 80),
            jobTitle: safeDisplayText(value.jobTitle, "Job unavailable", 80),
            hobbies: value.hobbies
                .filter((hobby) => typeof hobby === "string")
                .map((hobby) => safeDisplayText(hobby, "", 60))
                .filter(Boolean)
                .slice(0, PROFILE_LIMITS.hobbies),
            bio: safeDisplayText(
                value.bio,
                "Bio unavailable.",
                MAX_BIO_CODE_POINTS,
            ),
            createdAt:
                typeof value.createdAt === "string" ? value.createdAt : "",
            lastKnownLocationAt: value.lastKnownLocationAt,
            latitude: value.location.latitude,
            longitude: value.location.longitude,
            distanceKm: Number(value.distanceKm),
        };
    }

    function synchronizeTrackedLocations(nearbyPeople) {
        let changed = false;
        nearbyPeople.forEach((nearbyPerson) => {
            const trackedPerson = state.sessionPeople.get(nearbyPerson.id);
            if (!trackedPerson) {
                return;
            }
            if (
                trackedPerson.latitude !== nearbyPerson.latitude ||
                trackedPerson.longitude !== nearbyPerson.longitude ||
                trackedPerson.lastKnownLocationAt !==
                    nearbyPerson.lastKnownLocationAt
            ) {
                trackedPerson.latitude = nearbyPerson.latitude;
                trackedPerson.longitude = nearbyPerson.longitude;
                trackedPerson.lastKnownLocationAt =
                    nearbyPerson.lastKnownLocationAt;
                changed = true;
            }
        });
        if (changed) {
            persistSessionPeople();
        }
    }

    function scheduleNearbySearch(immediate = false) {
        window.clearTimeout(state.nearbyTimer);
        if (immediate) {
            void fetchNearbyPeople();
            return;
        }
        state.nearbyTimer = window.setTimeout(
            () => void fetchNearbyPeople(),
            NEARBY_DEBOUNCE_MS,
        );
    }

    async function fetchNearbyPeople() {
        state.nearbyAbortController?.abort();
        const controller = new AbortController();
        const sequence = state.nearbySequence + 1;
        state.nearbySequence = sequence;
        state.nearbyAbortController = controller;
        state.nearbyStatus = "loading";
        state.nearbyPeople = [];
        showAppStatus("Checking API…", "loading");
        renderAll();

        const query = new URLSearchParams({
            lat: String(state.searchCenter.latitude),
            lon: String(state.searchCenter.longitude),
            radius: String(state.radiusKm),
        });

        try {
            const response = await requestJson(
                `/persons/nearby?${query.toString()}`,
                { method: "GET", signal: controller.signal },
                "The nearby search failed",
            );
            if (!Array.isArray(response) || response.some((person) => !validNearbyPerson(person))) {
                throw new RequestError(
                    "The server returned an incomplete nearby result.",
                );
            }
            if (sequence !== state.nearbySequence) {
                return;
            }
            state.nearbyPeople = response.map(normalizeNearbyPerson);
            state.nearbyListOffset = 0;
            synchronizeTrackedLocations(state.nearbyPeople);
            state.nearbyStatus = "success";
            showAppStatus("API connected", "success");
            renderAll();
        } catch (error) {
            if (error?.name === "AbortError" || sequence !== state.nearbySequence) {
                return;
            }
            state.nearbyPeople = [];
            state.nearbyStatus = "error";
            showAppStatus("API unavailable", "error");
            renderAll();
            setNearbyState(
                userMessage(error, "The nearby search could not be loaded."),
                "error",
            );
        } finally {
            if (sequence === state.nearbySequence) {
                state.nearbyAbortController = null;
            }
        }
    }

    function membershipLabel(person) {
        if (state.nearbyStatus !== "success") {
            return "Not checked";
        }
        if (nearbyPersonById(person.id)) {
            return sessionPersonIsStale(person)
                ? "Nearby · tab marker may be stale"
                : "Nearby";
        }
        return "Outside search";
    }

    function renderSessionList() {
        setText(ui.sessionCount, String(state.sessionPeople.size));
        if (!ui.sessionList) {
            return;
        }
        clearElement(ui.sessionList);

        if (state.sessionPeople.size === 0) {
            setHidden(ui.sessionState, false);
            setText(
                ui.sessionState,
                "No people have map positions retained by this tab.",
            );
            return;
        }
        setHidden(ui.sessionState, true);
        setText(
            ui.sessionState,
            "Positions shown are the last locations submitted by this tab.",
        );

        for (const person of state.sessionPeople.values()) {
            const template = byId("session-person-template");
            const item = template?.content?.firstElementChild
                ? template.content.firstElementChild.cloneNode(true)
                : document.createElement("li");
            item.classList.add("person-row");
            item.dataset.personId = person.id;
            item.dataset.nearby = String(
                state.nearbyStatus === "success" &&
                    Boolean(nearbyPersonById(person.id)),
            );
            item.dataset.saving = String(
                state.pendingLocationIds.has(person.id),
            );
            item.dataset.stale = String(sessionPersonIsStale(person));
            item.classList.toggle(
                "is-selected",
                state.selectedPersonId === person.id,
            );

            let button = item.querySelector("[data-select-person]");
            if (!button) {
                button = document.createElement("button");
                button.className = "person-row-button";
                item.append(button);
            }
            button.type = "button";
            button.setAttribute(
                "aria-current",
                String(state.selectedPersonId === person.id),
            );
            setText(
                item.querySelector("[data-person-initials]"),
                person.name
                    .split(/\s+/u)
                    .slice(0, 2)
                    .map((part) => Array.from(part)[0] || "")
                    .join("")
                    .toLocaleUpperCase(),
            );
            setText(item.querySelector("[data-person-name]"), person.name);
            setText(item.querySelector("[data-person-job]"), person.jobTitle);
            setText(
                item.querySelector("[data-person-location]"),
                `${formatCoordinate(person.latitude, 5)}, ${formatCoordinate(
                    person.longitude,
                    5,
                )} · ${membershipLabel(person)}`,
            );
            const personState = item.querySelector("[data-person-state]");
            if (personState) {
                personState.setAttribute("role", "img");
                personState.setAttribute(
                    "aria-label",
                    membershipLabel(person),
                );
                personState.title = membershipLabel(person);
            }
            button.addEventListener("click", () => selectPerson(person.id, true));
            ui.sessionList.append(item);
        }
    }

    function renderNearbyList() {
        if (!ui.nearbyList) {
            return;
        }
        clearElement(ui.nearbyList);
        setHidden(ui.nearbyPagination, true);

        if (state.nearbyStatus === "loading") {
            setHidden(ui.nearbyEmpty, true);
            setNearbyCount(null);
            ui.nearbyList.setAttribute("aria-busy", "true");
            setNearbyState("Searching the selected area…");
            return;
        }
        ui.nearbyList.removeAttribute("aria-busy");

        if (state.nearbyStatus === "error") {
            setHidden(ui.nearbyEmpty, true);
            setNearbyCount(null);
            return;
        }
        if (state.nearbyStatus !== "success") {
            setHidden(ui.nearbyEmpty, false);
            setNearbyCount(null);
            setNearbyState("Nearby results have not loaded yet.");
            return;
        }

        setNearbyCount(state.nearbyPeople.length);
        if (state.nearbyPeople.length === 0) {
            setHidden(ui.nearbyEmpty, false);
            setNearbyState("No people are inside this search radius.");
            return;
        }
        setHidden(ui.nearbyEmpty, true);
        const lastPageOffset =
            Math.floor(
                (state.nearbyPeople.length - 1) / NEARBY_LIST_PAGE_SIZE,
            ) * NEARBY_LIST_PAGE_SIZE;
        const pageStart = Math.min(
            state.nearbyListOffset,
            lastPageOffset,
        );
        const pageEnd = Math.min(
            pageStart + NEARBY_LIST_PAGE_SIZE,
            state.nearbyPeople.length,
        );
        state.nearbyListOffset = pageStart;
        setNearbyState(
            `${state.nearbyPeople.length} ${
                state.nearbyPeople.length === 1 ? "person" : "people"
            } returned in API order. Showing ${pageStart + 1}–${pageEnd}.`,
            "success",
        );

        const fragment = document.createDocumentFragment();
        const template = byId("nearby-item-template");
        state.nearbyPeople
            .slice(pageStart, pageEnd)
            .forEach((person, index) => {
                const tracked = state.sessionPeople.get(person.id);
                const stale = tracked
                    ? sessionPersonIsStale(tracked)
                    : false;
                const item = template?.content?.firstElementChild
                    ? template.content.firstElementChild.cloneNode(true)
                    : document.createElement("li");
                item.classList.add("nearby-item");
                item.dataset.personId = person.id;
                item.dataset.tracked = String(Boolean(tracked));
                item.dataset.stale = String(stale);
                item.classList.toggle(
                    "is-selected",
                    state.selectedPersonId === person.id,
                );

                let button = item.querySelector("[data-select-nearby]");
                if (!button) {
                    button = document.createElement("button");
                    button.className = "nearby-card";
                    item.append(button);
                }
                button.type = "button";
                button.setAttribute(
                    "aria-current",
                    String(state.selectedPersonId === person.id),
                );
                setText(
                    item.querySelector("[data-nearby-rank]"),
                    String(pageStart + index + 1).padStart(2, "0"),
                );
                setText(item.querySelector("[data-nearby-name]"), person.name);
                setText(
                    item.querySelector("[data-nearby-job]"),
                    person.jobTitle,
                );
                setText(
                    item.querySelector("[data-nearby-distance]"),
                    `${person.distanceKm.toFixed(1)} km`,
                );
                setText(
                    item.querySelector("[data-nearby-location-note]"),
                    `${formatCoordinate(
                        person.latitude,
                        5,
                    )}, ${formatCoordinate(person.longitude, 5)} · ${
                        tracked
                            ? stale
                                ? "tab marker may be stale"
                                : "draggable in this tab"
                            : "read-only marker"
                    }`,
                );
                fragment.append(item);
            });
        ui.nearbyList.append(fragment);
        if (state.nearbyPeople.length > NEARBY_LIST_PAGE_SIZE) {
            if (ui.nearbyPrevious) {
                ui.nearbyPrevious.disabled = pageStart === 0;
            }
            if (ui.nearbyNext) {
                ui.nearbyNext.disabled =
                    pageEnd >= state.nearbyPeople.length;
            }
            setText(
                ui.nearbyPageStatus,
                `${pageStart + 1}–${pageEnd} of ${state.nearbyPeople.length}`,
            );
            setHidden(ui.nearbyPagination, false);
        }
    }

    function detailRow(term, description) {
        const row = document.createElement("div");
        row.append(
            textElement("dt", "person-detail__term", term),
            textElement("dd", "person-detail__description", description),
        );
        return row;
    }

    function renderMoveForm(person, container) {
        const form = document.createElement("form");
        form.className = "move-person-form";

        const heading = textElement(
            "h3",
            "move-person-form__title",
            "Move with coordinates",
        );
        const help = textElement(
            "p",
            "move-person-form__help",
            "Keyboard alternative to dragging. This submits a new last-known location.",
        );

        const latitudeLabel = textElement("label", "", "Latitude");
        const latitude = document.createElement("input");
        latitude.type = "number";
        latitude.min = "-90";
        latitude.max = "90";
        latitude.step = "any";
        latitude.required = true;
        latitude.value = formatCoordinate(person.latitude);
        latitudeLabel.append(latitude);

        const longitudeLabel = textElement("label", "", "Longitude");
        const longitude = document.createElement("input");
        longitude.type = "number";
        longitude.min = "-180";
        longitude.max = "180";
        longitude.step = "any";
        longitude.required = true;
        longitude.value = formatCoordinate(person.longitude);
        longitudeLabel.append(longitude);

        const error = textElement("p", "form-error", "");
        error.hidden = true;
        error.setAttribute("role", "alert");

        const submit = textElement("button", "button button-secondary", "Move person");
        submit.type = "submit";
        submit.disabled = state.pendingLocationIds.has(person.id);
        form.append(
            heading,
            help,
            latitudeLabel,
            longitudeLabel,
            error,
            submit,
        );
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            const coordinates = parseCoordinates(
                latitude.value,
                longitude.value,
            );
            if (!coordinates) {
                error.textContent =
                    "Latitude must be -90 to 90 and longitude -180 to 180.";
                error.hidden = false;
                latitude.focus();
                return;
            }
            const marker = state.personMarkers.get(person.id) || null;
            const rollback = marker?.getLatLng() || null;
            void updatePersonLocation(
                person.id,
                coordinates,
                marker,
                rollback,
                error,
            );
        });
        container.append(form);
    }

    function closePersonDetail() {
        if (!ui.detailDialog?.open) {
            return;
        }
        const returnFocus = state.detailReturnFocus;
        state.detailReturnFocus = null;
        if (typeof ui.detailDialog.close === "function") {
            ui.detailDialog.close();
        } else {
            ui.detailDialog.removeAttribute("open");
        }
        if (
            returnFocus?.isConnected &&
            typeof returnFocus.focus === "function"
        ) {
            returnFocus.focus({ preventScroll: true });
        }
    }

    function openPersonDetail(invoker = document.activeElement) {
        if (
            invoker &&
            !ui.detailDialog?.contains(invoker) &&
            typeof invoker.focus === "function"
        ) {
            state.detailReturnFocus = invoker;
        }
        if (!ui.detailDialog || ui.detailDialog.open) {
            return;
        }
        if (typeof ui.detailDialog.show === "function") {
            ui.detailDialog.show();
        } else {
            ui.detailDialog.setAttribute("open", "");
        }
        ui.detailClose?.focus({ preventScroll: true });
    }

    function renderDetail() {
        if (!ui.detailContent) {
            return;
        }
        const nearbyPerson = state.selectedPersonId
            ? nearbyPersonById(state.selectedPersonId)
            : null;
        const trackedPerson = state.selectedPersonId
            ? state.sessionPeople.get(state.selectedPersonId) || null
            : null;
        const person = nearbyPerson || trackedPerson;

        clearElement(ui.detailContent);
        if (!person) {
            closePersonDetail();
            return;
        }

        const header = document.createElement("header");
        header.className = "detail-heading";
        const headingCopy = document.createElement("div");
        const detailHeading = textElement(
            "h3",
            "person-detail__name",
            person.name,
        );
        detailHeading.id = "person-detail-title";
        headingCopy.append(
            textElement("p", "eyebrow", nearbyPerson ? "Nearby person" : "Tab-tracked person"),
            detailHeading,
        );
        header.append(headingCopy);
        if (nearbyPerson) {
            header.append(
                textElement(
                    "span",
                    "distance-badge",
                    `${nearbyPerson.distanceKm.toFixed(1)} km`,
                ),
            );
        }

        const details = document.createElement("dl");
        details.className = "detail-list";
        details.append(
            detailRow("Job", person.jobTitle),
            detailRow("Hobbies", person.hobbies.join(", ")),
            detailRow(
                "Last known",
                formatTimestamp(
                    nearbyPerson?.lastKnownLocationAt ||
                        trackedPerson?.lastKnownLocationAt,
                ),
            ),
        );
        if (nearbyPerson) {
            details.append(
                detailRow("Distance", `${nearbyPerson.distanceKm.toFixed(1)} km`),
            );
        }
        if (trackedPerson) {
            details.append(
                detailRow(
                    "Map position",
                    `${formatCoordinate(
                        trackedPerson.latitude,
                        5,
                    )}, ${formatCoordinate(
                        trackedPerson.longitude,
                        5,
                    )} · draggable in this tab`,
                ),
            );
        } else {
            details.append(
                detailRow(
                    "Read-only map position",
                    `${formatCoordinate(
                        nearbyPerson.latitude,
                        5,
                    )}, ${formatCoordinate(nearbyPerson.longitude, 5)}`,
                ),
            );
        }

        const bioHeading = textElement("h3", "", "Bio");
        const bio = textElement("p", "person-detail__bio", person.bio);
        ui.detailContent.append(header, details, bioHeading, bio);

        if (trackedPerson && sessionPersonIsStale(trackedPerson)) {
            ui.detailContent.append(
                textElement(
                    "p",
                    "notice notice--warning",
                    "The server reports a different last-known time. This tab marker may no longer show the latest location.",
                ),
            );
        }
        if (trackedPerson) {
            renderMoveForm(trackedPerson, ui.detailContent);
        }
    }

    function renderAll() {
        renderSessionList();
        renderNearbyList();
        renderDetail();
        renderPersonMarkers();
        renderNearbyMarkers();
        updateCenterVisuals();
    }

    function updateListSelection(list, personId) {
        if (!list) {
            return;
        }
        list.querySelectorAll(".is-selected").forEach((item) => {
            item.classList.remove("is-selected");
            item
                .querySelector("button")
                ?.setAttribute("aria-current", "false");
        });
        if (!personId) {
            return;
        }
        const selected = list.querySelector(
            `[data-person-id="${personId}"]`,
        );
        selected?.classList.add("is-selected");
        selected
            ?.querySelector("button")
            ?.setAttribute("aria-current", "true");
    }

    function selectPerson(personId, focusMap) {
        if (
            !state.sessionPeople.has(personId) &&
            !nearbyPersonById(personId)
        ) {
            return;
        }
        const previousPersonId = state.selectedPersonId;
        state.selectedPersonId = personId;
        updateListSelection(ui.sessionList, personId);
        updateListSelection(ui.nearbyList, personId);
        renderDetail();
        openPersonDetail(document.activeElement);
        renderPersonMarkers();
        updateNearbyMarkerSelection(previousPersonId, personId);

        const tracked = state.sessionPeople.get(personId);
        const nearby = nearbyPersonById(personId);
        const located = nearby || tracked;
        if (located && focusMap && state.map) {
            state.map.panTo([located.latitude, located.longitude]);
            if (tracked) {
                state.personMarkers.get(personId)?.getElement()?.focus();
            }
        }
    }

    function bindEvents() {
        ui.modeAdd?.addEventListener("click", () => setMode("add"));
        ui.modeCenter?.addEventListener("click", () => setMode("center"));
        ui.addAtCenter?.addEventListener("click", () =>
            openCreateDialog({ ...state.searchCenter }),
        );
        ui.refreshNearby?.addEventListener("click", () =>
            scheduleNearbySearch(true),
        );
        ui.nearbyPrevious?.addEventListener("click", () => {
            state.nearbyListOffset = Math.max(
                0,
                state.nearbyListOffset - NEARBY_LIST_PAGE_SIZE,
            );
            renderNearbyList();
            ui.nearbyList?.scrollIntoView({ block: "start" });
        });
        ui.nearbyNext?.addEventListener("click", () => {
            const lastPageOffset =
                Math.floor(
                    Math.max(0, state.nearbyPeople.length - 1) /
                        NEARBY_LIST_PAGE_SIZE,
                ) * NEARBY_LIST_PAGE_SIZE;
            state.nearbyListOffset = Math.min(
                state.nearbyListOffset + NEARBY_LIST_PAGE_SIZE,
                lastPageOffset,
            );
            renderNearbyList();
            ui.nearbyList?.scrollIntoView({ block: "start" });
        });
        ui.radiusInput?.addEventListener("input", applyRadiusInput);
        ui.radiusInput?.addEventListener("change", applyRadiusInput);
        ui.radiusRange?.addEventListener("input", applyRadiusInput);
        ui.radiusRange?.addEventListener("change", applyRadiusInput);
        ui.centerLatitude?.addEventListener("change", applyCenterInputs);
        ui.centerLongitude?.addEventListener("change", applyCenterInputs);
        ui.applyCenter?.addEventListener("click", applyCenterInputs);
        ui.addHobby?.addEventListener("click", () => addHobbyRow(""));
        ui.personName?.addEventListener("input", () => {
            ui.personName.setCustomValidity("");
            setCounter(ui.nameCount, ui.personName, PROFILE_LIMITS.name);
        });
        ui.personJob?.addEventListener("input", () => {
            ui.personJob.setCustomValidity("");
            setCounter(ui.jobCount, ui.personJob, PROFILE_LIMITS.jobTitle);
        });
        ui.createLatitude?.addEventListener("input", () => {
            ui.createLatitude.setCustomValidity("");
        });
        ui.createLongitude?.addEventListener("input", () => {
            ui.createLongitude.setCustomValidity("");
        });
        ui.createCancel?.addEventListener("click", () =>
            closeDialog(ui.createDialog),
        );
        ui.createClose?.addEventListener("click", () =>
            closeDialog(ui.createDialog),
        );
        ui.createForm?.addEventListener("submit", createPerson);
        ui.forgetSession?.addEventListener("click", forgetSessionPeople);
        ui.detailClose?.addEventListener("click", closePersonDetail);
        ui.detailDialog?.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                event.preventDefault();
                closePersonDetail();
            }
        });
        ui.nearbyList?.addEventListener("click", (event) => {
            const button = event.target.closest?.("[data-select-nearby]");
            const item = button?.closest("[data-person-id]");
            if (item?.dataset.personId) {
                selectPerson(item.dataset.personId, true);
            }
        });
    }

    function initialize() {
        collectDom();
        bindEvents();
        loadSessionPeople();
        initializeMap();

        if (ui.radiusInput) {
            ui.radiusInput.min = "0.1";
            ui.radiusInput.max = "100";
            ui.radiusInput.step = "0.1";
            ui.radiusInput.value = formatRadius(state.radiusKm);
        }
        if (ui.radiusRange) {
            ui.radiusRange.min = "0.1";
            ui.radiusRange.max = "100";
            ui.radiusRange.step = "0.1";
            ui.radiusRange.value = formatRadius(state.radiusKm);
        }
        if (
            ui.hobbiesList &&
            ui.hobbiesList.querySelectorAll(".hobby-row").length === 0
        ) {
            resetHobbyRows();
        }
        setMode("add", false);
        renderAll();
        scheduleNearbySearch(true);
    }

    if (typeof module !== "undefined" && module.exports) {
        module.exports = Object.freeze({
            isFiniteCoordinate,
            normalizeNearbyPerson,
            validNearbyLocation,
            validNearbyPerson,
        });
        return;
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initialize, { once: true });
    } else {
        initialize();
    }
})();

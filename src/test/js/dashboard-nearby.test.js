"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const {
    normalizeNearbyPerson,
    validNearbyLocation,
    validNearbyPerson,
} = require("../../main/resources/static/assets/dashboard.js");

const nearbyPerson = Object.freeze({
    id: "11111111-1111-4111-8111-111111111111",
    name: "Aroha",
    jobTitle: "Cartographer",
    hobbies: ["walking"],
    bio: "Maps the city, then walks it.",
    createdAt: "2026-07-20T00:00:00Z",
    lastKnownLocationAt: "2026-07-20T01:00:00Z",
    location: Object.freeze({
        latitude: -36.8485,
        longitude: 174.7633,
    }),
    distanceKm: 1.2,
});

test("nearby location requires finite in-range numeric coordinates", () => {
    assert.equal(validNearbyLocation(nearbyPerson.location), true);
    assert.equal(validNearbyLocation({ latitude: -90, longitude: 180 }), true);
    assert.equal(
        validNearbyLocation({ latitude: "-36.8485", longitude: 174.7633 }),
        false,
    );
    assert.equal(
        validNearbyLocation({ latitude: 91, longitude: 174.7633 }),
        false,
    );
    assert.equal(
        validNearbyLocation({ latitude: -36.8485, longitude: Number.NaN }),
        false,
    );
});

test("nearby person rejects a missing or malformed nested location", () => {
    assert.equal(validNearbyPerson(nearbyPerson), true);
    assert.equal(validNearbyPerson({ ...nearbyPerson, location: undefined }), false);
    assert.equal(
        validNearbyPerson({
            ...nearbyPerson,
            location: { latitude: -36.8485, longitude: 181 },
        }),
        false,
    );
});

test("nearby person normalization retains map coordinates", () => {
    const normalized = normalizeNearbyPerson(nearbyPerson);

    assert.equal(normalized.latitude, -36.8485);
    assert.equal(normalized.longitude, 174.7633);
    assert.equal(normalized.distanceKm, 1.2);
    assert.equal(normalized.name, "Aroha");
});

test("nearby person normalization retains a full contract-sized bio", () => {
    const bio = "A".repeat(732);
    const normalized = normalizeNearbyPerson({ ...nearbyPerson, bio });

    assert.equal(normalized.bio, bio);
});

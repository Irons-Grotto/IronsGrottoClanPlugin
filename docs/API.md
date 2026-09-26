# Plugin ↔ Backend API contract

## Versioning (read first)
- Every plugin route lives under **`/api/plugin/v1/`**. v1 is a promise to every installed
  plugin: it only changes backward-compatibly (add optional request fields, add response fields,
  add routes). Anything breaking is a new version (`/api/plugin/v2/...`) mounted beside v1.
- Every request sends **`X-Plugin-Version: <major.minor.patch>`** (plugin: `GrottoApiClient
  .PLUGIN_VERSION`). Missing/malformed → 400. Older than the server's `minimumPluginVersion`
  (`apps/web/config/plugin.ts`, currently **1.0.0**) → **426** `{ error, minimumVersion }`; the
  plugin pauses its outbox/screenshots/progress (keeps the data) and tells the member to update.
- Plugin routes are **token-only**: middleware rejects `/api/plugin/**` without a bearer token and
  never reads a session there; site routes never accept tokens. Two auth primitives, never both.
  The one exception is `/api/plugin/v1/public/**`: no auth at all, public data only, rate limited
  per address.

Backend lives in `~/irons-grotto-1/apps/web/app/api/plugin/v1/` (shared helpers in `../utils/`). Keep this file in sync with both
sides; the plugin's DTOs are in `src/main/java/com/ironsgrotto/api/model/`.

## Auth (every request)
| Header | Value |
|---|---|
| `Authorization` | `Bearer igp_…` — generated at `/plugin` on the website (Discord sign-in only; clan membership **not** required) |
| `X-Account-Hash` | RuneLite `client.getAccountHash()` as decimal text (signed 64-bit) |
| `X-Player-Name` | Local player name (NBSP → space) |
| `X-Plugin-Version` | Plugin release, `major.minor.patch` — required |

Server (`authenticatePluginRequest`): token live → per-token rate limit (120/min) → token bound
to this account (or unbound) → account link → bind an unbound token. Refused (403) if:
- the token is bound to a different account hash — code `token_account_mismatch`. **One token,
  one account**: the first account to use a token owns it (`plugin_tokens.account_hash`);
- a clan player with that RSN belongs to another Discord user, or the account hash is already
  linked to another Discord user — code `account_not_yours`.

Otherwise linked; `playerName` is null for non-members.

Envelope: `{ "success": true, "data": … }` or `{ "success": false, "error": "…", "code"?: "…" }`.
`code` is set only where the plugin acts on it: on `token_account_mismatch` or
`account_not_yours` it deletes the token it holds for that account and asks for a new one.

Getting a token: with no token for the logged-in account, the panel asks
`GET /public/registration` (below). Registered → "Get a token" opens `<Server URL>/plugin?name=<rsn>`,
which makes a token named after the account on arrival (a name in use gets a number, "EclipseGoon
2"; a reload replaces the unused one). Not registered, and `pluginOnboarding` → "Join Irons
Grotto" opens `/join`, which makes the token. Not registered without it (the site's
`IS_GROTTO_PLUGIN_ENABLED` is off, so `/join` has no plugin steps) → "Get a token" as above.

## `GET /api/plugin/v1/public/registration?rsn=<name>`
**Public**: no token, no account headers; `X-Plugin-Version` still required (400/426 as above).
30 requests/min per address (429 + `Retry-After`). Response `data`:
`{ "registered": boolean, "pluginOnboarding": boolean }`. `registered` is true when a `players` row
has that name (case-insensitive, active or not). `pluginOnboarding` is the site's
`IS_GROTTO_PLUGIN_ENABLED`: whether `/join` has the plugin steps and makes a token. Missing (older
server) reads as true.

Plugin storage: tokens live per game account in RuneLite's RS-profile config (`TokenStore`),
looked up by account hash, so a token is never sent for another account (e.g. a friend on the same
client) and syncs across machines with RuneLite profile sync.
Status: 400 bad headers/body · 401 token · 403 ownership · 426 plugin too old · 429 rate limit (`Retry-After`) · 5xx.

## `GET /api/plugin/v1/me`
```json
{ "rsn": "Iron Dude",
  "member": { "playerName", "rank", "points", "accountType", "staffRole",
              "currentRankThreshold", "nextRank", "nextRankThreshold" } | null,
  "joinUrl": "https://ironsgrotto.xyz/join" | null,
  "policy": { "minScreenshotLootValue": 1000000, "screenshotCollectionLog": true,
              "screenshotPets": true, "panelRefreshSeconds": 300 },
  "pluginOnboarding": true }
```
`pluginOnboarding` as in `/public/registration`: with it, a linked non-member's `joinUrl` is labelled
"Continue" (they're partway through the plugin steps); without, "Join Irons Grotto".
Policy source: `apps/web/config/plugin.ts`.

## `GET /api/plugin/v1/clan-events`
Same shape as `fetchClanEventStatus` (`app/data-sources/fetch-clan-event-status.ts`):
`{ active: { id, type, typeLabel, name, metricName, icon, startsAt, endsAt, participantCount,
standings: [{ position, playerName, gained }] (top 5), standingsUnavailable } | null,
next: { …summary } | null }`

## `POST /api/plugin/v1/events`
Request (≤ 50 events, all for the header account):
```json
{ "events": [ { "id": "<uuid, client-generated>", "type": "loot|collection_log_item|boss_kc|pet",
                "occurredAt": "2026-09-24T12:00:00Z", "payload": { … }, "test": false } ] }
```
`test` is optional and still accepted; the plugin no longer sends it (its developer tools were
removed).
Payloads (`apps/web/app/schemas/plugin-ledger.ts`):
- `loot`: `{ source, sourceType (NPC|PLAYER|EVENT|PICKPOCKET|UNKNOWN), combatLevel?, items:[{id,name,quantity,price}], totalValue }`
  — totalValue recomputed server-side.
- `collection_log_item`: `{ itemName, itemId? }`
- `boss_kc`: `{ boss, kc }` (boss as named in the game's kill count message)
- `pet`: `{ variant: follower|backpack|duplicate, message }`

Response `data`:
```json
{ "accepted": ["<id>", …],          // stored or already stored (idempotent on id)
  "rejected": { "<id>": "reason" },  // permanent: unknown type, bad payload, >7d old, >5min future
  "messages": ["Drop recorded: …"] } // only for newly inserted events; "[Test] " prefix for test events
```
Ids in neither list are retried. Client behaviour: 5xx/429/network → backoff (5s → 5min);
401/403 → pause until token changes; other 4xx → drop batch.

Server flags (never refusals): `kc_not_increasing` (≤ a KC this account already reported for that
boss, test events excluded), `delayed` (arrived > 1h after `occurredAt`), `test` (dev tools).

## Consumer query layer (backend only)
`lib/db/plugin-ledger-operations.ts` → `getLedgerEvents({ from, to, playerNames?, accountHashes?,
types?, itemIds?, itemNames?, sources?, bosses?, includeTest?, limit? })`. Test events excluded
unless `includeTest`. Matches on `occurred_at`.

## `POST /api/plugin/v1/events/{id}/screenshot`
Multipart, one `image` field (JPEG/PNG ≤ 2 MB). Event must be the caller's account (else 404).
Idempotent: an event with a screenshot returns it unchanged. Stores to Vercel Blob (or
`.local-uploads/`, served at `/api/dev/uploads/...`, under `DEV_LOCAL_UPLOADS`), sets `screenshot_url`, posts an embed with the image
attached to `DISCORD_DROPS_CHANNEL_ID` (skipped when unset, and for test events).
Response `data`: `{ screenshotUrl, announced }`.

## `PUT /api/plugin/v1/progress`
Any subset of (schema: `apps/web/app/schemas/plugin-progress.ts`):
```json
{ "skills": { "totalLevel", "totalXp", "skills": { "Attack": { "level", "xp" } } },
  "collectionLog": { "obtained?", "total?", "items?": [{ "id", "name", "quantity" }], "complete?" },
  "combatAchievements": { "points", "tier": "None|Easy|…|Grandmaster" },
  "diaries": { "Ardougne": "None|Easy|Medium|Hard|Elite", … },
  "quests": { "questPoints", "completed": ["Cook's Assistant", …] },
  "clues": { "Hard": 12 },
  "settings": { "collectionLogChat": true, "lootTracker": true },
  "accountType": "main|ironman|ultimate_ironman|hardcore_ironman|group_ironman|hardcore_group_ironman|unranked_group_ironman" }
```
`accountType` is the game mode from the game's ironman varbit (1777), sent with the login reading
and when it changes. Stored as the `account_type` snapshot. For a member it overwrites
`players.account_type` (not upwards-only: a hardcore dies, an ironman de-irons; a group name is kept
while the mode stays a group mode) and is marked source `plugin` under `account_type`, so the
Temple refresh leaves it alone for 30 days. On the plugin branch of `/join` it settles the game mode
without asking the member.
`settings` is not progress: the client settings tracking depends on (the game's new collection log
item chat message; RuneLite's Loot Tracker plugin). Stored as the account's `settings` snapshot for
onboarding, never applied. Partial kinds are merged into the stored snapshot: a counters-only
`collectionLog` keeps the stored item list, a single item joins it, a `complete` list replaces it;
`clues` tiers merge.
Always stored as the account's latest snapshot. Members: merged upwards-only into the ranking
record, rescored, category marked source `plugin`. Response `data`:
`{ member, applied: [categories, plus "account_type" when the mode changed], points, rank }`.
Plugin-owned categories (synced ≤ 30 days) are not overwritten by the Temple/WikiSync refresh.

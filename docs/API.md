# Plugin ↔ Backend API contract

Backend lives in `~/irons-grotto-1/apps/web/app/api/plugin/`. Keep this file in sync with both
sides; the plugin's DTOs are in `src/main/java/com/ironsgrotto/api/model/`.

## Auth (every request)
| Header | Value |
|---|---|
| `Authorization` | `Bearer igp_…` — generated at `/plugin` on the website (Discord sign-in only; clan membership **not** required) |
| `X-Account-Hash` | RuneLite `client.getAccountHash()` as decimal text (signed 64-bit) |
| `X-Player-Name` | Local player name (NBSP → space) |

Server (`authenticatePluginRequest`): token live → per-token rate limit (120/min) → account
link. Link refused (403) if a clan player with that RSN belongs to another Discord user, or the
account hash is already linked to another Discord user. Otherwise linked; `playerName` is null
for non-members.

Envelope: `{ "success": true, "data": … }` or `{ "success": false, "error": "…" }`.
Status: 400 bad headers/body · 401 token · 403 ownership · 429 rate limit (`Retry-After`) · 5xx.

## `GET /api/plugin/me`
```json
{ "rsn": "Iron Dude",
  "member": { "playerName", "rank", "points", "accountType", "staffRole",
              "currentRankThreshold", "nextRank", "nextRankThreshold" } | null,
  "joinUrl": "https://ironsgrotto.xyz/join" | null,
  "policy": { "minScreenshotLootValue": 1000000, "screenshotCollectionLog": true,
              "screenshotPets": true, "panelRefreshSeconds": 300 } }
```
Policy source: `apps/web/config/plugin.ts`.

## `GET /api/plugin/clan-events`
Same shape as `fetchClanEventStatus` (`app/data-sources/fetch-clan-event-status.ts`):
`{ active: { id, type, typeLabel, name, metricName, icon, startsAt, endsAt, participantCount,
standings: [{ position, playerName, gained }] (top 5), standingsUnavailable } | null,
next: { …summary } | null }`

## `POST /api/plugin/events`
Request (≤ 50 events, all for the header account; also send `X-Plugin-Version`):
```json
{ "events": [ { "id": "<uuid, client-generated>", "type": "loot|collection_log_item|boss_kc|pet",
                "occurredAt": "2026-09-24T12:00:00Z", "payload": { … }, "test": false } ] }
```
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

## `POST /api/plugin/events/{id}/screenshot`
Multipart, one `image` field (JPEG/PNG ≤ 2 MB). Event must be the caller's account (else 404).
Idempotent: an event with a screenshot returns it unchanged. Stores to Vercel Blob (or
`.local-uploads/` under `DEV_LOCAL_UPLOADS`), sets `screenshot_url`, posts an embed with the image
attached to `DISCORD_DROPS_CHANNEL_ID` (skipped when unset, and for test events).
Response `data`: `{ screenshotUrl, announced }`.

## `PUT /api/plugin/progress`
Any subset of (schema: `apps/web/app/schemas/plugin-progress.ts`):
```json
{ "skills": { "totalLevel", "totalXp", "skills": { "Attack": { "level", "xp" } } },
  "collectionLog": { "obtained?", "total?", "items?": [{ "id", "name", "quantity" }], "complete?" },
  "combatAchievements": { "points", "tier": "None|Easy|…|Grandmaster" },
  "diaries": { "Ardougne": "None|Easy|Medium|Hard|Elite", … },
  "quests": { "questPoints", "completed": ["Cook's Assistant", …] },
  "clues": { "Hard": 12 } }
```
Always stored as the account's latest snapshot. Members: merged upwards-only into the ranking
record, rescored, category marked source `plugin`. Response `data`:
`{ member, applied: [categories], points, rank }`.
Plugin-owned categories (synced ≤ 30 days) are not overwritten by the Temple/WikiSync refresh.

# Irons Grotto Plugin — Roadmap & Progress

> **Resume here.** This file is the source of truth across sessions. Update it at the
> end of every session and whenever a checklist item completes.

## Current status / next step
- **Milestone:** Feature complete (M1–M6 built) — **awaiting user validation** via
  [`docs/VALIDATION.md`](VALIDATION.md). Fix whatever it turns up, then push + PRs + Plugin Hub.
- **Next step:** read the user's results against docs/VALIDATION.md; fix failures; then the
  "Needs you" list there (license confirm, push/PRs, prod config, webhook secret, Plugin Hub).
  **P0 after that: M7 plugin-first onboarding** (below).
- Backend: branch `mm/plugin-foundations` in worktree
  `~/irons-grotto-1/.claude/worktrees/plugin-api`. Unpushed.
- Plugin: this repo, branch `mm/plugin-foundations`.

## Local test setup
- Postgres: Docker container `irons-grotto-pg` (volume `irons-grotto-pg`, user/pass/db `grotto`,
  port 5432), all migrations applied. `docker start irons-grotto-pg` after a reboot.
- Worktree `apps/web/.env.local` is a **copy** of the main one with `DATABASE_URL` →
  `postgres://grotto:grotto@localhost:5432/grotto` (Redis/Discord/Temple still the real ones —
  onboarding only asks Temple to track the account, no Discord posts). Certificates copied in.
- `yarn dev` in the worktree → https://localhost:3000 (self-signed). Java won't trust that cert,
  so `node scripts/dev-relay.mjs` (this repo) serves the plugin at **http://localhost:3001**.
- Worktree `.env.local` also has `DEV_LOCAL_UPLOADS=true` (screenshots on disk, served at
  `/api/dev/uploads/...`); `DISCORD_DROPS_CHANNEL_ID` unset (no Discord posts locally).
- Plugin: `./gradlew shadowJar` → `build/libs/irons-grotto-1.0.0-all.jar`, run with
  `java -ea -jar build/libs/irons-grotto-1.0.0-all.jar --developer-mode`; set
  Irons Grotto → Advanced → Server URL = `http://localhost:3001`.
- `DEV_WAIVE_JOIN_REQUIREMENTS=true` is set in the worktree `.env.local`: under `next dev` only,
  `/join` skips the total-level minimum and stores mains as ironman
  (`config/dev-overrides.ts`), so username/password test accounts can onboard. Alternative for
  Jagex accounts: `--insecure-write-credentials` via RuneLite `--configure`, launch once from the
  Jagex Launcher → `~/.runelite/credentials.properties` (delete when done).
- Local DB has production's `clan_events` + `clan_event_wins` copied in (2026-09-24), with
  `competition_key` nulled so local admin actions can't edit real Temple competitions.

## Repos
- Plugin: this repo (`~/IronsGrottoClanPlugin`), Java 11, Gradle, RuneLite plugin-hub layout.
- Backend: `~/irons-grotto-1/apps/web` (Next.js 16 App Router, Drizzle/Postgres, NextAuth Discord,
  Vercel Blob, Upstash Redis). Follow its `CLAUDE.md` (PRs need a `member-summary` block).

## Target end state (summary)
1. **Account progress** (collection log, CAs, KCs, diaries, skills, clues, quests, notable items)
   is written by the plugin into the *existing* progress tables that ranking reads
   (`player_acquired_items`, `player_achievement_diaries`, `players` columns …), with a per-value
   **source** (`plugin|temple|wikisync|manual`) and last-synced time. Plugin is the primary source;
   Temple/WikiSync become the fallback for non-RuneLite members and are phased out. Monotonic.
2. **Event ledger** — append-only, timestamped, event-agnostic record used *only* for event
   verification (`loot`, `collection_log_item`, `boss_kc`, `pet`, extensible). Client UUIDs for
   idempotency, `occurred_at` + `received_at`, optional screenshot. Bingo etc. are *consumers*
   querying by player/time/type/item/source.
3. **Identity/trust** — website-issued plugin tokens (hashed at rest, revocable) linked to RSN +
   account hash, only if the token's Discord user owns the RSN. One shared helper resolves
   "session OR plugin token" so routes currently gated by Discord OAuth (event status, standings,
   rank info) also serve the plugin. HTTPS, per-token rate limits, zod, standard worlds only,
   monotonic/time-window cross-checks.
4. **Screenshots** — auto-captured for valuable drops / clog slots / pets → backend → Blob →
   attached to ledger event → bot posts to drops channel. Thresholds are server-controlled.
5. **Plugin UX** — side panel (link state, RSN, rank, points, next-rank progress, active
   SOTW/BOTW + top 5, recent activity, sync status); in-game chat feedback; durable batched
   outbox; progress sync on clog/CA open, varp changes, login. Published to Plugin Hub.
6. **Website** — "RuneLite Plugin" area (tokens, linked accounts, progress sources); staff
   ledger view for disputes. Members see plugin effects through existing feeds, not the ledger.

## Milestones

### M1 Foundations
- [x] Backend: `plugin_tokens` + `plugin_accounts` schema & migration (`drizzle/0026_plugin_tokens.sql`)
- [x] Backend: token management UI + server actions (`/plugin` page, nav link under Accounts)
- [x] Backend: plugin auth helper (`app/api/plugin/utils/authenticate-plugin-request.ts`), rate limited, spec'd
- [x] Backend: `GET /api/plugin/me` (rank/progress for members; `member: null` + join URL for prospects; policy from `config/plugin.ts`)
- [x] Backend: `GET /api/plugin/clan-events` (active SOTW/BOTW + top 5 via `fetchClanEventStatus`)
- [x] Backend: migration applied to local Docker Postgres
- [x] Backend: onboarding (`/join`) opens with a "Connect RuneLite" step that auto-generates a
  token before the name/scan, with "Skip for now" (`app/join/components/connect-plugin.tsx`, spec'd)
- [x] Plugin: Server URL moved from hidden to a collapsed "Advanced" config section
- [ ] Backend: push branch + open PR (with `member-summary` block)
- [x] Plugin: Gradle 8.10.2 wrapper, `runelite-plugin.properties`, icons, `IronsGrottoPluginTest` launcher
- [x] Plugin: `IronsGrottoConfig`, `session/AccountSession` (world-type filter), `api/GrottoApiClient`
- [x] Plugin: `outbox/Outbox` + `OutboxStore` (`~/.runelite/irons-grotto/outbox.json`, batches of 50,
  per-account, backoff, pause on 401/403) — `OutboxTest`
- [x] Plugin: `ui/GrottoPanel` (no-token / logged-out / member rank+progress / prospect join button /
  SOTW-BOTW top 5 / pending count / refresh)
- [x] Verify end to end against local backend (token → link → panel shows rank) — user confirmed in game

### M2 Ledger
- [x] Backend: `plugin_ledger_events` (migration 0027), `POST /api/plugin/events`, per-event zod
  validation, idempotent insert, flags `kc_not_increasing`/`delayed`/`test` — smoke-tested via curl
- [x] Backend: `getLedgerEvents` query layer (excludes test events by default)
- [x] Plugin: `ChatEventTracker` (KC / clog slot / pet from chat, `ChatMessageParser` tested),
  `LootEventTracker` (NpcLootReceived + PlayerLootReceived + LootReceived for non-NPC/PLAYER types),
  `LedgerRecorder` → outbox
- [x] Plugin: chat feedback from server messages (only newly inserted events)
- [x] Plugin: panel "Recent activity"; Advanced → "Developer tools" toggle shows spawn buttons
  (KC / Drop / Clog slot / Pet) that go through the real hooks, marked `test`
- [x] Verify in game: dev-tool events land in `plugin_ledger_events` with `test` flag (all 5, 1–5s lag)
- [x] Real-kill check waived by user (dev tools use identical messages/hooks)
- [x] Kill↔loot linking (`KillLootLinker`, either order, raid chests up to 15 min later) +
  `getBossDrops({ minTotalValue, minItemValue, bosses, … })` — verified against local DB

### M3 Screenshots
- [x] Plugin: `ScreenshotService` (next frame via DrawManager, ≤1600px JPEG q0.85), disk-backed
  `ScreenshotStore` (`~/.runelite/irons-grotto/screenshots/<eventId>.jpg|.json`),
  `ScreenshotUploader` (after event delivered; 404 retried ≤24h), `ScreenshotPolicy` (server
  thresholds), config "Screenshots" toggle
- [x] Backend: `POST /api/plugin/events/[id]/screenshot` (multipart `image`, JPEG/PNG ≤2MB,
  owner-only, idempotent) → `lib/storage/screenshot-storage.ts` (Blob, or `.local-uploads/` +
  `/api/plugin/dev-uploads/...` when `DEV_LOCAL_UPLOADS`) → `screenshot_url` → Discord embed with
  attached image to `DISCORD_DROPS_CHANNEL_ID` (unset = no post; never for test events) — curl
  smoke-tested

### M4 Account progress
- [x] Plugin: `progress/` — `ProgressCollector` (skills, diaries via *_DIARY_*_COMPLETE varbits,
  CA points+tier via CA_POINTS/CA_THRESHOLD_*, quests, clog counters COLLECTION_COUNT/_MAX),
  `CollectionLogSync` (on clog open: search-toggle trick, script 4100 args → full item list),
  clue counts from chat, `ProgressSync` (tick 8 after login, every ~10 min, "Sync progress"
  button), `ProgressUploader` (latest-wins per category, dedupes unchanged, backoff)
- [x] Backend: `PUT /api/plugin/progress` → `plugin_progress_snapshots` (all accounts) +
  upwards-only merge into `players` / `player_acquired_items` / `player_achievement_diaries`,
  rescore; `player_progress_sources` — verified against local DB
- Not done: CA *task ids* (WikiSync still needed for tzhaar/blood torva/quiver notable checks);
  boss KC snapshot is derivable from the ledger (max kc per boss) so no separate sync.

### M5 Source precedence
- [x] `updatePlayerWithFullData` skips categories the plugin synced ≤30 days ago
  (`progress-source-operations.ts`); Temple clog not fetched at all when plugin-owned; Temple/
  WikiSync writes recorded as their source — verified against local DB
- [x] `/plugin` shows "Where your progress comes from" per account

### M6 Consumers & polish
- [x] Staff ledger view: `/admin` → "Plugin ledger" (search with proof/flags + rule tester)
- [x] Consumer layer for bingo: `lib/ledger/ledger-rules.ts` — JSON rules (`drop`, `boss_drop`,
  `kills`, `collection_log`, `pet`) → per-player progress + evidence. Bingo itself not built yet.
- [x] API versioning `/api/plugin/v1` + `X-Plugin-Version` (426 below minimum); plugin 1.0.0
- [x] Accomplishments synced after plugin progress (feeds handle bursts on read)
- [x] Announce-on-merge workflow + CLAUDE.md; BSD-2 LICENSE (user to confirm); README
- [ ] Plugin Hub submission (user action; see VALIDATION.md "Needs you")

### M7 Plugin-first onboarding — **P0** (user, 2026-09-25)
**End state:** `/join` is built around the plugin. The member generates a token, and from then on
the page is a live, interactive walkthrough that watches the plugin's data arrive and moves on by
itself: each step says what to do in game, shows "waiting for your plugin…", and advances the
moment the server sees it. The account is created from what the plugin read, not from a
Temple/WikiSync scan. Members who don't use RuneLite keep today's scan as the fallback (the
existing "Skip for now" path).

Walkthrough the page should drive (each step completes on server-observed data):
1. **Token made** → "Paste it into the Irons Grotto plugin settings" → done when the token is
   first used (`plugin_tokens.last_used_at` set).
2. **Log in** → done when the account links (`plugin_accounts` row; shows the RSN it saw, so
   the member confirms it's the right account; an alt can be picked if several link).
3. **Progress read** → done when the login snapshot lands (skills, diaries, CA tier, clog
   counters): show total level / CA tier / clog count as they arrive.
4. **Open your collection log** → done when the full `collection_log` snapshot lands: show the
   slot count.
5. **Settings check** → the plugin reports whether the in-game "new collection log item" chat
   notification is on and whether RuneLite's Loot Tracker is enabled; the page asks the member to
   fix either if not (both are needed for event tracking).
6. **Rank reveal / apply** → account created from the plugin's snapshots (they're already stored
   per account hash for prospects), then the existing reveal + application.

Design notes (not prescriptive):
- Needs a session-authenticated **status endpoint** for the web page (e.g. per step: token used,
  linked RSN(s), which snapshot kinds exist + headline values) that the page polls every few
  seconds. It's a site route (session auth), never a plugin route — the two auth primitives stay
  separate.
- Onboarding currently creates the player from the scan; it needs a "create from plugin
  snapshots" path. The total-level gate, account type and clan-membership checks still apply
  (account type: Temple, or the plugin reading the game-mode varbit — see open gaps).
- The plugin needs to report the two settings in step 5 (small additive v1 field).
- Must degrade gracefully: any step can be skipped to fall back to the scan.

## Open questions / decisions
- **Only notable items and pets are stored** from plugin collection logs (user, 2026-09-25) — in
  `player_acquired_items` *and* in `plugin_progress_snapshots` (no full raw list kept). Names are
  normalised to canonical (`toCanonicalPluginItemName`) and existing rows keep their names.
  Snapshots themselves stay: they carry readings sent before an account is a member.
- **Data boundary** (user, 2026-09-24): two sources of truth, backend deferential to the plugin —
  see [`DATA_BOUNDARY.md`](DATA_BOUNDARY.md). Plugin-owned categories (≤30 days): Temple/WikiSync
  may raise, never lower (SQL `greatest`). Plugin writes must not bump `players.updated_at`.
- **No manual sync** (user): plugin syncs on login, logout/client close, and standing-changing
  events (clog slot, pet); xp etc. once per session is enough. No Refresh/Sync buttons.
- **The ledger is not a member-facing store** (user, 2026-09-24). It exists for event
  arbitration only (staff pane + rule evaluator). Member-visible effects flow through existing
  feeds: recent clogs (reads `player_acquired_items`, filled by progress sync) and accomplishments
  (`syncPlayerAccomplishments` runs after each plugin progress sync). Those feeds already collapse
  first-sync bursts on read — don't add write-side dating tricks. Check existing primitives first.
- **API versioning (user, 2026-09-24):** plugin routes are `/api/plugin/v1/*`, token-only
  (middleware refuses `/api/plugin/**` without a bearer; no route takes both session and token).
  `X-Plugin-Version` required; server `minimumPluginVersion` = 1.0.0 → older gets 426 and the
  plugin pauses without losing data. Plugin is release **1.0.0**. v1 must stay backward compatible.
- **Announce-on-merge** (user, 2026-09-24): `.github/workflows/announce-merge.yaml` ported from
  irons-grotto-1, posts as "Irons Grotto Plugin Update". Needs repo secret
  `DISCORD_RELEASE_WEBHOOK`. Every PR needs a `member-summary` block (see CLAUDE.md).
- Ledger `type` is free text validated in code (no enum migration to add a type). Loot outside NPC/
  PvP (raids, clues, Barrows…) needs RuneLite's Loot Tracker plugin enabled. Clog slots need the
  in-game "new collection log item" chat notification on. KC from chat now; varp-based KC is M4.
- Dev-tool events: chat messages signed with sender `IronsGrottoDevTools`; loot posted inside
  `LootEventTracker.simulate`. Server stores them flagged `test`.
- Plugin step is **first** in onboarding (before info pull) so the plugin can later replace the
  Temple/WikiSync scan as the data source for onboarding (M4/M5).
- **Membership not required to link.** Token needs only a Discord sign-in (NextAuth still requires
  being in the clan Discord guild). `plugin_accounts.player_name` is nullable; filled when a
  `players` row with that RSN owned by the same Discord user exists. Prospects get
  `member: null` + join URL. Future: plugin data can drive onboarding instead of WikiSync.
- Account hash stored as `varchar(20)` (this Drizzle version has no bigint string mode).
- Refusals: RSN owned by another Discord user → 403; account hash already linked to another
  Discord user → 403 (staff unlink needed later).
- `/api/clan-events/status` is actually unauthenticated today; plugin got its own token-gated
  `/api/plugin/clan-events` instead of a shared session-or-token helper (add that helper only when
  a route truly needs both).
- Decided: website-issued token auth; screenshots via backend+bot; ledger ≠ account progress.
- Game IDs (varps/varbits/scripts/widgets) live in one class in the plugin.
- Separate finding: `POST /api/update-member-list` (irons-grotto-1) is unauthenticated.

## Session log
- 2026-09-25 — Added M7 (P0): plugin-first interactive onboarding. Collection log uploads stay
  full-list (server filters; ~40–85KB, ≤ every 30 min); Temple diffs client-side only for scale.
  Plugin network work moved off RuneLite's shared executor (771911a).
- 2026-09-24 — Data boundary written; fixed refresh starvation (updated_at), raise-only
  precedence (atomic), rename resilience; plugin auto-sync (login/logout/close/events), buttons
  removed. Backend fbe005b, 7c7d43e, 1b97c11; plugin cde5f22.
- 2026-09-24 — Copied Aceriwyn (user's main) from prod into local DB. In-game test on it found:
  readings sent before an account is a member were never applied (plugin skips unchanged
  categories) → fixed server-side (e405a92): unapplied snapshots applied on next request incl.
  /me. Aceriwyn now plugin-sourced for skills/clog/CA/diaries/quests; values matched prod.
- 2026-09-24 — Copied prod competitions locally; found + fixed a prod bug (all-digit RSN made
  Temple standings fail, fedc01c). Panel shows the next event under the active one.
- 2026-09-24 — Built M3–M6, versioning, announce workflow; wrote docs/VALIDATION.md. Incident:
  a careless `pkill -f cat` killed Docker Desktop (and possibly other apps); restarted Docker,
  local data intact. Never use broad `pkill -f` patterns.
- 2026-09-24 — Dev-tool events verified in ledger. Fixed loot item names on F2P worlds
  (`getMembersName`).
- 2026-09-24 — M2 built: backend ledger (11ee20c) + plugin trackers + dev tools; 16 plugin tests,
  ledger specs passing.
- 2026-09-24 — In-game test working against local backend (Server URL must be exactly
  `http://localhost:3001`). Added dev join-requirement waiver.
- 2026-09-24 — Local Postgres + dev server + relay up; smoke-tested token → link (non-member) →
  `/me` + `/clan-events`. Onboarding Connect RuneLite step added. Handed to user for in-game test.
- 2026-09-24 — Plugin M1 scaffold + panel + outbox + API client built (`./gradlew build` green,
  12 tests). Machine has only JDK 14 (fine for release 11).
- 2026-09-24 — Plan approved; roadmap created. Backend M1 built + committed (d67e150, 8 specs
  passing, lint/types clean; pre-existing unrelated type errors in `submit-rank-calculator.spec.ts`).

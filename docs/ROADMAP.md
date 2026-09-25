# Irons Grotto Plugin: Roadmap & Progress

> **Resume here.** Source of truth across sessions. Update the status block and session log at the
> end of every session. Also read: [`CLAUDE.md`](../CLAUDE.md) (repo rules),
> [`DATA_BOUNDARY.md`](DATA_BOUNDARY.md) (who owns what data), [`API.md`](API.md) (contract),
> [`VALIDATION.md`](VALIDATION.md) (user's in-game checklist).

## Status (session 2026-09-25, continued)
- **M1–M6 built**, all tests green. **Token model hardened** (below). **M6.5 done** (plugin repo
  on GitHub, `main` pushed, CI green). **M7 built**, behind `IS_GROTTO_PLUGIN_ENABLED`, not yet
  tried in game (VALIDATION 7b). Plugin branch `mm/m7-plugin-onboarding`.
  - Backend: `~/irons-grotto-1/.claude/worktrees/plugin-api`, branch `mm/plugin-foundations`,
    unpushed. Migrations 0000–0029 (0029 = `plugin_tokens.account_hash`).
  - Plugin: this repo, `main` on `Irons-Grotto/IronsGrottoClanPlugin` (private). Version stays
    1.0.0 until the first Plugin Hub release.
- **Token model (done):** one token, one game account. Server binds a token to its first account
  and refuses others (`token_account_mismatch`; someone else's account is `account_not_yours`).
  Plugin keeps tokens per account in RuneLite RS-profile config (`TokenStore`) and deletes the
  one the server refuses. Paste box is in the side panel. Why: a global token was sent for any
  account on the client, so a friend on your machine got linked to your Discord and claimed.
- **Verified in game** (Aceriwyn + test account "Irons Grotto"): linking, panel, dev-tool events →
  ledger, login progress sync, full collection log sync (814 items), SOTW/BOTW + top 5.
- **Built but not yet seen in game:** per-account tokens and the mismatch flow (VALIDATION), logout /
  client-close sync, new-log-slot sync via the last-obtained varp, the copy overhaul, dedicated
  `irons-grotto-sync` thread.

## Next steps (in order)
1. **Record onboarding for feedback** (`scripts/reset-onboarding.sh EclipseGoon` for a clean
   run), then merge plugin PR #1 and push the backend branch with a PR.
2. Needs you: `DISCORD_RELEASE_WEBHOOK` secret on the plugin repo (M6.5).
3. Spot-check the unverified items above with `build/libs/irons-grotto-dev.jar`.
4. "Needs you" list in VALIDATION.md: confirm BSD-2 license, backend PR (needs a `member-summary`
   block), prod `DISCORD_DROPS_CHANNEL_ID`, Plugin Hub submission.
5. Optional, offered to user, not requested: pairing flow instead of pasting tokens (plugin shows
   a code, member approves on the site; needs an unauthenticated pairing route); read at logout
   instead of cached reading; CA task ids (retires WikiSync); game-mode varbit (account type); flag
   in-game renames for staff; notable-only filter on the Temple clog path; strip dashes from
   internal docs.

## M6.5 Repo up and running (user 2026-09-25)
- [x] `AGENTS.md`: working knowledge (RuneLite storage/game state/threads, dev pitfalls).
- [x] CI: `.github/workflows/ci.yaml`, `./gradlew build` on PRs and main; fails a PR without a
  member-summary block.
- [x] Clan Discord updates: `announce-merge.yaml` (modelled on irons-grotto-1's) posts each merged
  PR's member summary as "Irons Grotto Plugin Update".
- [x] Pushed as `main` on `Irons-Grotto/IronsGrottoClanPlugin` (private); default branch set;
  CI green.
- [ ] **Needs you:** repo secret `DISCORD_RELEASE_WEBHOOK` (the same webhook irons-grotto-1 uses,
  or a new one): `gh secret set DISCORD_RELEASE_WEBHOOK -R Irons-Grotto/IronsGrottoClanPlugin`.
- [ ] Optional: branch protection on `main` requiring CI.

## M7 Plugin-first onboarding (P0, user 2026-09-25) — built
- **Flag:** `IS_GROTTO_PLUGIN_ENABLED` (deploy-time, `config/feature-flags.ts`, inlined via
  `next.config.ts` `env`). Off: `/join` is the name lookup only and the menu hides "RuneLite
  plugin". The plugin API and `/plugin` stay reachable by URL (testers, Hub reviewers). **Keep off
  in production until Plugin Hub approval.** On locally.
- **Built:** `components/plugin-setup.tsx` (steps, token, polling), `utils/resolve-plugin-steps.ts`
  (pure, spec'd), `GET /api/join/plugin-status` (session only; accounts seen in the last 30 min),
  `addPlayerAction` `pluginAccountHash` (ownership + name match, game total level for the gate,
  link + apply snapshots before the reveal), plugin reports `settings` on login and on change.
- **Not built from the spec:** nothing. Differences: token step also counts a plugin already
  speaking for an account (token pasted earlier); settings step has "Continue without".
- **Shape:** `/join` stays the single-page, phase-driven `JoinExperience`. One branch near the start
  (the token step): **iff** the member uses the plugin, follow the plugin phases; otherwise continue
  the existing scan flow unchanged. Both rejoin at confirm → reveal → apply. No new routes.
- **Plugin phases**, each advancing on its own when the server sees the data (page polls):
  1. Paste token → done when the token is first used (`plugin_tokens.last_used_at`).
  2. Log in → done when the account links; show the RSN seen so the member confirms (pick an alt
     if several link).
  3. Progress read → login snapshot lands; show total level, CA tier, clog count.
  4. Open collection log → full `collection_log` snapshot lands; show slot count.
  5. Settings check → plugin reports whether the "new collection log item" chat notification and
     RuneLite Loot Tracker are on; ask the member to fix either.
  6. Account created from the plugin snapshots (already stored per account hash for prospects),
     then the existing reveal + application.
- **Notes:** a session-auth status endpoint (site route, never a plugin route); a "create player
  from plugin snapshots" path keeping the total-level / account-type / membership checks; plugin
  reports the two settings (additive v1 field); any plugin phase can drop into the scan branch
  with state kept.

## Local test setup
- `open -a Docker && docker start irons-grotto-pg` (Postgres 16, user/pass/db `grotto`, port 5432,
  volume `irons-grotto-pg`; migrations 0000–0029 applied).
- `cd ~/irons-grotto-1/.claude/worktrees/plugin-api/apps/web && yarn dev` → https://localhost:3000
- `node scripts/dev-relay.mjs` (this repo) → http://localhost:3001 for the plugin (Java won't trust
  the dev cert).
- Plugin: `scripts/dev-client.sh` (stops the dev client, rebuilds `irons-grotto-dev.jar`,
  relaunches); settings → Advanced → Server URL `http://localhost:3001`. Log
  in, then paste a token in the side panel (one per account; it's checked and saved on paste).
- Worktree `.env.local` is a copy of the main one plus: `DATABASE_URL` → local, `DEV_WAIVE_JOIN_
  REQUIREMENTS=true`, `DEV_LOCAL_UPLOADS=true`, local-only `CRON_SECRET` (trigger jobs by hand,
  e.g. `/api/reconcile-points`). Redis/Discord/Temple creds are real; `DISCORD_DROPS_CHANNEL_ID`
  unset so nothing posts. Main repo `.env.local` line 2 has stray chars after the closing quote.
- Local data: production's `clan_events`/`clan_event_wins` (Temple edit keys nulled) and player
  **Aceriwyn** (user's main, ~550 rows) copied read-only from prod; plus test member "Irons Grotto".
- Scoring needs the Next runtime (standalone `tsx` scripts can't call `scoreStoredPlayer`); DB-only
  scripts work with `npx tsx --conditions=react-server --tsconfig tsconfig.json`.
- Jagex accounts in the dev client: `--insecure-write-credentials` via RuneLite `--configure`,
  launch once from the Jagex Launcher → `~/.runelite/credentials.properties` (delete after).

## What's built (by milestone)
- **M1 Foundations.** Tokens (`plugin_tokens`, sha256 only, max 10 active, onboarding rotates its
  unused ones) and `plugin_accounts` (account hash → Discord user, `player_name` nullable for
  prospects, survives in-game renames). `/plugin` token page; `/join` opens with Connect RuneLite.
  Plugin: session, API client, durable outbox, side panel.
- **M2 Ledger.** `plugin_ledger_events` (append-only, client UUIDs, flags `kc_not_increasing`/
  `delayed`/`test`). Plugin trackers: loot (NPC/PvP + Loot Tracker events), kc / clog slot / pet
  from chat, kill↔loot linking. Dev tools spawn test events through the real hooks.
- **M3 Screenshots.** Next-frame JPEG, disk queue, upload after event delivery; stored in Blob (or
  `.local-uploads` in dev); Discord embed to the drops channel (never for tests).
- **M4 Account progress.** Plugin reads skills, diaries, CA points/tier, quests, clog counters,
  full clog (search-toggle trick), clue counts, new slot via `COLLECTION_OVERVIEW_LAST_ITEM0`.
  Syncs on login (tick 8), logout, client close, new log slot, pet. No buttons. Server merges
  upwards only into the ranking tables, rescores, syncs accomplishments. Only notable items + pets
  stored, names canonicalised. Pre-membership readings applied once the account joins.
- **M5 Precedence.** Plugin-owned categories (≤30 days) can be raised by Temple/WikiSync, never
  lowered (SQL `greatest`); Temple clog not fetched while plugin-owned; plugin writes don't bump
  `players.updated_at`. `/plugin` shows each category's source.
- **M6 Consumers.** `/admin` Plugin ledger pane (search + rule tester); `lib/ledger/ledger-rules.ts`
  JSON rules (`drop`, `boss_drop`, `kills`, `collection_log`, `pet`) for bingo. API versioned
  `/api/plugin/v1`, `X-Plugin-Version` required, 426 below 1.0.0. Announce-on-merge workflow.

## Decisions (user)
- Two auth primitives, never both on a route: plugin routes (`/api/plugin/**`) take only the token;
  site routes only the session.
- Two sources of truth, backend deferential to the plugin (see DATA_BOUNDARY.md).
- The ledger is for event arbitration only, not member-facing. Members see plugin effects through
  existing feeds (recent clogs, accomplishments), which already collapse first-sync bursts on read.
- Only notable items and pets are stored from plugin logs, anywhere (no full raw list).
- No manual sync; progress once per session is enough except standing-changing events.
- Collection log uploads stay full-list (server filters); client-side diffing only matters at
  Temple's scale.
- One token per game account (server-bound on first use, stored per account in RS-profile config).
  Tokens stay hash-only on the server; a new machine without RuneLite sync gets a new token.
- Copy: terse, no em dashes, status only when actionable (see memory `copy-style`).
- Check existing primitives in irons-grotto-1 before building new mechanisms.

## Known issues / gaps
- WikiSync still needed for fire cape / blood torva / Dizana's quiver (CA task ids).
- In-game rename: Temple refresh fails for the old name until the member renames on the site;
  nothing prompts them.
- CA tier precedence is read-then-write (negligible race).
- Collection log sync toggles the log's search (same as WikiSync); Plugin Hub reviewers may object.
- `POST /api/update-member-list` in irons-grotto-1 is unauthenticated (pre-existing).
- 10 pre-existing failing test suites on `origin/main` (398 tests), unrelated.

## Session log
- 2026-09-25 (late): token UX finished. Panel asks the public registration route: new accounts
  go to Join, registered ones to Get a token (`/plugin?name=` makes it on arrival, numbered
  names). Pasting is the only action (checked, then saved). Revoked tokens are dropped. Dev
  tools removed. `scripts/dev-client.sh` (restart client safely), `scripts/reset-onboarding.sh`
  (wipe an account locally to re-onboard). Dev relay only proxies `/api/plugin/*`. User happy
  with the state; next is recording onboarding for friends' feedback, then merging PR #1.
- 2026-09-25 (cont.): token model hardened (both repos); M7 backend groundwork (status endpoint,
  client settings, snapshot merge fix: counters-only uploads were wiping a prospect's stored log);
  M6.5 CI, AGENTS.md. Researched RuneLite storage (see AGENTS.md).
- 2026-09-25: copy overhaul (both repos); plugin notable-only filter incl. snapshots; canonical
  item names (a clog sync had renamed 25 items, -694 pts); dedicated sync thread; M7 spec; dev jar
  build flag. Temple plugin studied: additive upserts with client-side diff; logout "sync" is only
  an `add_datapoint` hiscores refresh.
- 2026-09-24: M1–M6 built and partly verified in game; data boundary + four refresh fixes
  (updated_at starvation, raise-only, atomic greatest, pre-membership backlog); prod bug fixed
  (all-digit RSN broke Temple standings, fedc01c). Incident: a broad `pkill -f cat` killed Docker
  Desktop; never use broad kill patterns.

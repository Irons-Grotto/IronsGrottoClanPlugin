# Validation checklist — plugin 1.0.0 + backend (`mm/plugin-foundations`)

Everything below is built, unit-tested (plugin 35 tests; backend 395 in the touched suites) and
smoke-tested against the local database with curl. This list is what only a real client can show.
Tick items as you go; anything that fails, note what you saw and I'll pick it up from here.

## Accounts

| Account | What it is | Used for |
|---|---|---|
| **A — "Irons Grotto"** | Your username/password account, already onboarded locally as an ironman member (dev waiver) | Almost everything: panel, ledger, screenshots, progress sync, staff pane |
| **B — a Jagex account** | Logged in through the saved-session file (below). **Do not onboard it.** | The non-member path, and a real-sized collection log |

Staff access for the ledger pane (local DB only):
```sh
docker exec irons-grotto-pg psql -U grotto -c "update players set staff_role='owner' where player_name='Irons Grotto'"
```

## 0. Start the stack
- [ ] `open -a Docker` → `docker start irons-grotto-pg`
- [ ] `cd ~/irons-grotto-1/.claude/worktrees/plugin-api/apps/web && yarn dev` (https://localhost:3000)
- [ ] `cd ~/IronsGrottoClanPlugin && node scripts/dev-relay.mjs` (http://localhost:3001)
- [ ] `./gradlew shadowJar && java -ea -jar build/libs/irons-grotto-1.0.0-all.jar --developer-mode`
- [ ] Plugin settings: paste your token; **Advanced → Server URL = `http://localhost:3001`**;
      Advanced → **Developer tools** on.

Handy query (latest ledger rows):
```sh
docker exec irons-grotto-pg psql -U grotto -c "select type, coalesce(player_name, rsn) who, left(payload::text,80) payload, flags, screenshot_url is not null shot from plugin_ledger_events order by received_at desc limit 10"
```

## 1. Onboarding & linking — website
- [ ] Signed in, open https://localhost:3000/join → first screen is **Connect RuneLite** with a
      token already made; **Skip for now** goes to the name step. Reload `/join` twice more, then
      Accounts → RuneLite plugin: only **one** unused onboarding token is listed (older unused ones
      are revoked automatically).
- [ ] Accounts → **RuneLite plugin** (`/plugin`): generate, copy and revoke a token.

## 2. Panel — account A
- [ ] Log in: panel shows RSN, rank, points and a progress bar "N pts to <next rank>".
- [ ] SOTW/BOTW: the local DB has the real competitions (copied from production, edit keys
      removed). Shows the running one with its **top 5** from TempleOSRS and time left, and a small
      **"Next: Boss of the Week — Zulrah · in …"** line under it.
- [ ] No Refresh or Sync buttons; the footer says progress syncs on login and logout.
- [ ] Revoke the token on `/plugin`; within ~5 min (or on next login) the panel says "Your plugin
      token was not accepted…". Paste a fresh one → recovers without a restart.

## 3. Event ledger via developer tools — account A
- [ ] Click **Kill count**, **Drop**, **Clog slot**, **Pet**, **Kill + drop**. Each appears under
      *Recent activity* marked `[Test]`; Drop/Clog/Pet also get a `[Irons Grotto] [Test] …` chat
      line within ~5s.
- [ ] Query above: every row has `{test}` flag; the Kill + drop loot row's payload has
      `kc`, `kcBoss: Vorkath`, `kcEventId`.

## 4. A real event — account A
Real play is what the rule tester counts (test events never count). Lower the screenshot threshold
so a cheap drop qualifies: in `apps/web/config/plugin.ts` set `minScreenshotLootValue: 0`, save,
log out and back in so the plugin picks it up. **Put it back to 1_000_000 afterwards.**
- [ ] *(Needs a new slot)* get any collection log item you don't have → the slot is in
      `player_acquired_items` within seconds, without opening the log, and the panel's points refresh.
- [ ] Kill anything that drops loot (a chicken is fine). Row `loot` with no `test` flag,
      `sourceType: NPC`, GE prices, and a chat "Drop recorded: …".
- [ ] That row gets `screenshot_url` within ~10s; the URL opens in the browser and shows the kill.
- [ ] *(Optional, Discord)* set `DISCORD_DROPS_CHANNEL_ID` in the worktree `.env.local` to a
      **private test channel**, kill again → an embed with the screenshot is posted there. Unset it after.

## 5. Nothing is lost offline — account A
- [ ] Stop the relay (Ctrl-C). Click Drop, Pet → panel footer says "N events waiting to send".
- [ ] Close RuneLite, start it again (still offline) → count still there.
- [ ] Start the relay → count drains to "All activity synced"; rows arrive once each.

## 6. Account progress — account A
- [ ] ~5s after login the panel says **Progress synced HH:MM**.
- [ ] Log out → a few seconds later `player_progress_sources.synced_at` moves (only if something
      changed during the session, e.g. xp). Close the client while logged in → same.
- [ ] **Open your collection log** once, wait ~2s → another sync.
- [ ] Check the record:
      ```sh
      docker exec irons-grotto-pg psql -U grotto -c "select total_level, combat_achievement_tier, collection_log_count, collection_log_total, points, rank from players where player_name='Irons Grotto'" -c "select count(*) from player_acquired_items where player_name='Irons Grotto'" -c "select location, tier from player_achievement_diaries where player_name='Irons Grotto'" -c "select category, source, synced_at from player_progress_sources where player_name='Irons Grotto'"
      ```
      Totals match the game; item count ≈ your obtained slots; diaries match; sources say `plugin`.
- [ ] `/plugin` shows **Where your progress comes from** → RuneLite plugin for each category.
- [ ] Panel points/rank reflect the rescored record.

## 7. Non-member — account B (Jagex)
One-time: `/Applications/RuneLite.app/Contents/MacOS/RuneLite --configure` → client arguments
`--insecure-write-credentials` → launch B once from the Jagex Launcher. Then run the dev jar.
(Undo afterwards: remove the argument, delete `~/.runelite/credentials.properties`.)
- [ ] Panel: "Linked — not a clan member yet" and a **Join Irons Grotto** button.
- [ ] Dev-tool events record with `player_name` empty (RSN shown instead).
- [ ] Open the collection log → a full, real-sized log is stored but **not** applied to any player:
      ```sh
      docker exec irons-grotto-pg psql -U grotto -c "select kind, jsonb_array_length(coalesce(data->'items','[]'::jsonb)) items, captured_at from plugin_progress_snapshots order by captured_at desc"
      ```

## 8. Staff ledger pane — account A as staff (SQL above)
- [ ] `/admin` → **Plugin ledger**: search last 7 days; test rows hidden until *Test events: Show*.
      Screenshot links open.
- [ ] *Try an event rule*: `{ "kind": "drop", "sources": ["Chicken"] }` → account A, 1/1, with
      a proof link. `{ "kind": "pet" }` → nobody (only test pets exist).

## 9. Versioning
- [ ] `curl -s -H 'Authorization: Bearer x' -H 'X-Plugin-Version: 0.9.0' http://localhost:3001/api/plugin/v1/me`
      → 426 "no longer supported". (The plugin shows that message and keeps its queue.)

---

## Needs you (not things I can or should do)
1. **Confirm the license** — `LICENSE` is BSD 2-Clause © Irons Grotto (the Plugin Hub expects BSD-2).
2. **Push + PRs** — both repos are on local branch `mm/plugin-foundations`, unpushed. Backend PR
   body needs a member-summary; suggested:
   > You can now link the new Irons Grotto RuneLite plugin to your account — it keeps your rank up to date straight from the game and records drops for clan events, so no more screenshots as proof.
3. **Production config** — deploy runs migrations 0026–0028; set `DISCORD_DROPS_CHANNEL_ID` on
   the host (and leave `DEV_*` unset — they are ignored outside `next dev` regardless).
4. **Plugin repo secret** `DISCORD_RELEASE_WEBHOOK` for the "Irons Grotto Plugin Update" announcer.
5. **Plugin Hub submission** — a PR to `runelite/plugin-hub` adding `plugins/irons-grotto`
   (repository + commit). Reviewer risk: the collection log sync briefly toggles the log's own
   search (same technique as WikiSync). Fallback if refused: read pages as the member browses.

## Known gaps
- WikiSync is still needed for three notable-item checks that use combat achievement *task ids*
  (fire cape, blood torva, Dizana's quiver). The plugin syncs CA points/tier, not task ids.
- Clue counts update from the "You have completed N … Treasure Trails" message only (no full
  snapshot on login).
- SOTW/BOTW standings still come from TempleOSRS (by design, for now).

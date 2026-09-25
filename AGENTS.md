# AGENTS.md

Working knowledge for anyone (human or agent) changing this plugin. `CLAUDE.md` has the rules;
this file has the context and the things that were learned the hard way. Add to it when you learn
something a future session would otherwise rediscover.

## Orientation
- Two repos, one feature set. The plugin (this repo, Java 11) and the clan site's backend
  (`~/irons-grotto-1/apps/web`, Next.js + Drizzle/Postgres). They talk over `/api/plugin/v1`;
  [`docs/API.md`](docs/API.md) is the contract. Change both sides together.
- Status and next steps: [`docs/ROADMAP.md`](docs/ROADMAP.md). Data ownership between plugin,
  TempleOSRS and WikiSync: [`docs/DATA_BOUNDARY.md`](docs/DATA_BOUNDARY.md). The owner's in-game
  checklist: [`docs/VALIDATION.md`](docs/VALIDATION.md).
- The backend work lives in a git worktree, `~/irons-grotto-1/.claude/worktrees/plugin-api`,
  not the main checkout.

## Invariants
- **A token speaks for exactly one game account.** The server binds a token to the first account
  hash that uses it (`plugin_tokens.account_hash`) and refuses others with
  `token_account_mismatch`. The plugin stores tokens per account (`api/TokenStore`), so it never
  sends one account's token for another, for example a friend on the same client.
- **Never lose member data.** Events sit in the durable outbox until the server accepts or
  permanently rejects them. 401/403/426 pause the outbox and keep everything. Only a malformed
  request (other 4xx) is dropped.
- **Record what the game said.** No event-specific logic in the plugin. Bingo rules and the like
  live on the server (`lib/ledger/ledger-rules.ts`).
- **Two auth primitives, never both on one route.** Plugin routes take only the bearer token.
  Site routes take only the Discord session. Onboarding status for `/join` is a site route. The
  exception is `/api/plugin/v1/public/**`: no auth, public data only, rate limited per address.
- **Progress only moves up.** The server merges plugin readings with `greatest()`. Levels, clue
  counts and log slots can't go down in game, so a lower reading is stale.

## RuneLite: storage
Checked against the client jar and a real `~/.runelite/profiles2`.
- **Global config** (`@ConfigItem`, `configManager.getConfiguration(group, key)`) lives in the
  active RuneLite settings profile (`profiles2/<name>-<id>.properties`). Every game account on that
  profile shares it. It syncs across machines only if the member signs in to a RuneLite account
  **and** turns on sync for that profile.
- **Per-account config** (`setRSProfileConfiguration`, or `getConfiguration(group, rsProfileKey,
  key)` for any account) lives in the internal `$rsprofile` profile, keyed by account hash and
  world type. That profile has `sync:true`, so it follows the game account to any machine signed in
  to the same RuneLite account. RuneLite creates an account's RS profile on its first login.
  `configManager.getRSProfiles()` maps account hash to profile key. Loot Tracker and Time
  Tracking keep their per-account data here.
- Per-account values do not appear in the settings panel. Collect them in the side panel.
- Nothing forces sync. Many members don't sign in to RuneLite, so design for local-only storage.
- When inspecting someone's `profiles2`, print **key names only**. Values include secrets.

## RuneLite: game state and events
- `client.getAccountHash()` is -1 and the local player's name is null on the tick the game
  reports `LOGGED_IN`. `AccountSession.poll()` retries each tick until both exist.
- Names use non-breaking spaces. Normalise to plain spaces (`AccountSession.sanitiseName`, and
  `normaliseRsn` on the server).
- Varbits and varps arrive a few ticks after login. Progress is first read at tick 8
  (`ProgressSync.FIRST_READ_TICK`).
- Game state is gone by the time `LOGIN_SCREEN` fires. Anything sent at logout has to come from a
  reading taken earlier (`ProgressSync` caches one every ~100 ticks).
- `ClientShutdown.waitFor(future)` holds exit briefly for a final flush.
- `LootReceived` (raids, Barrows, clue caskets, other non-NPC loot) is posted **by the Loot
  Tracker plugin**, so it only arrives while that plugin is enabled. `NpcLootReceived` and
  `PlayerLootReceived` come from the core client.
- New collection log slots are read from the game's chat message, which only appears if the game
  setting "new collection log item" includes chat: varbit `OPTION_COLLECTION_NEW_ITEM` (11959),
  bit 1 = chat, bit 2 = popup.
- The last obtained log item is varp `COLLECTION_OVERVIEW_LAST_ITEM0`. It can land a tick either
  side of the chat message, so match it against the message's name before trusting it.
- The full collection log: the log only draws the open page, so `CollectionLogSync` toggles the
  log's own search (draws every obtained item), collects script 4100's args (item id, quantity),
  then closes the search with script 2240. WikiSync and TempleOSRS use the same calls. **Only on the
  member's click**: the "Grotto" button `CollectionLogButton` adds to the log header, beside
  search. WikiSync and Temple add theirs in the same row and `deleteAllChildren` on the log's
  setup script (7797), so ours is added after theirs (`@Subscribe(priority = -1)`), goes left of
  whatever is already in the row, and only ever deletes widgets when all of them are ours.
  Turning Temple off runs its cleanup, which deletes every widget on the log (ours too), so
  `CollectionLogButton` checks each tick while the log is open and puts itself back.
- Prefer `net.runelite.api.gameval.*` (`VarbitID`, `VarPlayerID`, `InterfaceID`) over the
  deprecated `Varbits`/`VarPlayer`. Game ids RuneLite doesn't name go in `progress/GameIds.java`.
- To confirm a constant or signature, read the jar Gradle resolved, don't guess:
  `unzip -o ~/.gradle/caches/.../runelite-api-<v>.jar 'net/runelite/api/gameval/VarbitID.class'
  && javap -constants ... | grep NAME`. For behaviour, `javap -c -p` on the client class
  (for example `ConfigManager`) shows which store a method reads.

## RuneLite: threads
- Never block the client thread. HTTP and JPEG encoding run on the plugin's own `SyncExecutor`,
  not RuneLite's shared scheduled executor (one thread for every plugin).
- A scheduled task that throws is never run again. Catch `RuntimeException` in periodic work.
- Swing only on the EDT. `GrottoPanel` methods hop onto it themselves.

## Plugin Hub constraints
- Java 11, no reflection, no extra runtime dependencies beyond what the client ships (OkHttp,
  Gson, Guava, Lombok at compile time).
- The version is `build.gradle` `version` and `GrottoApiClient.PLUGIN_VERSION`. Nothing has
  shipped yet, so there is nothing to bump until the first Hub release. After that, bump both for
  every release. The server's `minimumPluginVersion` retires old releases with 426.
- `build=standard`: the Hub swaps in its own `build.gradle` (client + Lombok, no tests), so main
  code can't use anything else. `scripts/hub-check.sh` runs the Hub's packager on a pushed commit.
- The Hub manifest's `warning` lists what the plugin sends (`docs/PLUGIN_HUB.md`). Sending
  something new means updating it in the same release.

## Local development
- Setup (Docker Postgres, backend dev server, dev relay, dev jar): ROADMAP "Local test setup".
- **Never rebuild the jar a running client is using.** `scripts/dev-client.sh` stops the dev
  client, rebuilds `build/libs/irons-grotto-dev.jar` and starts it again; use it rather than
  doing the steps by hand.
- Java doesn't trust the Next dev server's HTTPS certificate. `node scripts/dev-relay.mjs` serves
  it as plain HTTP on :3001. Point Advanced, Server URL at it. The relay only proxies
  `/api/plugin/*` and redirects everything else to https://localhost:3000: a page proxied
  through :3001 breaks server actions (Next refuses an Origin that isn't the forwarded host).
- Jagex accounts in the dev client: `--insecure-write-credentials` via RuneLite `--configure`,
  launch once from the Jagex Launcher, then delete `~/.runelite/credentials.properties`.
- There are no developer tools in the plugin: test with real game events on a throwaway account.
  `scripts/reset-onboarding.sh <rsn>` wipes an account from the local stack to onboard it again.
- **Never use broad kill patterns** (`pkill -f cat` once took down Docker Desktop). Kill by PID.
  `pgrep -f <jar>` (even `"java.*<jar>"`) also matches the shell that launched it, so a kill
  gets two PIDs and fails, and a wait loop never ends. Match on process name:
  `ps -axo pid=,comm=,args=`, `comm` ending in `java` (as `dev-client.sh` does).
- Backend: `yarn dev` in the worktree's `apps/web`. Migrations: `npx drizzle-kit generate --name
  <slug>` then `npx drizzle-kit migrate` (pipe `< /dev/null`, as they can prompt). Typecheck:
  `npx tsc --noEmit -p tsconfig.app.json`. Tests: `npx jest <path>`.

## CI and announcements
- `.github/workflows/ci.yaml` runs `./gradlew build` on every PR and on main, and fails a PR
  whose description has no member-summary block.
- `.github/workflows/announce-merge.yaml` posts that block to the clan Discord when the PR
  merges (secret `DISCORD_RELEASE_WEBHOOK`). Direct pushes to main are never announced.

## Copy
Member-facing text (panel, chat, site, Discord) is terse and plain: no em dashes, no filler, and
status only when the member has to act. Hide empty sections rather than printing "nothing yet".
Code comments are not copy.

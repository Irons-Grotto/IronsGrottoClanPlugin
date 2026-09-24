# CLAUDE.md

The Irons Grotto **RuneLite plugin** (Java 11, Gradle, Plugin Hub layout). It talks to the clan
website's backend in `~/irons-grotto-1/apps/web` (Next.js) over a versioned API.

**Start every session with [`docs/ROADMAP.md`](docs/ROADMAP.md)** — current status, next step,
local test setup and decisions. Update it at the end of every session and when an item completes.
The plugin ↔ backend contract is [`docs/API.md`](docs/API.md); change both sides together.

## Layout
- `src/main/java/com/ironsgrotto/`
  - `IronsGrottoPlugin` / `IronsGrottoConfig` — wiring and settings.
  - `session/` — which account is logged in; excluded world types.
  - `api/` — `GrottoApiClient` (the only network code; `API_PREFIX`, `PLUGIN_VERSION`), DTOs.
  - `outbox/` — durable, batched queue for ledger events.
  - `ledger/`, `tracker/` — event ledger: chat/loot trackers, kill↔loot linking.
  - `screenshot/` — capture, disk queue, upload.
  - `progress/` — account progress sync (skills, clog, CAs, diaries, quests, clues).
  - `ui/` — side panel. `dev/` — developer-tools event spawner.
- Game ids the plugin reads without a RuneLite name live in `progress/GameIds.java`.

## Rules
- **The API is versioned** (`/api/plugin/v1`). v1 only changes backward-compatibly; a breaking
  change is a new version on the server. Bump `PLUGIN_VERSION` (and `build.gradle` `version`) for
  every release; the server's `minimumPluginVersion` retires old releases with 426.
- **Never lose member data.** Transient failures back off and retry; a 401/403/426 pauses and
  keeps everything; only a request the server calls malformed is dropped.
- Everything the ledger records is what the game said — no event-specific logic in the plugin.
  Event rules (bingo etc.) live on the server (`lib/ledger/ledger-rules.ts`).
- Build and test: `./gradlew build`. Run with the plugin: `./gradlew shadowJar` then
  `java -ea -jar build/libs/irons-grotto-<version>-all.jar --developer-mode`.

## Shipping a change
Branch (`mm/<slug>`), commit, push, open a PR against `main`. The PR body says what changed, why,
how it was tested — and what was **not** verified.

## Announcing a change — every PR body needs a member summary

When a PR merges to `main`, `.github/workflows/announce-merge.yaml` posts a note to the clan
Discord as **Irons Grotto Plugin Update** (secret: `DISCORD_RELEASE_WEBHOOK`). It does **not**
write that note — it greps the PR body for this block and posts what it finds verbatim:

```markdown
<!-- member-summary:start -->
Your drops over 1M are now screenshotted and shared in the clan drops channel automatically.
<!-- member-summary:end -->
```

**Include it in every PR.** A PR without the block is skipped with a warning — nothing is
announced. The delimiters are HTML comments, so they don't render on GitHub.

### How to write it
Read by **clan members**, not developers. One or two sentences, plain language, describing **what
is different for them** in the plugin.

- **Say what they'll notice.** "The side panel now shows your rank progress" — not "added MeResponse DTO".
- **No jargon, no identifiers.** No class names, file names, endpoints, or PR numbers.
- **Technical work still gets announced**, framed by its effect: *"Behind-the-scenes improvements
  to keep the plugin fast and reliable."* Vague is fine; silence is not.
- **Present tense, active voice. Don't oversell.**

| Instead of | Write |
|---|---|
| Added KillLootLinker to tie boss_kc events to loot | Boss drops are now recorded together with the kill count they came from. |
| Outbox retries with exponential backoff and disk persistence | Nothing you get is lost if the clan server is briefly unreachable — it's sent as soon as it's back. |
| Bumped RuneLite dependency | Behind-the-scenes updates to keep the plugin working with the latest RuneLite. |

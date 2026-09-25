# Irons Grotto RuneLite Plugin

The Irons Grotto clan plugin.

- Your clan rank, points and progress to the next rank.
- The current SOTW or BOTW and its top 5.
- Drops, kill counts, new collection log slots and pets are recorded automatically, so clan
  events are verified without screenshots.
- Valuable drops, new log slots and pets are screenshotted for the clan drops channel.
- Your levels, collection log, combat achievements, diaries, quests and clue counts keep your rank
  up to date.

This plugin sends data about your account to the Irons Grotto server (ironsgrotto.xyz).

## Setup
1. Log in to the game.
2. Get a token at <https://ironsgrotto.xyz/plugin>. New members get one on the first step of
   <https://ironsgrotto.xyz/join>.
3. Paste it into the Irons Grotto side panel.
4. Open your collection log once.

Each account needs its own token. Tokens are saved per account, and follow the account to other
computers if you're signed in to RuneLite with profile sync on.

For log slots to be recorded as you get them, turn on the in-game chat notification for new
collection log items. Raid, clue and other non-NPC loot needs RuneLite's Loot Tracker plugin on.

## Development
- Java 11. `./gradlew build` compiles and runs the tests. See `CLAUDE.md`.
- Run the client with the plugin: `./gradlew shadowJar`, then
  `java -ea -jar build/libs/irons-grotto-<version>-all.jar --developer-mode`.
- Local backend: plugin settings, Advanced, Server URL. `node scripts/dev-relay.mjs` serves the
  HTTPS dev server over plain HTTP for it.
- Advanced, Developer tools shows buttons that spawn test events.
- Roadmap: [`docs/ROADMAP.md`](docs/ROADMAP.md). API contract: [`docs/API.md`](docs/API.md).

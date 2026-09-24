# Irons Grotto RuneLite Plugin

Clan companion for **Irons Grotto**:

- **Your clan standing** — rank, points and progress to the next rank, in the side panel.
- **SOTW / BOTW** — the running competition and its top 5.
- **Event tracking without screenshots** — drops, kill counts, new collection log slots and pets
  are recorded as they happen, so clan events (bingo and more) can be verified automatically.
- **Automatic proof** — valuable drops, new collection log slots and pets are screenshotted and
  shared in the clan drops channel.
- **Your progress, straight from the game** — levels, collection log, combat achievements,
  diaries, quests and clue counts keep your rank up to date without TempleOSRS or WikiSync.

This plugin sends data about your account to the Irons Grotto server (ironsgrotto.xyz).

## Setup
1. Sign in with Discord at <https://ironsgrotto.xyz/join> (new members — the first step makes a
   plugin token) or <https://ironsgrotto.xyz/plugin> (existing members).
2. Paste the token into the plugin's settings.
3. Log in — the side panel shows your rank, progress and the current clan event.
4. Open your collection log once so the plugin can read it.

You do not need to be a clan member yet to link the plugin. For collection log slots to be
recorded as they happen, turn on the in-game setting that announces new collection log items in
chat. Raids, clues and other non-NPC loot need RuneLite's Loot Tracker plugin enabled.

## Development
- Java 11 target; `./gradlew build` compiles and runs unit tests. See `CLAUDE.md`.
- Run the client with the plugin: run `IronsGrottoPluginTest` (src/test) from your IDE, or
  `./gradlew shadowJar && java -ea -jar build/libs/irons-grotto-*-all.jar --developer-mode`.
- To point at a local backend: plugin settings → Advanced → Server URL.
- Advanced → Developer tools shows buttons that spawn test events (never counted for events).
- Roadmap and progress: [`docs/ROADMAP.md`](docs/ROADMAP.md). API contract: [`docs/API.md`](docs/API.md).

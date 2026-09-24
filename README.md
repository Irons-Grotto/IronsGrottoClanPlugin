# Irons Grotto RuneLite Plugin

Clan companion for **Irons Grotto**: see your clan rank and progress, the running SOTW/BOTW
standings, and (in progress) automatic recording of drops, collection log slots, kill counts and
pets so clan events can be verified without posting screenshots by hand.

## Setup
1. Sign in with Discord at <https://ironsgrotto.xyz/plugin> and generate a plugin token.
2. Paste the token into the plugin's settings.
3. Log in — the side panel shows your rank, progress and the current clan event.

You do not need to be a clan member yet to link the plugin.

## Development
- Java 11 target; `./gradlew build` compiles and runs unit tests.
- Run the client with the plugin: run `IronsGrottoPluginTest` (src/test) from your IDE, or
  `./gradlew shadowJar && java -ea -jar build/libs/irons-grotto-*-all.jar --developer-mode`.
- To point at a local backend, set the hidden `apiBaseUrl` config
  (`ironsgrotto.apiBaseUrl`) via the RuneLite profile/config file.
- Roadmap and progress: [`docs/ROADMAP.md`](docs/ROADMAP.md). API contract: [`docs/API.md`](docs/API.md).

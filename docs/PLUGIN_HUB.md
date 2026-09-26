# Plugin Hub

The plugin is released through [runelite/plugin-hub](https://github.com/runelite/plugin-hub). A
release is a PR there that adds or updates one file, `plugins/irons-grotto`, pointing at a commit
of this repo. Their CI builds that commit; a maintainer reviews it and merges.

## Manifest (`plugins/irons-grotto` in the plugin-hub repo)
```
repository=https://github.com/Irons-Grotto/IronsGrottoClanPlugin.git
commit=<full 40-character sha of the release commit on main>
warning=This plugin submits your IP address, RSN, account hash, levels and XP, collection log, quests, diaries, combat achievements, clue counts, loot, kill counts, and pets to the Irons Grotto clan server, a 3rd-party server not controlled or verified by the RuneLite developers.
```
- `warning` is shown to a member before the plugin is enabled. Hub reviewers ask for it on any
  plugin that talks to a 3rd-party server
  ([example](https://github.com/runelite/plugin-hub/pull/16726#issuecomment-5822984155)). It
  lives in the manifest, not `runelite-plugin.properties` (the packager rejects unknown keys
  there). **Keep it true:** anything new the plugin sends gets added here in the same release.
- `scripts/hub-check.sh` reads the block above, so keep it as three lines in that order.

## This repo's side
- `runelite-plugin.properties`: `build=standard`. The Hub replaces `build.gradle` and
  `settings.gradle` with its own (RuneLite client + Lombok, main sources only; tests aren't
  built), so the plugin can't use a dependency the client doesn't ship.
- `icon.png` at the root, at most 48x72. `LICENSE` BSD 2-Clause. The repo must be **public**.
- The packager fails on terminally deprecated RuneLite APIs and on unknown properties
  (`support` is allowed).

## Check a commit before submitting
`scripts/hub-check.sh [commit]` builds a pushed commit with the Hub's packager (same bundle as
their CI), so a failure shows up here first. Needs Java and `gh` logged in; the first run
downloads about 1 GB into `$TMPDIR/irons-grotto-hub-check`.

## Releasing
1. Bump `build.gradle` `version` and `GrottoApiClient.PLUGIN_VERSION`, merge to `main`.
2. `scripts/hub-check.sh <sha of main>`.
3. In the plugin-hub fork: `git fetch upstream && git checkout -B irons-grotto upstream/master`,
   set `commit=` in `plugins/irons-grotto`, commit, `git push -f -u origin irons-grotto`, open a PR
   to `runelite/plugin-hub`. Keep fixes in the same PR (push a new `commit=`), never a new one.
4. The server's `minimumPluginVersion` retires old releases only after the new one is live.

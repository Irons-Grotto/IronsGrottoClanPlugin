# Data boundary: plugin ↔ backend ↔ TempleOSRS / WikiSync

Two sources of truth, with the backend **deferential to the plugin**: whatever the plugin read from
the game wins, and the older sources may only add to it.

## Who owns what

| Data | Owner | Fallback / other writers | Rule |
|---|---|---|---|
| Collection log (items, counts) | **Plugin** (full list on opening the log; single slots as they happen) | Temple — only when the plugin hasn't synced it in 30 days | Items: union, counts only rise; the plugin stores **only notable items and pets** (full list kept in the raw snapshot); names normalised to canonical. Temple's log isn't fetched at all while the plugin owns it |
| Levels / total XP | **Plugin** (login, logout) | Temple hiscores | Plugin-owned: Temple may raise, never lower |
| Combat achievement tier | **Plugin** (CA points vs the game's tier thresholds) | WikiSync | Tier only rises |
| Achievement diaries | **Plugin** (completion varbits) | WikiSync | Per-location tier only rises |
| Clue counts | **Plugin**, a tier at a time from the completion message | Temple hiscores (all tiers) | Per tier, the higher count wins, so tiers the plugin hasn't seen still update from Temple |
| Quests | **Plugin** (stored; not scored yet) | WikiSync quest list (used for a few notable-item checks) | — |
| EHB / EHP | **Temple only** | — | The plugin never writes these |
| Account type (game mode) | **Temple** (+ member's own answer) | — | The plugin doesn't send it yet |
| Fire cape / blood torva / Dizana's quiver flags | **WikiSync** (CA *task ids*) | Stored-value floors | The plugin doesn't read CA task ids yet |
| SOTW / BOTW standings | **Temple** (live, never stored) | — | — |
| Event ledger (loot, kc, clog slot, pet) | **Plugin only** | — | Append-only; only for resolving events; members never read it directly |
| Points, rank | **Derived**: `scoreStoredPlayer` over the stored record | Both paths rescore after writing | Same code path whichever source wrote |
| Accomplishments / recent clogs feeds | **Derived** from the stored record | Both paths run `syncPlayerAccomplishments` | The feeds collapse first-sync bursts when they read |

"Plugin-owned" = the plugin synced that category for this player in the last **30 days**
(`player_progress_sources`). After that the fallback takes over again without anyone intervening, so a
member who stops using RuneLite isn't frozen.

## When the plugin syncs
- **Login** (a few ticks in): levels, diaries, CA tier, collection log counts, quests.
- **Logout and closing the client**: the session's latest reading (it's re-read in memory each minute).
- **Standing-changing events**: new collection log slot (with its item id) or a pet → sent at once, and
  the panel refreshes when the server has it.
- **Opening the collection log**: the full item list.
- **Clue completed**: that tier's count.
- Nothing unchanged is re-sent. Developer-tools test events never touch progress.

## Issues found with the Temple refresh (and status)
1. **Refresh starvation — fixed (fbe005b).** The hourly job refreshes players whose
   `players.updated_at` is over 24h old. Plugin writes were bumping it, so a daily plugin user would never
   be refreshed and EHB/EHP/account type would freeze. Plugin writes no longer touch `updated_at`.
2. **Skip-entirely precedence froze partial data — fixed (fbe005b).** Clue counts arrive a tier at a
   time; skipping Temple for the whole category froze the other tiers. It also ignored progress made on
   mobile. Now: plugin-owned values can be raised by Temple/WikiSync, never lowered.
3. **Race between the refresh and a plugin sync — fixed (7c7d43e).** The raise-only merge is computed
   with `greatest()` inside the `UPDATE`, not read-then-write. CA tier is still read-then-write (tier
   order isn't a SQL comparison); a tier change landing in that millisecond window is negligible.
4. **Readings sent before an account became a member were never applied — fixed (e405a92).** Applied on
   the next plugin request, including the panel's `/me`.
5. **In-game name change — mitigated (1b97c11).** The site keys players by name; the plugin by account
   hash. Between an in-game rename and the member renaming on the site, the plugin now keeps applying to
   the old-name record instead of dropping to "not a member". **Still open:** the Temple refresh for the
   old name fails in that window (existing behaviour), and nothing prompts the member or staff to rename.
   A cheap follow-up: flag "plugin reports a different RSN" on `/plugin` and in the admin pane.

## Open gaps (not bugs, just not built)
- **CA task ids** — reading them would retire WikiSync entirely (the three notable flags + quest-based
  items). Reading them uses the game's tier enums and task structs.
- **Account type from the game** — the plugin can read the ironman-mode varbit, which is authoritative
  where Temple is ambiguous (GIM vs main). Would remove the join-flow account-type question for
  plugin users.
- **Onboarding from plugin data** — prospects' snapshots are already stored; `/join` could use them
  instead of the Temple/WikiSync scan.
- **Temple collection-log proof link** isn't set for plugin-owned logs (it pointed at Temple's page).
  The screenshot proof on ledger events replaces it for events; the calculator's proof link field is
  just empty.
- **Rank-up DMs** can now be triggered by either path (plugin in production, or the refresh). There's no
  double DM: the second path's "before" already includes the rank, and announcements are remembered —
  still worth a glance in production.

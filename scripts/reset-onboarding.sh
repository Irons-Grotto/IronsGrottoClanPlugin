#!/usr/bin/env bash
# Local development only: wipes one in-game account from the local stack so it
# can be onboarded again from scratch (e.g. to record the flow for feedback).
#
#   scripts/reset-onboarding.sh EclipseGoon
#
# Removes, for that RSN and every account hash linked to it:
# - the site: the player row and everything hung off it (what "Delete account"
#   removes), plus its rank submissions;
# - the plugin's server data: linked account, snapshots, ledger events, tokens;
# - the client: the token RuneLite saved for the account, and any of its
#   events still queued in the plugin's outbox.
#
# Only ever touches the local Docker database (container irons-grotto-pg).
# The dev client is stopped first (RuneLite rewrites its config on exit) and
# relaunched at the end with scripts/dev-client.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

rsn="${1:?usage: scripts/reset-onboarding.sh <rsn>}"
container=irons-grotto-pg
profiles="$HOME/.runelite/profiles2"
outbox="$HOME/.runelite/irons-grotto/outbox.json"

psql() {
  docker exec -i "$container" psql -U grotto -d grotto -v ON_ERROR_STOP=1 -At "$@"
}

# psql variables quote the name safely (:'rsn').
hashes=$(psql -v rsn="$rsn" <<'SQL'
select account_hash from plugin_accounts
where lower(rsn) = lower(:'rsn') or lower(player_name) = lower(:'rsn');
SQL
)
player=$(psql -v rsn="$rsn" <<'SQL'
select player_name from players where lower(player_name) = lower(:'rsn');
SQL
)

if [ -z "$hashes" ] && [ -z "$player" ]; then
  echo "Nothing to reset: no player or plugin account named $rsn."
  exit 0
fi

echo "Resetting $rsn (player: ${player:-none}; account hashes: $(echo $hashes | tr '\n' ' '))"

# 1. Stop the dev client, by process name (see dev-client.sh).
for pid in $(ps -axo pid=,comm=,args= | awk '$2 ~ /(^|\/)java$/ && index($0, "irons-grotto-dev.jar") { print $1 }'); do
  kill "$pid"
  while kill -0 "$pid" 2>/dev/null; do sleep 1; done
  echo "Stopped the dev client ($pid)."
done

# 2. The database, in one transaction.
hash_list=$(printf "'%s'," $hashes | sed 's/,$//')
[ -z "$hashes" ] && hash_list="''"
psql -v p="${player:-}" <<SQL
begin;
delete from player_acquired_items where player_name = :'p';
delete from player_achievement_diaries where player_name = :'p';
delete from player_rank_ups where player_name = :'p';
delete from player_accomplishments where player_name = :'p';
delete from player_item_overrides where player_name = :'p';
delete from player_derived_items where player_name = :'p';
delete from player_progress_sources where player_name = :'p';
delete from rank_submissions where player_name = :'p';
update plugin_accounts set player_name = null where player_name = :'p';
delete from players where player_name = :'p';
delete from plugin_ledger_events where account_hash in ($hash_list);
delete from plugin_progress_snapshots where account_hash in ($hash_list);
delete from plugin_tokens where account_hash in ($hash_list);
delete from plugin_accounts where account_hash in ($hash_list);
commit;
SQL
echo "Cleared the local database."

# 3. RuneLite: the token saved for each account (RS-profile config), and its
#    queued events. A backup of each file is kept beside it.
rsprofile="$profiles/\$rsprofile--1.properties"
for hash in $hashes; do
  if [ -f "$rsprofile" ]; then
    # rsprofile.rsprofile.<id>.accountHash=<hash>  ->  ironsgrotto.rsprofile.<id>.token
    for id in $(grep -E "^rsprofile\.rsprofile\.[^.]+\.accountHash=${hash}$" "$rsprofile" | sed -E 's/^rsprofile\.rsprofile\.([^.]+)\..*/\1/'); do
      if grep -q "^ironsgrotto\.rsprofile\.${id}\.token=" "$rsprofile"; then
        sed -i.reset-bak "/^ironsgrotto\.rsprofile\.${id}\.token=/d" "$rsprofile"
        echo "Removed the saved token for $rsn."
      fi
    done
  fi
done

if [ -f "$outbox" ] && [ -n "$hashes" ]; then
  cp "$outbox" "$outbox.reset-bak"
  HASHES="$hashes" python3 - "$outbox" <<'PY'
import json, os, sys
path = sys.argv[1]
hashes = set(os.environ["HASHES"].split())
entries = json.load(open(path))
kept = [e for e in entries if (e.get("account") or {}).get("accountHash") not in hashes]
json.dump(kept, open(path, "w"))
print(f"Dropped {len(entries) - len(kept)} queued events.")
PY
fi

# 4. Relaunch.
echo "Relaunching the dev client."
exec scripts/dev-client.sh

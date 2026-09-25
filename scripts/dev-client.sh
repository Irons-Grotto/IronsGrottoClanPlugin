#!/usr/bin/env bash
# Local development only: stops the dev client, rebuilds its jar, starts it again.
#
# The running client must be stopped before its jar is rebuilt. The client is
# found by process name (java) plus the jar in its arguments, never by text
# alone: `pgrep -f <jar>` also matches the shell that launched it.
#
#   scripts/dev-client.sh
set -euo pipefail
cd "$(dirname "$0")/.."

jar=irons-grotto-dev.jar

pids=$(ps -axo pid=,comm=,args= | awk -v jar="$jar" '$2 ~ /(^|\/)java$/ && index($0, jar) { print $1 }')
for pid in $pids; do
  kill "$pid"
  while kill -0 "$pid" 2>/dev/null; do sleep 1; done
done

./gradlew shadowJar -PclientJar="$jar" -q
exec java -ea -jar "build/libs/$jar" --developer-mode

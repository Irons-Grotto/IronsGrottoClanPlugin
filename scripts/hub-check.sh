#!/bin/sh
# Builds a commit of this repo with the Plugin Hub's own packager, the same build its CI runs
# on a submission PR. Usage: scripts/hub-check.sh [commit] (default: HEAD, which must be pushed).
# The manifest it writes is docs/PLUGIN_HUB.md's, with the commit filled in.
set -eu

repo_dir=$(cd "$(dirname "$0")/.." && pwd)
commit=$(git -C "$repo_dir" rev-parse "${1:-HEAD}")
work=${HUB_CHECK_DIR:-${TMPDIR:-/tmp}/irons-grotto-hub-check}
bundle_url=https://github.com/runelite/plugin-hub-tooling/releases/download/v4/bundle.tar.zst

if ! git -C "$repo_dir" branch -r --contains "$commit" | grep -q .; then
	echo "$commit is not pushed; the packager clones it from GitHub" >&2
	exit 1
fi

mkdir -p "$work"
cd "$work"
if [ ! -f package.jar ]; then
	curl -sL --fail -o bundle.tar.zst "$bundle_url"
	tar xf bundle.tar.zst
fi
if [ ! -d plugin-hub ]; then
	git clone -q --depth 1 https://github.com/runelite/plugin-hub.git
fi
git -C plugin-hub fetch -q --depth 1 origin master
git -C plugin-hub reset -q --hard origin/master
if [ ! -d api ]; then
	./prepare.sh
fi

# The manifest block from docs/PLUGIN_HUB.md, with this commit.
sed -n '/^repository=/,/^warning=/p' "$repo_dir/docs/PLUGIN_HUB.md" \
	| sed "s/^commit=.*/commit=$commit/" > plugin-hub/plugins/irons-grotto
git -C plugin-hub add plugins/irons-grotto
git -C plugin-hub -c user.name=hub-check -c user.email=hub-check@localhost commit -qm "irons-grotto $commit"

# The packager copies its output to a fixed /tmp/jars and fails if it is already there.
rm -f /tmp/jars/irons-grotto.jar /tmp/jars/irons-grotto.log /tmp/jars/irons-grotto.zip
# PACKAGE_IS_PR skips the upload step. The repo may be private, so let git borrow gh's login.
PACKAGE_IS_PR=true FORCE_BUILD=irons-grotto \
	GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=credential.https://github.com.helper \
	GIT_CONFIG_VALUE_0='!gh auth git-credential' \
	java -XX:+UseParallelGC -cp package.jar net.runelite.pluginhub.packager.Packager

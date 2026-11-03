#!/usr/bin/env bash
#
# Publishes the pages in this folder to the GitHub wiki of the repository.
#
#   bash wiki/publish.sh [git-url-of-the-repository]
#
# GitHub creates the wiki git repository only after the feature is enabled and the
# first page exists (Settings -> Features -> Wikis, then "Create the first page").
# Until then this script explains what is missing instead of failing silently.
#
set -euo pipefail

REPO="${1:-https://github.com/SlimefunNewHorizons/MultiverseGambling.git}"
WIKI="${REPO%.git}.wiki.git"
SRC="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"

cleanup() {
    rm -rf "$TMP"
}
trap cleanup EXIT

echo "Wiki repository: $WIKI"

if ! git clone --quiet "$WIKI" "$TMP" 2>/dev/null; then
    cat >&2 <<'MESSAGE'
Could not clone the wiki repository.

Check, in this order:
  1. The repository has the wiki enabled: Settings -> Features -> Wikis.
  2. The first page exists: open the Wiki tab and use "Create the first page".
  3. Your account has write access to the repository, and git is authenticated
     (git config --global credential.helper manager, or use a token in the URL).

Then run this script again.
MESSAGE
    exit 1
fi

cd "$TMP"
BRANCH="$(git symbolic-ref --short HEAD 2>/dev/null || echo master)"

copied=0
for page in "$SRC"/*.md; do
    name="$(basename "$page")"
    if [ "$name" = "README.md" ]; then
        continue
    fi
    cp "$page" .
    copied=$((copied + 1))
done

git add -- '*.md'
if git diff --cached --quiet; then
    echo "Nothing to publish: the wiki is already up to date ($copied pages checked)."
    exit 0
fi

git -c user.name="$(git config user.name || echo wiki)" \
    -c user.email="$(git config user.email || echo wiki@localhost)" \
    commit --quiet -m "Wiki: English and Spanish pages from wiki/ in the repository"

git push --quiet origin "$BRANCH"
echo "Published $copied pages to $BRANCH ($(git rev-parse --short HEAD))."
echo "Open any page and save it once if the sidebar does not refresh by itself."

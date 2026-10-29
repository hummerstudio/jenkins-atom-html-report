#!/usr/bin/env bash
# Run the atom the way the runner does and assert the report declaration it produces. No Jenkins.
set -euo pipefail
cd "$(dirname "$0")/.."

export BUILD_NUMBER=7 JOB_NAME="$(basename "$PWD")-job"

./build.sh >/dev/null
jar="$(cd dist/html-report && pwd)/html-report.jar"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
work="$(mktemp -d)"


fail() { echo "smoke failed: $*" >&2; exit 1; }

run() { # run <dir>
  local dir="$1"
  python3 - "$dir/input.json" "$dir" <<'JSONFIX'
import json, os, sys
path, workspace = sys.argv[1], sys.argv[2]
given = {}
if os.path.exists(path) and os.path.getsize(path):
    given = json.load(open(path))
# the runner writes its environment into input.json first, then the job's inputs on top
data = dict(os.environ)
data.update(given)
data.setdefault("atom_workspace", workspace)
json.dump(data, open(path, "w"))
JSONFIX
  ( cd "$dir" && atom_data_dir="$dir" atom_data_input=input.json atom_data_output=output.json \
      atom_workspace="$dir" "$java_bin" -cp "$jar" com.jenkinschina.atom.sdk.AtomRunner )
}

# 1. a directory with a nested entry point is declared as a report
dir="$work/ok"; mkdir -p "$dir/out/coverage/assets"
printf '<html><body>COVERAGE-REPORT</body></html>\n' >"$dir/out/coverage/index.html"
printf 'x\n' >"$dir/out/coverage/assets/a.css"
cat >"$dir/input.json" <<'JSON'
{"path": "out/coverage", "index": "index.html", "label": "Coverage", "requireIndex": "true"}
JSON
run "$dir" >"$dir/log.txt" 2>&1 || fail "publishing failed: $(cat "$dir/log.txt")"
grep -q '"status":"success"' "$dir/output.json" || fail "not a success: $(cat "$dir/output.json")"
grep -q '"type":"report"' "$dir/output.json" || fail "report output missing"
grep -q '"path":"'"$dir"'/out/coverage"' "$dir/output.json" || fail "report path is wrong"
grep -q '"target":"index.html"' "$dir/output.json" || fail "entry file missing"
grep -q '"label":"Coverage"' "$dir/output.json" || fail "label missing"
grep -q "2 files" "$dir/log.txt" || fail "file count not reported: $(cat "$dir/log.txt")"

# 2. a missing directory is named, not a stack trace
dir="$work/missing"; mkdir -p "$dir"
cat >"$dir/input.json" <<'JSON'
{"path": "out/nope", "index": "index.html", "label": "Coverage", "requireIndex": "true"}
JSON
if run "$dir" >/dev/null 2>&1; then fail "missing directory must fail"; fi
grep -q 'report directory not found: out/nope' "$dir/output.json" || fail "message is not readable"

# 3. a directory without the entry point fails only when requireIndex says so
dir="$work/noindex"; mkdir -p "$dir/out/empty"
cat >"$dir/input.json" <<'JSON'
{"path": "out/empty", "index": "index.html", "label": "Empty", "requireIndex": "false"}
JSON
run "$dir" >/dev/null 2>&1 || fail "requireIndex=false must not fail"
grep -q '"target":"index.html"' "$dir/output.json" || fail "declaration missing in lenient mode"

# 4. escaping the workspace is refused
dir="$work/escape"; mkdir -p "$dir"
cat >"$dir/input.json" <<'JSON'
{"path": "../etc", "index": "index.html", "label": "Coverage", "requireIndex": "true"}
JSON
if run "$dir" >/dev/null 2>&1; then fail "escaping the workspace must fail"; fi
grep -q 'must stay inside the workspace' "$dir/output.json" || fail "escape message missing"

echo "smoke ok: report declaration, missing dir, lenient mode, workspace escape"

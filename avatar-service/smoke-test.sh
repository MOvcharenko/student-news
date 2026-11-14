#!/usr/bin/env bash
# Smoke test for a running avatar-service: health, upload, download, delete.
#
#   ./smoke-test.sh                                   # http://localhost:8090
#   BASE_URL=http://localhost:9000 ./smoke-test.sh
#
# Stops at the first failure, says what failed, and exits non-zero.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8090}"
BASE_URL="${BASE_URL%/}"
AVATAR_ID="smoke-test-$$"
WORK_DIR="$(mktemp -d)"
uploaded=false

cleanup() {
  # If a step after the upload failed, do not leave the test avatar behind.
  if $uploaded; then
    curl -s -o /dev/null -X DELETE "$BASE_URL/avatars/$AVATAR_ID" || true
  fi
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

# request METHOD PATH [curl options...]
# Saves the response body to $WORK_DIR/body and prints "<status> <content type>".
request() {
  local method="$1" path="$2"
  shift 2
  curl -sS -X "$method" -o "$WORK_DIR/body" -w '%{http_code} %{content_type}' "$@" "$BASE_URL$path" \
    || fail "$method $path: no response from $BASE_URL. Is the service running?"
}

# check STEP EXPECTED_STATUS RESULT_OF_REQUEST
check() {
  local step="$1" expected="$2" status="${3%% *}"
  if [[ "$status" != "$expected" ]]; then
    fail "$step returned HTTP $status, expected $expected. Response body: $(head -c 500 "$WORK_DIR/body")"
  fi
  echo "ok    $step -> $status"
}

echo "Smoke-testing avatar-service at $BASE_URL"

# A 1x1 PNG, written byte by byte so the script needs nothing but bash and curl.
printf '\x89PNG\r\n\x1a\n' > "$WORK_DIR/avatar.png"
printf '\x00\x00\x00\x0dIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89' >> "$WORK_DIR/avatar.png"
printf '\x00\x00\x00\x0dIDAT\x78\xda\x63\x64\xf8\xcf\x50\x0f\x00\x03\x86\x01\x80\x5a\x34\x7d\x6b' >> "$WORK_DIR/avatar.png"
printf '\x00\x00\x00\x00IEND\xae\x42\x60\x82' >> "$WORK_DIR/avatar.png"

result=$(request GET /health)
check "GET /health" 200 "$result"

result=$(request POST "/avatars/$AVATAR_ID" -F "file=@$WORK_DIR/avatar.png;type=image/png")
check "POST /avatars/$AVATAR_ID" 201 "$result"
uploaded=true
grep -Eq "\"id\": ?\"$AVATAR_ID\"" "$WORK_DIR/body" \
  || fail "the upload response does not name id $AVATAR_ID: $(cat "$WORK_DIR/body")"

result=$(request GET "/avatars/$AVATAR_ID")
check "GET /avatars/$AVATAR_ID" 200 "$result"
[[ "${result#* }" == "image/png" ]] \
  || fail "GET /avatars/$AVATAR_ID returned Content-Type '${result#* }', expected 'image/png'"
cmp -s "$WORK_DIR/avatar.png" "$WORK_DIR/body" \
  || fail "GET /avatars/$AVATAR_ID returned different bytes from the ones uploaded"
echo "ok    downloaded image matches the upload byte for byte"

result=$(request DELETE "/avatars/$AVATAR_ID")
check "DELETE /avatars/$AVATAR_ID" 204 "$result"
uploaded=false

result=$(request GET "/avatars/$AVATAR_ID")
check "GET /avatars/$AVATAR_ID after delete" 404 "$result"

echo "PASS: avatar-service at $BASE_URL works"

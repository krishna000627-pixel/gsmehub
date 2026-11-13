#!/data/data/com.termux/files/usr/bin/bash
# One-time setup: tools, keystore, GitHub repo, repo secrets.
# Run from anywhere:  bash termux/setup.sh
set -euo pipefail
cd "$(dirname "$0")/.."

OWNER="${GH_OWNER:-krishna000627-pixel}"
REPO="${GH_REPO:-gsmehub}"
TOKEN_FILE="${GH_TOKEN_FILE:-$HOME/questphone_token}"
KS_DIR="$HOME/.gamehub"
KS_FILE="$KS_DIR/gamehub.keystore"
KS_ENV="$KS_DIR/keystore.env"
ALIAS="gamehub"

echo "==> Tools"
pkg install -y git openjdk-17 python curl libsodium clang make binutils >/dev/null 2>&1 || true
python -c "import nacl, requests" 2>/dev/null || SODIUM_INSTALL=system pip install --quiet pynacl requests

if [ ! -s "$TOKEN_FILE" ]; then
  read -r -s -p "GitHub token (repo + workflow scopes): " T; echo
  printf '%s' "$T" > "$TOKEN_FILE"; chmod 600 "$TOKEN_FILE"
fi
TOKEN="$(tr -d '[:space:]' < "$TOKEN_FILE")"

echo "==> Keystore"
mkdir -p "$KS_DIR"; chmod 700 "$KS_DIR"
if [ ! -f "$KS_FILE" ]; then
  PASS="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)"
  keytool -genkeypair -keystore "$KS_FILE" -alias "$ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=Gamehub, OU=Krishna, O=Krishna, C=IN" >/dev/null
  printf 'KEYSTORE_PASSWORD=%s\nKEY_ALIAS=%s\nKEY_PASSWORD=%s\n' "$PASS" "$ALIAS" "$PASS" > "$KS_ENV"
  chmod 600 "$KS_ENV" "$KS_FILE"
  echo "    created $KS_FILE (back this folder up, updates must use the same key)"
fi

echo "==> GitHub repo $OWNER/$REPO"
API="https://api.github.com"
AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")
CODE="$(curl -s -o /dev/null -w '%{http_code}' "${AUTH[@]}" "$API/repos/$OWNER/$REPO")"
if [ "$CODE" = "404" ]; then
  curl -sf "${AUTH[@]}" -X POST "$API/user/repos" \
    -d "{\"name\":\"$REPO\",\"private\":${GH_PRIVATE:-true},\"description\":\"Gamehub: offline HTML apps for Android\"}" >/dev/null
  echo "    created"
elif [ "$CODE" != "200" ]; then
  echo "GitHub API returned $CODE, check your token"; exit 1
fi

echo "==> Repo secrets"
set -a; . "$KS_ENV"; set +a
export OWNER REPO TOKEN KS_FILE
python - <<'PY'
import os, base64, requests
from nacl import encoding, public

o, r, t = os.environ["OWNER"], os.environ["REPO"], os.environ["TOKEN"]
h = {"Authorization": "Bearer " + t, "Accept": "application/vnd.github+json"}
base = "https://api.github.com/repos/%s/%s/actions/secrets" % (o, r)
k = requests.get(base + "/public-key", headers=h)
k.raise_for_status()
k = k.json()
box = public.SealedBox(public.PublicKey(k["key"].encode(), encoding.Base64Encoder()))
secrets = {
    "KEYSTORE_BASE64": base64.b64encode(open(os.environ["KS_FILE"], "rb").read()).decode(),
    "KEYSTORE_PASSWORD": os.environ["KEYSTORE_PASSWORD"],
    "KEY_ALIAS": os.environ["KEY_ALIAS"],
    "KEY_PASSWORD": os.environ["KEY_PASSWORD"],
}
for n, v in secrets.items():
    enc = base64.b64encode(box.encrypt(v.encode())).decode()
    x = requests.put(base + "/" + n, headers=h, json={"encrypted_value": enc, "key_id": k["key_id"]})
    x.raise_for_status()
    print("    secret " + n + " set")
PY

echo "==> Git"
git config --global --add safe.directory "$PWD" 2>/dev/null || true
[ -d .git ] || git init -q -b main
git remote get-url origin >/dev/null 2>&1 || git remote add origin "https://github.com/$OWNER/$REPO.git"
git config credential.helper store
grep -q "github.com" "$HOME/.git-credentials" 2>/dev/null || echo "https://$OWNER:$TOKEN@github.com" >> "$HOME/.git-credentials"
chmod 600 "$HOME/.git-credentials"
git config user.name "$OWNER"
git config user.email "$OWNER@users.noreply.github.com"

echo "Done. Next: bash termux/release.sh \"v1: first build\""

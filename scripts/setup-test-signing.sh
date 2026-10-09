#!/usr/bin/env bash
# Run as the repository owner on a trusted computer. Never commit the key.
set -euo pipefail
umask 077
signing_repository=RaiquiaSeishun/Khushu_Test
for dependency in gh keytool base64; do
  command -v "$dependency" >/dev/null || { echo "Install $dependency first." >&2; exit 1; }
done
# Verify owner access before creating any private key material.
gh api "repos/$signing_repository/actions/secrets/public-key" --silent
existing_signing_secret=$(gh secret list --repo "$signing_repository" --json name --jq '.[] | select(.name == "SUKUN_TEST_KEYSTORE_BASE64") | .name')
signing_path=${1:-${XDG_DATA_HOME:-$HOME/.local/share}/sukun-signing/sukun-test.p12}
[[ "$signing_path" == /* ]] || { echo 'Use an absolute path outside the checkout.' >&2; exit 1; }
checkout_root=$(git rev-parse --show-toplevel)
mkdir -p "$(dirname "$signing_path")"
signing_directory=$(cd "$(dirname "$signing_path")" && pwd -P)
case "$signing_directory/" in
  "$checkout_root/"*) echo 'Keep the signing key outside the checkout.' >&2; exit 1 ;;
esac
if [[ ! -e "$signing_path" ]]; then
  if [[ -n "$existing_signing_secret" ]]; then
    echo 'The signing secret already exists. Restore the original key backup instead of creating a replacement.' >&2
    exit 1
  fi
  keytool -genkeypair -keystore "$signing_path" -storetype PKCS12 \
    -alias androiddebugkey -storepass android -keypass android \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -dname 'CN=Sukun Test, OU=Independent Test Builds'
fi
chmod 600 "$signing_path"
keytool -list -keystore "$signing_path" -storepass android -alias androiddebugkey
if [[ -z "$existing_signing_secret" ]]; then
  base64 < "$signing_path" | tr -d '\r\n' | gh secret set SUKUN_TEST_KEYSTORE_BASE64 --repo "$signing_repository"
  echo 'Retained test key uploaded.'
else
  echo 'Signing secret is already configured; leaving it unchanged.'
fi
echo "Keep a secure backup of: $signing_path"
echo "Never replace this key for the same test app. It is separate from production signing."
echo "Start Sukun checks on main in GitHub Actions to obtain the consistently signed APK."

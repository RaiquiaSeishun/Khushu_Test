# Repeatable test APK updates

Phone downloads now require the retained `SUKUN_TEST_KEYSTORE_BASE64` repository secret. Missing credentials withhold the phone artifact; ordinary build, lint and device checks still run. Pull requests never receive this key or publish phone downloads. The private key is loaded only for signing, removed immediately afterwards, and never included in artifacts. The APK's public signer certificate is included for comparison.

The test app keeps `io.github.raiquiaseishun.sukun.debug`. CI uses version code `100000 + github.run_number`, so each new workflow run advances the test version independently of production. Rerunning the same run produces the same version code; Android permits reinstalling it with the same signer. Keep this workflow's run-number sequence and key. A downloaded artifact from an older run is not an update to a newer version. Production remains separate and uses the [production signing guide](RELEASE_SIGNING.md).

## One-time owner setup

The Codex GitHub integration can push code and inspect runs, but the secret-encryption endpoint returned HTTP 403, `Resource not accessible by integration`. No signing secret has been installed by Codex. Do this on a trusted computer as the repository owner, with GitHub CLI (`gh`), Git and JDK (`keytool`) installed. Authenticate `gh` with access to manage this repository's Actions secrets, then run from the checkout:

```sh
bash scripts/setup-test-signing.sh
```

The script verifies secret access, creates a 4096-bit RSA test key outside the checkout at `~/.local/share/sukun-signing/sukun-test.p12`, and uploads its encoding directly to the repository secret. An optional absolute path selects another secure location. Repeating it reuses the existing key rather than generating a replacement. Back up that file securely. If moving computers, restore the same file before running the script again. Do not replace an existing secret using a freshly generated key.

This debug identity uses the Android test alias/password (`androiddebugkey` / `android`). The password is conventional; the private key file and repository secret must remain private. It is not the production identity. Never paste the key or its base64 encoding into chat or commit it.

After setup, open **Actions → Sukun checks → Run workflow → main**. A successful run includes the `sukun-phone-test` ZIP. Future APKs signed with this retained key and a newer version code can be installed as updates, preserving app data; on a computer use `adb install -r`.

## Transition from the earlier APK

The old GitHub runner keys were temporary and cannot be recovered from their APKs. This new key therefore cannot update an earlier downloaded installation signed by another key. Do not uninstall an installation containing data to bypass a signature conflict. There is no implemented data export/import; preserving an old installation or arranging data migration is required before replacing it. Consistent updates begin with the first APK signed using the retained key.

## Validation

CI exercises an actual version-86 → version-87 APK update on API 30 and API 35. It checks the installed version and byte-for-byte preservation of the real settings DataStore and a private-file marker. These emulator checks use a key retained within the job; repository-secret signing additionally needs the one-time owner configuration and a successful signed-download run. No production signing or old-key recovery is claimed.

# Production signing setup

The manual release workflow is prepared. Do not run it until the official timetable requirement and physical-device checks in [the development notes](FORK_DEVELOPMENT.md) are resolved. The phone-test APK uses a debug key; it is not the production signing identity.

Create the production key on a trusted computer under the repository owner's control, outside any checkout or cloud environment snapshot:

```sh
keytool -genkeypair -keystore sukun-release.p12 -storetype PKCS12 \
  -alias sukun-release -keyalg RSA -keysize 4096 -validity 10000
```

Let keytool prompt for the password. Keep the keystore and password in separate secure backups. With PKCS12, use the same store and key password. Preserve this identity for every Sukun update; changing it prevents normal updates to installed copies.

In this repository's GitHub **Settings → Secrets and variables → Actions**, add:

| Secret | Value to enter securely |
| --- | --- |
| `SIGNING_KEY_STORE_BASE64` | Base64 encoding of the complete `sukun-release.p12` file |
| `SIGNING_KEY_ALIAS` | `sukun-release` |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_PASSWORD` | Same password for this PKCS12 key |

Base64 is encoding, not encryption. Treat the encoded keystore as secret material. Do not put either form, passwords, or secret screenshots in source, issues, build artifacts, logs, or chat. Remove any temporary encoding file after secure entry.

After the release requirements pass, increment the application version, run **Prepare signed Sukun release** on the reviewed branch, and provide the matching version tag. The workflow builds both flavors, verifies APK signatures, and prepares a draft release. Check the exact source commit/tag, corresponding source and licenses, checksums, and signer certificate against your retained key before publishing. An initial test installation followed by a newer build with the same key must preserve saved data and complete the update. Obtainium must select the intended flavor.

The workflow has not yet been executed with production signing secrets. Creating a draft is separate from approving and publishing an application release.

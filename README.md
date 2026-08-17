# Moonshop

Install games on an Android handheld from your own PC, from anywhere, with no account to
create and no third-party app.

The project comes in three parts:

| | Role |
|---|---|
| **`app/`** | The Android app (Kotlin, Jetpack Compose) that runs on the handheld. |
| **`srv/`** | **Moonshop srv**, the PC app that shares a folder of games. See [srv/README.md](srv/README.md). |
| **`worker/`** | The directory, a Cloudflare Worker deployed once and for all. See [worker/README.md](worker/README.md). |

## Download

Grab the latest [release](../../releases): the APK for the handheld, and Moonshop srv for
Windows or macOS.

## How it works

On the PC, Moonshop srv serves the chosen folder over local HTTP and opens a Cloudflare
tunnel to it — no port is opened on your router, the connection dials out from the
machine. The tunnel address changes on every start, which is why the PC publishes
"code → current address" to the directory, and the handheld finds its PC behind a
six-character code typed once.

When both devices sit on the same network, the handheld notices on its own and downloads
directly, skipping Cloudflare: throughput becomes your wifi's rather than your uplink's.

## Security

**The code does not grant access, it requests it.** An unknown handheld files a request
that you approve in front of your PC; it then receives a token of its own, revocable from
the window. A code glimpsed over your shoulder is therefore not enough to download.

**A code belongs to the machine that published it.** Every PC draws a secret on first
launch; the directory keeps only its fingerprint and refuses any write that does not
present it. Nobody can redirect a code to another server.

**Sharing is read-only**, confined to the chosen folder, and escaping that folder is
blocked. The handheld has no way to write anything to the PC.

What remains, and is worth knowing: Cloudflare decrypts in transit, so it sees files
transferred remotely; and the direct link on the local network is plain HTTP, readable by
anyone sharing that network. Both are avoided by turning the tunnel off and staying on
your own wifi.

## API keys

Moonshop ships **no keys at all**. A key written into the app can be read by anyone who
opens the file, then spent or revoked by a stranger. Everyone brings their own, requested
one at a time by the first-launch wizard and editable later under *Settings → API*.

| Service | What you lose without it | Where to get it |
|---|---|---|
| **IGDB** | Descriptions, year, genre, studio, rating | [Twitch console](https://dev.twitch.tv/console/apps/create) |
| **SteamGridDB** | Cover art, banners, logos | [SteamGridDB profile](https://www.steamgriddb.com/profile/preferences/api) |
| **Google Drive** | The Drive source, an alternative to the PC | [Google Cloud](https://console.cloud.google.com/apis/credentials) |

With none of them, the app still installs games normally: it shows filenames on plain
tiles, without artwork or descriptions.

## Installing

**On the handheld.** Moonshop is distributed as an APK, outside any store, so you need to
allow installation from whatever serves the file — a browser or file manager — which
Android offers on the first attempt. The app requests a single permission: internet access.

**On the PC.** Moonshop srv installs nothing: it is an executable you run. Windows shows a
SmartScreen warning on first launch, the app not being signed by a registered publisher —
*More info*, then *Run anyway*. A macOS build is produced on every change but has never
been tried in real conditions: treat it as experimental, and note it is neither signed nor
notarised.

On first launch a wizard asks for the PC code, then the API keys one at a time, each with
a link to where it is obtained. Every step can be skipped.

## Making it your own

The deployed directory is this repository's, and its quota is shared: a copy of the
project that keeps the address consumes it. To stand on your own, deploy yours — see
[worker/README.md](worker/README.md) — then replace `URL_BASE` in
`app/src/main/java/com/monshop/app/Annuaire.kt` and `URL_ANNUAIRE` in `srv/annuaire.py`.

Nothing else is shared: API keys belong to each user, and the secret binding a code to a
machine is drawn locally on first launch.

## Building

The APK is built on every push to `main` (see `.github/workflows/build.yml`) and comes out
as an artifact. Locally:

```bash
./gradlew assembleRelease
```

No private key lives in this repository. Without configuration, the release build is
signed with the debug key — fine for installing by hand, never for distribution, since
that key is public and would let anyone forge an update that installs over yours.

To sign with a real key, set these environment variables (or the GitHub Actions secrets of
the same name): `MOONSHOP_KEYSTORE_FILE`, `MOONSHOP_KEYSTORE_PASSWORD`,
`MOONSHOP_KEY_ALIAS`, `MOONSHOP_KEY_PASSWORD`.

Pushing a `v*` tag builds all three parts and publishes them as a release.

## Licence

MIT, see [LICENSE](LICENSE). It covers the code only: what anyone chooses to share through
Moonshop is their own business.

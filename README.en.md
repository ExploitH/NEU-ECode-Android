# NEU eCode Android

[中文](README.md)

NEU eCode Android is a Kotlin / Jetpack Compose companion client for Northeastern University e码通 workflows. It is intended for personal study, research, and convenience use.

> This public repository is a sanitized Android client source release. It does **not** include backend source code, Cloudflare Worker source code, private deployment config, private account data, session cookies, signing keys, private APK download links, raw protocol key material, or raw diagnostic logs.

## Features

- Kotlin + Jetpack Compose + Material 3
- Native encrypted login flow using remotely fetched runtime protocol config
- Optional long-term login with Android Keystore-backed encrypted preferences
- OkHttp / WebView cookie synchronization
- e码通 WebView entry and QR capture support
- Campus-card / network-balance sync support
- Home-screen widget support
- Recharge WebView wrapper with visible popup-window handling for legacy H5 payment pages
- WorkManager background refresh jobs
- Cloudflare Worker / R2 backed update checks and verification-gated APK downloads
- Mandatory in-app user agreement and disclaimer confirmation before login

## Runtime Configuration

Public builds contact the maintainer-operated helper endpoint by default:

```text
https://echelp.exploith.com
```

The client uses `ECHELP_BASE_URL` to fetch runtime protocol configuration and app-update metadata. The helper backend is private infrastructure and is not part of this repository. Cloudflare Worker code, deployment files, object-storage config, private APK links, raw protocol key material, and other private infrastructure details are intentionally excluded from the public tree.

Fetched protocol config is cached in Android Keystore-backed encrypted preferences so short helper-service outages do not immediately break installed clients.

## Project Structure

```text
app/
├── data/
│   ├── local/          # DataStore, encrypted credentials/config, cookie persistence
│   ├── remote/         # Retrofit APIs, protocol config, update checks, crypto helpers
│   └── repository/     # Auth/eCode/personal-data repositories
├── di/                 # Hilt modules
├── domain/             # Models and result types
├── ui/                 # Compose screens, navigation, theme, WebView helpers
├── widget/             # Home-screen widget
└── worker/             # WorkManager jobs
```

## Build

Prerequisites:

- JDK 17
- Android SDK with API 35
- Gradle wrapper from this repository

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

Run local unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Override the helper endpoint for local testing:

```bash
export ECHELP_BASE_URL="https://your-helper.example.com"
./gradlew :app:assembleDebug
```

## Update Flow

The public client expects a private helper service to provide:

1. runtime protocol configuration
2. latest-version metadata
3. a verification-gated APK download link

See [docs/CLIENT_UPDATE_FLOW.md](docs/CLIENT_UPDATE_FLOW.md) for the Android-side flow only. The private Worker/backend implementation, KV data, R2 config, Turnstile secrets, and protocol material are intentionally not published in this repository.

## Security and Privacy

- Long-term login credentials are stored only when the user opts in.
- Credentials and cached protocol config prefer Android Keystore-backed `EncryptedSharedPreferences`.
- Network logging is metadata-oriented and redacts sensitive headers/bodies where possible.
- App update checks and gated APK downloads are documented from the Android-client side only.
- Do **not** commit backend code, Worker code, deployment config, object-storage config, account credentials, session cookies, APK signing keys, raw diagnostic logs, APK/AAB artifacts, or private download links.

## User Agreement and Disclaimer

The app requires the user to read and accept an in-app agreement and disclaimer before login. The key boundaries are:

- This app is not an official app of Northeastern University or related campus service providers.
- Users must only use their own accounts and are responsible for account, device, and network security.
- The maintainer does not redistribute RSA keys, private keys, session tickets, cookies, raw packet captures, or other sensitive reverse-engineering material in this public repository.
- Any user-side packet capture, reverse engineering, extraction, redistribution, or request replay is the user's own responsibility.

## Status

This repository is a cleaned open-source snapshot of an actively developed personal campus utility app. Campus endpoints, pages, and policies may change at any time and should be tested only in authorized and compliant contexts.

## License

Apache License 2.0. See [LICENSE](LICENSE).

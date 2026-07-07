# Private Build Notes

This public repository contains only the Android client source.

It does **not** include:

- backend source code
- Worker / edge source code
- backend private deployment config
- raw protocol key material
- APK/AAB artifacts
- private download links

## Android build

Normal public-client build:

```bash
./gradlew :app:assembleDebug
```

Override the helper endpoint for a private environment:

```bash
export ECHELP_BASE_URL="https://your-helper.example.com"
./gradlew :app:assembleDebug
```

`ECHELP_BASE_URL` is compiled into `BuildConfig` and used by the client to fetch runtime protocol configuration and update metadata. Public builds default to `https://echelp.exploith.com`. The helper backend / Worker itself is private infrastructure and is intentionally not documented or shipped here.

## Never commit

- backend source or deployment files
- Worker source or Wrangler config
- real helper-backend config
- `*.pem`, `*.key`, `*.jks`, `*.keystore`
- `local.properties`
- APK/AAB artifacts
- private download URLs
- diagnostic logs
- account credentials or cookies

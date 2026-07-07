# Android Client Update Flow

This document describes the **public Android-client-side** update flow only. It does **not** include any private backend / Worker source code, deployment files, object-storage config, raw protocol key material, or private download links.

## Default runtime target

Public builds default to:

```text
https://echelp.exploith.com
```

`ECHELP_BASE_URL` can still be overridden at build time for a private environment.

## Startup flow

1. The app warms up runtime protocol configuration from the helper endpoint.
2. The app checks the latest Android version metadata.
3. If `latestVersionCode > currentVersionCode`, the app shows an update prompt.
4. If `minSupportedVersionCode > currentVersionCode`, the prompt becomes mandatory.

## Update verification flow

When the user taps **立即更新**:

1. The app requests an update session from the helper service.
2. The helper service returns a verification URL.
3. The app opens that URL inside an in-app WebView activity.
4. The verification page completes a Cloudflare / human-verification step.
5. The page redirects to a custom callback URI captured by the app.
6. The app exchanges the returned claim for a short-lived APK download link.

## Download + install flow

1. The app downloads the APK to app-private cache.
2. If the helper service provides a SHA-256 hash, the app verifies it locally.
3. The app exposes the downloaded APK through `FileProvider`.
4. The system package installer is launched to install the update.
5. If Android requires **Install unknown apps** permission, the user is prompted to grant it before installation continues.

## Public repository boundary

The public repository intentionally excludes:

- backend / Worker source code
- Wrangler config and deployment scripts
- object-storage credentials or bucket paths
- APK signing keys / keystores
- private download links
- raw protocol key material

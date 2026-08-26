# 应用内学生 VPN（官方 OpenVPN 3）

Engine: official [`OpenVPN/openvpn3`](https://github.com/OpenVPN/openvpn3)
(`a2b602cd086aaf9613a8f7c51b1e0824373db9f7` on 2026-08-26).

License chosen for this client: **MPL-2.0** (the core is dual-licensed
AGPL-3.0-only **or** MPL-2.0). We do **not** elect AGPL. See
`third_party/openvpn3/NOTICE`.

## What is in this repo

- Kotlin `VpnService` + profile sanitizer + CRV1 parser + UI state machine
- Fail-closed bridge `UnbundledOfficialOpenVpn3Bridge` until the official
  C++ client is compiled with NDK/SWIG into `libovpncli.so`

## What is not in this repo

- `schwabe/ics-openvpn` (GPL). Do not vendor it.
- Live student `.ovpn`, CA, tls-auth, or `auth.txt`. Debug assets live under
  `app/src/debug/assets/vpn/` (gitignored). Device import path:
  `filesDir/vpn/student.ovpn`.

## Split tunnel

Sanitizer always inserts:

```text
pull-filter ignore "redirect-gateway"
```

and drops local `redirect-gateway`. MFA CRV1 is submitted once; AUTH_FAILED
without a new challenge does not auto-retry.

## Building the official core later

Host clone (not public git): `/www/android-dev/third_party/openvpn3`.
Needs Android NDK + SWIG + CMake. Until that lands, Connect shows
「官方 OpenVPN 3 核心未编入本构建」and the installed-client button remains
as a manual fallback.

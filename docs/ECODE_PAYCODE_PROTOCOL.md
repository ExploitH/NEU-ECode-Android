# eCode 原生付款码协议（只读）

Verified: 2026-08-26 on student OpenVPN (`tun0`), CAS service
`https://ecode.neu.edu.cn/ecode/api/sso/login`.

This document lists methods, paths, non-secret fields, and response **shape**.
Do **not** store Cookie / CASTGC / ticket / password / live `qrCode` values here.

Spike (not shipped in the public APK):
`/www/neu-jwxt-schedule/spikes/probe_ecode_paycode.py`

## Auth

1. CAS password login with service `https://ecode.neu.edu.cn/ecode/api/sso/login`.
2. Successful SSO often **pauses** at `https://ecode.neu.edu.cn/ecode/api` (HTTP 404)
   or `/ecode/api/sso/login?...`. That 404 is an intermediate landing, not an
   auth failure. Forward to `https://ecode.neu.edu.cn/ecode/#/`.
3. Business cookie: `SESSION` on `ecode.neu.edu.cn` path `/ecode/api`.
   Also seen: `XSRF-TOKEN` on `ecode.neu.edu.cn` path `/`.
4. Subsequent reads use `credentials: include` / OkHttp `CookieJar`.
   Do **not** fetch `/ecode/api/qr-code` from an `AppWidgetProvider` broadcast
   process via `CookieManager.getCookie` — that jar is often empty. Use
   `PersistentCookieJar` or a same-WebView `fetch`.

This is **not** the 一号通 `{e,m,d}` RSA envelope. It is same-origin JSON:API
on `ecode.neu.edu.cn`.

## Verified pay-code endpoint

```text
GET https://ecode.neu.edu.cn/ecode/api/qr-code
Accept: application/json
Cookie: SESSION=…   (path /ecode/api)
```

Live probe 2026-08-26: HTTP **200** `application/json`.

Success shape (values redacted):

```json
{
  "data": [
    {
      "type": null,
      "attributes": {
        "qrCode": "<payload string, observed len=27>",
        "createTime": "<epoch-ms string, observed len=13>",
        "qrInvalidTime": "<epoch-ms string, observed len=13>"
      }
    }
  ]
}
```

Domain mapping for `PayCode`:

| Field | Maps to |
|---|---|
| `data[0].attributes.qrCode` | `payload` (widget encode input; **do not draw on PayCodeScreen**) |
| `data[0].attributes.qrInvalidTime` | `expiresAtEpochMs` (parse as Long) |
| `ttlSeconds` | `max(0, (expiresAt - now) / 1000)` |
| `createTime` | unused for domain, optional diagnostics |

If `qrInvalidTime <= now` → `PayCodeFailure.Expired`.
If HTTP 401/403 or login HTML → `Unauthenticated` / `NeedRelogin`.
If campus-net / VPN missing → `NeedCampusNet`.
Empty / missing `qrCode` → `ProtocolError`.

Main UI: protocol success still **must not** render a native QR. Payload is for
the home-screen widget. Protocol failure → button「打开付款码」→ visible WebView.

## Related read-only endpoints (same cookie)

All `GET`, JSON:API `data[0].attributes`:

| Path | Non-secret fields observed |
|---|---|
| `/ecode/api/user-info` | `userCode`, `userName`, `unitName`, `idType` |
| `/ecode/api/user-priority` | `ruleset.data.{id,color,name,result}` |
| `/ecode/api/ecard-customer` | `noUseDate` (date string) |

These are optional. Pay-code repository only needs `/ecode/api/qr-code`.
Balances stay on 一号通 `items_app` / `detail_app`.

## Out of scope

- Drawing ZXing/Compose QR on `PayCodeScreen`
- Embedding WebView in the widget
- Writing `qrCode` fixtures from live captures

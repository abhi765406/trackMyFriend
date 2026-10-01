# TrailLink

A tiny, two-person live location sharer for old/low-storage Android phones (built and
tested against a Vivo 1820 / Y91i, 16GB storage, Android 8.1 — and works on every phone
from 8.1 up). Designed & developed by **Abhishek Kumar**.

One person taps **Start Sharing** and gets a short code. The other person types that code
into **Track Someone** and sees a live map. That's the whole app — no accounts, no contact
syncing, no ads, no analytics, nothing running unless sharing is actively turned on.

---

## One-time setup (about 5 minutes) — do this before your trip

Two phones can't see each other directly over mobile data, so something in the middle has
to relay the location. Rather than you depending on a server I run (which I can't keep
online for you), TrailLink uses **your own free Firebase Realtime Database** — Google's
infrastructure, your data, zero cost for this kind of usage, and it only takes a few
minutes to set up once:

1. Go to **https://console.firebase.google.com/** (there's a shortcut button for this in
   the app's Settings screen) and sign in with any Google account.
2. Click **Add project** → give it any name (e.g. "traillink") → you can turn off Google
   Analytics for the project, it isn't needed → **Create project**.
3. In the left sidebar: **Build → Realtime Database → Create Database**.
   - Pick any region close to India.
   - Choose **Start in test mode** (this just means "no login required to read/write" —
     fine here, since the code itself is what keeps a location private; see the Security
     note below).
4. Once created, you'll see a database URL at the top of the page, like:
   `https://traillink-xxxxx-default-rtdb.asia-southeast1.firebasedatabase.app`
   Copy that whole URL.
5. **Do this on both phones** (yours and your brother's): open TrailLink → Settings →
   paste that URL into **Realtime Database URL** → tap the checkmark to save.

That's it — permanent, one-time, and both phones now share the same private relay.

### Locking it down a little further (optional but recommended)

Test mode rules expire automatically after 30 days, after which reads/writes will start
failing until you update the rules. To make it permanent and slightly tighter, go to
**Realtime Database → Rules** in the Firebase console and paste:

```json
{
  "rules": {
    "locations": {
      "$code": {
        ".read": true,
        ".write": true
      }
    },
    ".read": false,
    ".write": false
  }
}
```

This means: nobody can browse or list anything in the database, but anyone who already
knows one specific code can read or write *only* that one code's location — which is
exactly the two people you shared it with.

## Honest note on security

The code itself (e.g. `XK4P-7QRT`) is generated with a secure random generator — about 45
bits of entropy, meaning there's no realistic way to guess it, and it's never shown or
sent anywhere except by you, manually, to your brother. Nobody browsing the internet will
stumble onto your location. That said, this is "private via an unguessable secret," the
same model Google Maps' own "share live location" links use — it is **not** end-to-end
encrypted against Firebase/Google's own servers, since that would require a much heavier
authentication system than fits a lite app like this. For sharing your location with one
trusted family member during a trip, this is a reasonable, honest trade-off. Tap
**Stop & Clear** when you arrive — it deletes the stored location from the database
immediately, so nothing lingers after your trip.

## What's actually inside

| Feature | How it's implemented |
|---|---|
| Live location sending | Plain `android.location` API (GPS + network provider) — no Google Play Services, no Fused Location |
| Relay between phones | Firebase Realtime Database's plain REST interface, called with `HttpURLConnection` — no Firebase SDK |
| Map | Leaflet.js + OpenStreetMap tiles, bundled locally (~190KB), shown in a plain WebView |
| Keeps sharing while screen is off | A foreground service with a low-priority "Sharing location" notification (required by Android so background location access isn't silently abused by apps) |
| Resilience on patchy bus-route signal | Failed sends are simply retried on the next tick; the Track screen always shows exactly how old the last known position is, never a silent failure |
| Auto-stop safeguard | Sharing automatically stops after 12 hours in case you forget |
| Battery control | Choose 10s / 15s / 30s update frequency |

## Building the APK via GitHub Actions

1. Create a new empty GitHub repository.
2. Unzip this project and push it (keep `.github/workflows/build-apk.yml` at that exact
   path):
   ```bash
   cd TrailLink
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/<your-repo>.git
   git push -u origin main
   ```
3. Open the repo's **Actions** tab — the build starts automatically and finishes in a few
   minutes.
4. Download the `traillink-release-apk` artifact — it's debug-signed automatically, so it
   installs directly on both phones, no extra signing step.

## Installing on both phones

1. **Settings → Security → Unknown sources** (or allow it per-app the first time you open
   the file).
2. Install the APK on **both** your phone and your brother's phone.
3. Paste the same Firebase database URL into Settings on **both** phones (one-time).
4. On your phone: **Share My Location → Start Sharing** → grant location permission,
   including "Allow all the time" when asked (needed so sharing keeps working with the
   screen off during the ride) → send your brother the code shown (the in-app **Share
   code via…** button works with WhatsApp, SMS, anything).
5. On your brother's phone: **Track Someone** → paste the code → **Track**.

## Project layout

```
TrailLink/
├── app/src/main/java/com/traillink/app/
│   ├── TrailLinkApp.kt            # app init, theme
│   ├── data/LocationPoint.kt      # the JSON shape sent/received
│   ├── net/FirebaseRestClient.kt  # plain HttpURLConnection PUT/GET/DELETE
│   ├── service/LocationShareService.kt  # foreground service, GPS + relay
│   ├── ui/                        # MainActivity, ShareActivity, TrackActivity, SettingsActivity
│   └── util/                      # Prefs, CodeGenerator
├── app/src/main/assets/
│   ├── map.html                   # the Leaflet map page
│   └── leaflet/                   # bundled Leaflet.js + CSS + marker icons
└── .github/workflows/build-apk.yml
```

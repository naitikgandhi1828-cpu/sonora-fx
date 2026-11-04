# Sonora FX — source rebuild

Rebuilding Sonora FX from scratch as a real Kotlin/Compose project, so it owns
its source again and can take proper features like one-tap Shizuku setup.

## How it builds

Push to a GitHub repo with Actions enabled. `.github/workflows/build.yml`
compiles `:app:assembleRelease` on GitHub's runners (which have Maven access)
and pushes the result to the **`builds`** branch:

- success → `SonoraFX.apk`
- failure → `build-log.txt` (the compiler errors, so they can be fixed)

Nothing here compiles in the Claude sandbox — the Maven repositories are
blocked there — so GitHub Actions is the build.

## Installs ALONGSIDE your current FX

During the rebuild the app ships as `com.sonora.fx.dev`, labelled
**"Sonora FX (dev)"**. It installs next to your working Sonora FX and does not
touch it. When the rebuild reaches parity we drop `.dev` in
`app/build.gradle.kts` and it takes over `com.sonora.fx`, installing over the
original with the same signing key.

## Staged plan

- **Stage 1 (this):** project scaffold + build pipeline, the equalizer data
  model and settings, **separate EQ per device**, and a friendly **Catch Every
  App** screen with one-tap **Shizuku** grant and a short manual fallback.
  Goal: it compiles, installs, and the catch-all setup is painless.
- **Stage 2:** port the audio engine — the background service that attaches
  effects to other apps' sessions, and the EQ/preamp/tone/limiter application.
- **Stage 3:** reverb and spatial, then the full Compose UI (home, EQ screen
  with sliders and the live curve, reverb screen).
- **Stage 4:** flip `.dev` off, verify it installs over the original and keeps
  its settings, tag a release.

## Layout

```
app/src/main/java/com/sonora/fx/
  FxApplication.kt          app entry; starts per-device EQ
  MainActivity.kt           Compose host + Shizuku listeners
  settings/Eq.kt            EQ band + preset model
  settings/FxSettings.kt    SharedPreferences store (same keys as original FX)
  perdevice/PerDeviceEq.kt  separate EQ per output device
  shizuku/ShizukuGrant.kt   one-tap DUMP grant via Shizuku
  ui/CatchAllScreen.kt      Catch Every App setup screen
  ui/Theme.kt
```

The signing key in `keystore/` is the same one the original FX used, kept in
the repo on purpose so builds install over each other.

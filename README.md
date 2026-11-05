# Sonora FX — source rebuild

Rebuilding Sonora FX from scratch as a real Kotlin/Compose project, so it owns
its source again. It is one self-contained app: nothing else to install.

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
  App** that works with no setup (effects on the phone's whole output).
  Goal: it compiles, installs, and the catch-all setup is painless.
  No PC, no helper apps, no special permission.
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
  MainActivity.kt           Compose host + navigation
  settings/Eq.kt            EQ band + preset model
  settings/FxSettings.kt    SharedPreferences store (same keys as original FX)
  perdevice/PerDeviceEq.kt  separate EQ per output device
  engine/FxEngine.kt        audio engine: tracks sessions, applies settings
  engine/SessionFx.kt       DynamicsProcessing chain per session (EQ, preamp, limiter)
  engine/EqCurve.kt         parametric bands + tone shelves -> 32-band graphic EQ gains
  engine/SessionScanner.kt  Catch Every App: finds sessions via the DUMP permission
  engine/FxService.kt       foreground service + session-broadcast receiver
  engine/BootReceiver.kt    restarts the engine after reboot/update
  diag/Log.kt               in-app log (Activity log screen; no PC/logcat needed)
  ui/Theme.kt               Sonora palette (always dark, orange accent)
  ui/Components.kt          cards, rows, sliders, vertical EQ faders
  ui/HomeScreen.kt          power card, Apps / Effects / More apps / How it works
  ui/EqualiserScreen.kt     curve, 10 faders, Tone, Pre-amp, auto pre-amp, limiter
  ui/CatchAllScreen.kt      Catch every app (whole-phone mode, no setup)
  ui/KeepRunningScreen.kt   battery saver / autostart help
  ui/LogScreen.kt           view, share and copy the log
```

The signing key in `keystore/` is the same one the original FX used, kept in
the repo on purpose so builds install over each other.

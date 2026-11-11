![Likhtar Home — Your TV. Your home.](docs/readme-hero-en.webp)

# Likhtar Home

A calm home screen for Android TV and Google TV. Your apps and films close at hand, without the noise.

[Explore features ↓](#make-yourself-at-home) · [How to install ↓](#install) · [Українська](README.uk.md) · [Website](https://likhtar.syvyj.dev/)

Android TV / Google TV · Android 7.0+ · GPL-3.0

## Make yourself at home

<table><tr><td width="50%"><img src="docs/screenshots/1920x1080/06-likhtaryk.png" alt="Likhtaryk — what to watch tonight?"></td><td width="50%"><strong>Likhtaryk — what to watch tonight?</strong><br><br>Choose a format and mood to get film and series picks, skipping what you have watched. You can optionally connect your own AI.</td></tr></table>

<table><tr><td width="50%"><strong>Cinema and your streaming services</strong><br><br>Explore new releases, genres and streaming catalogues; open the title in its own app.</td><td width="50%"><img src="docs/screenshots/1920x1080/02-cinema-hero.png" alt="Cinema and your streaming services"></td></tr></table>

<table><tr><td width="50%"><img src="docs/screenshots/1920x1080/08-title.png" alt="Films and your libraries"></td><td width="50%"><strong>Films and your libraries</strong><br><br>Bring your Jellyfin and Plex libraries and synced Continue watching; DLNA and SMB are supported too.</td></tr></table>

<table><tr><td width="50%"><strong>Live TV from your playlist</strong><br><br>Add M3U and XMLTV to see channels, groups and the programme guide on your TV.</td><td width="50%"><img src="docs/screenshots/1920x1080/09-live-tv.png" alt="Live TV from your playlist"></td></tr></table>

<table><tr><td width="50%"><img src="docs/screenshots/1920x1080/16-smart-home.png" alt="Smart home from the sofa"></td><td width="50%"><strong>Smart home from the sofa</strong><br><br>Control your Home Assistant rooms and devices with the remote or your own voice assistant.</td></tr></table>

<table><tr><td width="50%"><strong>Your phone does the typing</strong><br><br>Scan a QR code to arrange sections, enter passwords and addresses, or send files and links.</td><td width="50%"><img src="docs/screenshots/1920x1080/15-from-phone-qr.png" alt="Your phone does the typing"></td></tr></table>

<table><tr><td width="50%"><img src="docs/screenshots/1920x1080/18-games.png" alt="Games and apps"></td><td width="50%"><strong>Games and apps</strong><br><br>Cloud gaming services and your own ROMs through RetroArch; group, hide, pin or lock apps. Games are off by default.</td></tr></table>

[All features and screenshots →](docs/features.md)

## Install

### From your phone (easiest)

1. On the TV: *Settings → System → About*, press OK on *Android TV OS build* seven times. Then in
   *Developer options* turn on *USB debugging* (on some TVs it's called *Network debugging*).
2. On an Android phone on the same Wi-Fi, install [**Likhtar Installer**](https://github.com/Syvyj/likhtar-home/releases/latest/download/likhtar-installer.apk).
   Press *Find the TV*, then *Install*, and allow debugging when the TV asks.
3. Likhtar opens on the TV and offers to become your home screen. That's it.

TVs that only allow wireless debugging with pairing are not supported by the installer yet; use the
classic way.

### Or the classic way

1. Download [**likhtar-home.apk**](https://github.com/Syvyj/likhtar-home/releases/latest/download/likhtar-home.apk) and install it with the *Downloader* app, from
   a USB stick, or with `adb install likhtar-home.apk`.
2. Open Likhtar Home and follow the prompt to make it your home screen. Google TV tries hard to
   keep its own launcher, so Likhtar walks you through a one-time setup on the TV itself — no
   computer needed.

| You want to… | Do this |
|---|---|
| Just try it | Install it and open it like any app. Nothing on the TV changes. |
| Make it your home screen | Press *Make Likhtar your home screen* inside the app. One confirmation on the TV, no computer. |
| Go back to stock | *Settings → Remote and startup → Bring back the stock home screen.* One button. |

Requires Android TV or Google TV 7.0 or newer; the UI is laid out for 1080p and scales to 720p and
4K. The devices we have tested are [listed below](#tested-on). How a TV lets another app become the
home screen differs between manufacturers, so the switch can behave differently on yours.

## Safe to try, easy to undo

Becoming the home screen on Google TV is the scary part, so here is exactly what happens.

- **Nothing is uninstalled.** Likhtar sets itself as the home activity and *disables* — not removes —
  the stock Google TV launcher and its setup wizard. Nothing else is touched.
- **It talks to the TV, not to the internet.** The switch uses the TV's own debugging connection
  (`127.0.0.1`), so no computer and no cable are needed. You approve it once on screen.
- **Your Wi-Fi is checked first.** If a saved network belongs to an app that would be disabled, Likhtar
  refuses to continue — disabling it could forget your Wi-Fi.
- **One button back.** *Settings → Remote and startup → Bring back the stock home screen* re-enables
  everything in the reverse order.
- **A crash safety net.** If Likhtar crashes three times within five minutes, it tries to give the
  home screen back to the stock launcher by itself. It covers the usual crash loop, not every
  possible failure, so the button above is still the way back.

> ⚠️ **Before uninstalling Likhtar, press *Bring back the stock home screen* first.** Android gives an
> app no chance to undo its changes while it is being removed.

### Uninstalled it and got a black screen?

Nothing is broken: the stock home screen is only switched off, so the TV has no home screen to show.
Switch it back on in any of these ways:

<details>
<summary><b>Show the three ways back</b></summary>


1. **Without a computer.** Open the TV settings — say *"Open settings"* to the Assistant (the
   microphone button), or press the ⚙ Settings button if your remote has one. Go to *Apps → See all
   apps → Show system apps*, open **Google TV Home** and **Google TV Setup** (names vary a little
   between brands) and press **Enable** on each. Then press HOME or restart the TV.
2. **From a computer** on the same network — USB debugging is still on:
   ```bash
   adb connect <TV address>:5555
   adb shell pm enable com.google.android.tungsten.setupwraith
   adb shell pm enable com.google.android.apps.tv.launcherx
   ```
   Accept the debugging prompt on the TV if it appears.
3. **Or put Likhtar back** (`adb install likhtar-home.apk`), open it, press *Bring back the stock home
   screen*, and only then uninstall it.

</details>

## Privacy, in plain words

- Your history, lists, profiles, PINs and settings stay on the TV.
- Keys, tokens, passwords and playlist addresses are stored encrypted.
- Film data comes from TMDB through a small read-only Likhtar gateway, so there is no API key to
  paste. Like any website it sees your IP address, the titles and searches being looked up — but
  not your history, your lists, your settings or anything you ask the AI. Normal requests are not logged.
- Full details: [docs/privacy-policy.md](docs/privacy-policy.md).

## AI, on your terms (optional)

Connect an AI model and just say what you're in the mood for: *"something like Interstellar, but
lighter"*, *"a cosy series for a rainy weekend"*, *"what to watch with a seven-year-old"*. Likhtaryk
answers with a pick, a sentence on why, and a row of alternatives.

**An AI film advisor that works in any country, needs no Google account, and runs on the model you
choose** — in your language, on any TV or box with Android TV, not just the latest TVs in a few markets.

- **Your key, your model.** Use your own API key for **Anthropic Claude, OpenAI, Google Gemini or
  OpenRouter** — or point Likhtar at a model running on your own computer (**Ollama, LM Studio**),
  free and fully private.
- **Typed on your phone**, scanned in by QR, stored on the TV encrypted with a key from the Android
  Keystore that never leaves the device.
- **Straight to the provider.** Your request goes directly to the AI provider you choose. Likhtar's
  server doesn't receive it.
- **We check every title.** Each suggestion is matched against TMDB by title and year before it is
  shown, so a film the model made up doesn't reach the screen.
- **Season recaps.** Before season 3, ask for a short retelling of season 2, written from the
  episode summaries — spoilers stop where you are.

Without an AI connected, Likhtaryk still works: format and mood picks come from curated cards,
and search runs on TMDB.

## Questions people ask

**Is it free?** Yes, and open source under GPL-3.0. No subscriptions, no paid tier.

**Do I need a server or a computer?** No. The public build works on its own; film data comes through a
small read-only gateway. A computer is only needed if you want to sideload with `adb`.

**Does it play films itself?** No. Likhtar shows what is available and where, then opens the official
app — Netflix, Disney+, YouTube, your provider's player. Live TV plays the playlist *you* add.

**Do I need an AI subscription?** No. Mood picks, search and all sections work without AI. Connecting
your own model only adds free-form requests and season recaps — and a local model costs nothing.

**Will Google TV updates break it?** A system update can switch the stock launcher back on. At the
next boot Likhtar quietly takes the home screen back — as long as USB debugging is still on. Your
settings are never touched.

**Do I have to leave USB debugging on?** Only if you want that automatic fix after updates. Turn it
off and Likhtar stays your home screen; after an update you would press *Make it the home screen*
again (Settings → Remote and startup).

**How do I play my own games?** See [docs/games.md](docs/games.md). Likhtar does not include or download any games.

**Which languages?** The interface follows your TV: English, Ukrainian or Polish.

## Tested on

| Device | Android | Status |
|---|---|---|
| Skyworth / RockTek Google TV box (4K) | 14 | Daily use since August 2026 |
| Android Studio Google TV emulator | 14 | Every release |

Tried it on another TV or box? Open an issue with the model and what worked — the list grows with you.

## For developers

Bug reports, device reports, fixes and translations are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md)
and [Issues](https://github.com/Syvyj/likhtar-home/issues).

### Building from source

Requirements: Android Studio (latest stable), JDK 17.

```bash
./gradlew assemblePlayDebug        # public build, debug
./gradlew assemblePlayRelease      # public build, release (needs your own key, see below)
./gradlew testPlayDebugUnitTest lintPlayRelease
```

Optional, in a local `.env` (never committed):

```properties
TMDB_key=your-tmdb-api-key            # use TMDB directly instead of the gateway
LIKHTAR_KEYSTORE=/path/to/release.jks # required for release builds: your own key
LIKHTAR_KEYSTORE_PASSWORD=…
LIKHTAR_KEY_ALIAS=…
LIKHTAR_KEY_PASSWORD=…
```

The project is split into `app`, `core/*` (design system, data, catalogue, TV, smart home,
wallpapers) and `feature/*` (one module per screen).

## The story behind Likhtar

Likhtar arrives while everyone is talking about freeing their TV — but it didn't start there, and it
didn't start as a launcher. It grew one annoyance at a time, on a real TV in a real living room.

| When | What happened |
|---|---|
| **Jan 2026** | *Studios Master*: a small plugin for the Lampa media app that gathered TMDB collections from big studios and streamers into its sidebar. Metadata only, about 400 lines of JavaScript. |
| **Mar 2026** | *Likhtar* appears — a custom home page for that app with a hero banner, title logos and badges, focused on Ukrainian-language content. |
| **Aug 5, 2026** | *Likhtaryk*, the film-picking assistant, is born inside the plugin — first running on a local model on a home PC. |
| **Aug 11, 2026** | The plugin hits the limits of someone else's app. First commit of a native Android TV launcher in Kotlin and Compose. |
| **Aug 12, 2026** | Live TV with a real channel grid and TV guide, and the Cinema section on a TMDB catalogue. |
| **Sep 2026** | Reality section, title pages with episodes and franchises, the remote-first focus system rebuilt from scratch. Likhtaryk becomes a page of its own. |
| **Oct 20, 2026** | A public build that needs no home server of its own, and settings from your phone by QR. |
| **Oct 23, 2026** | Bring your own AI, encrypted secrets, and this README. |
| **Oct 29, 2026** | Arrange the TV from your phone, and a phone app that installs Likhtar over Wi-Fi. |

Where it began is still public: [the original plugin on GitHub](https://github.com/Syvyj/studios_4lampa).
This repository starts fresh with the launcher's first public release.

## Support

Likhtar is free and will stay free. If it made your evenings a bit easier, you can chip in:
[Ko-fi](https://ko-fi.com/syvyj) · [Buy Me a Coffee](https://buymeacoffee.com/syvyj) ·
[Monobank jar](https://send.monobank.ua/jar/5juJPj142b). The same links are on the
[website](https://likhtar.syvyj.dev/#support) and, as a QR code, in *Settings → About → Support the author*.

## Credits

- Film and TV data and images: [TMDB](https://www.themoviedb.org). This product uses the TMDB API
  but is not endorsed or certified by TMDB. Where-to-watch data: JustWatch, via TMDB.
- Weather: [Open-Meteo](https://open-meteo.com) (CC BY 4.0).
- Game box art and screenshots: [libretro-thumbnails](https://github.com/libretro-thumbnails). Boosteroid,
  Xbox, GeForce NOW and PlayStation names and app icons belong to their owners and only link to their apps.
- The on-device ADB client that makes Likhtar your home screen is ported from
  [Free My TV](https://github.com/simbasjungles/free-my-tv) (MIT).
- Fonts: Manrope, JetBrains Mono, Oswald (SIL OFL 1.1).
- Every third-party component and its license is listed in the app under *Settings → About* and in
  [NOTICE.md](NOTICE.md).

## License

Copyright © 2026 Syvyj. Likhtar Home is free software under the [GNU General Public License v3.0](LICENSE).

The name "Likhtar Home" and the lantern logo are not covered by the GPL. You are welcome to fork the code, but please give your fork its own name and icon, so people can tell it apart from the original.

Made in Ukraine, for living rooms everywhere. Listed in the [CleanYourTV Showcase](https://github.com/Gathin23/cleanyourtv/blob/main/SHOWCASE.md).

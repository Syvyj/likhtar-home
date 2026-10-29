# Contributing to Likhtar Home

Thanks for wanting to help. Likhtar is built by one person in the evenings, so small, focused
changes are the easiest to take in.

## Bugs and ideas

Open an [issue](https://github.com/Syvyj/likhtar-home/issues). For a bug, please include:

- the TV or box model and its Android version;
- the Likhtar version (*Settings → About*);
- what you pressed on the remote and what happened instead.

**Tried it on another TV or box?** An issue with the model and what worked, or didn't, is just as
useful. That is how the [Tested on](README.md#tested-on) list grows.

## Pull requests

Welcome, especially for:

- bug fixes and focus or D‑pad problems;
- making Likhtar work on more TVs and boxes;
- translations.

For a new feature or a bigger change, please open an issue first so we can agree on the shape
before you spend the time.

Out of scope: playing or hosting streams, sources of pirated content, ads, and tracking of any
kind.

## Building and checking a change

Requirements: Android Studio (latest stable), JDK 17. See [Building from source](README.md#building-from-source).

1. In Android Studio, *Device Manager → Create device → Television*, pick a Google TV image
   (API 34 or newer) and start it.
2. Install the public debug build: `./gradlew installPlayDebug`.
3. Go through your change with the remote only: arrows, OK, Back and a long press on OK. Focus
   should land where you expect and come back to the same place after Back.
4. Run the checks: `./gradlew testPlayDebugUnitTest lintPlayRelease`.

Code is Kotlin with Jetpack Compose. Match the style of the code around your change; keep comments
short and about *why*.

## Translations

The interface follows the TV's language. Each module keeps its strings in
`src/main/res/values/strings.xml`; a translation goes into `values-<language>` next to it (for
example `values-de`). To list them all:

```bash
find . -path '*/src/main/res/values/strings.xml' -not -path '*/build/*'
```

You don't have to translate everything at once: missing strings fall back to English.

## License

By contributing, you agree that your work is released under the
[GNU General Public License v3.0](LICENSE), like the rest of the project.

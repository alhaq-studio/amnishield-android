# AmniShield — Android App

[![Google Play Testing](https://img.shields.io/badge/Google%20Play-Testing%20%26%20Pre--registration-4285F4?logo=googleplay&logoColor=white)](https://play.google.com/apps/testing/com.alhaq.deenshield)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20On--Device%20%7C%20Zero%20Telemetry-008080.svg)](PRIVACY_POLICY.md)
[![Discord](https://img.shields.io/badge/Community-Discord-5865F2?logo=discord&logoColor=white)](https://discord.gg/zXz7pGVJY)
[![Telegram](https://img.shields.io/badge/Community-Telegram-26A5E4?logo=telegram&logoColor=white)](https://t.me/amnishield)

AmniShield is a 100% free and open-source, privacy-first digital wellness and distraction blocker for Android. Engineered with Kotlin and Jetpack Compose, it empowers users to reclaim focus by intercepting addictive short-form video reels, scheduled app blocking, keyword filtering, and strict anti-bypass modes without transmitting any screen data or telemetry to remote servers.

---

## Download & Testing

- **Google Play Pre-Registration & Open Testing**: [Join the Beta on Google Play](https://play.google.com/apps/testing/com.alhaq.deenshield) | [Play Store Listing](https://play.google.com/store/apps/details?id=com.alhaq.deenshield)
- **Direct APK Downloads**: Available on [GitHub Releases](https://github.com/alhaq-studio/amnishield-android/releases) (`universal` for standard Google Play Services devices, `fdroid` for de-Googled ROMs).
- **Bug Reports & Issues**: Found a bug or want to suggest an improvement? Submit an issue using our [GitHub Issue Tracker](https://github.com/alhaq-studio/amnishield-android/issues/new/choose).

---

## Core Features

- **Short Video & Reel Blocker**: Surface-level interception of infinite-scroll feeds (YouTube Shorts, Instagram Reels, TikTok).
- **Flexible App & URL Blocking**: Block selected apps, categories, or specific domains with custom schedules and launch limits.
- **Keyword Filtering**: Real-time accessibility text scanning that clears distracting or inappropriate search terms.
- **Zero-Knowledge Privacy**: 100% on-device operation. No user tracking, no third-party analytics SDKs, no cloud logging.
- **Offline Cryptographic Licensing**: Optional Supporter Pass activated completely offline via NIST P-256 ECDSA public key verification.

---

## Table of Contents

1. [Requirements](#requirements)
2. [Building the App](#building-the-app)
3. [Product Flavors](#product-flavors)
4. [Android Studio Setup](#android-studio-setup)
5. [If the Build Shows Old UI](#if-the-build-shows-old-ui)
6. [Release Builds](#release-builds)
7. [Contributing & Bug Reports](#contributing--bug-reports)
8. [Project Documentation](#project-documentation)

---

## Requirements

| Tool | Version |
| --- | --- |
| Android Studio | Meerkat or newer |
| Gradle | 9.5+ (managed by wrapper) |
| Android SDK compile target | API 36 |
| Min Android version | API 26 (Android 8.0) |
| JDK | 17 |

---

## Building the App

> **[IMPORTANT] Product Flavors**: AmniShield uses three **product flavors**
> (`playstore`, `fdroid`, `universal`). The plain `assembleDebug` Gradle task is
> **no longer valid**. Always use a flavor-qualified task. Running bare `assembleDebug`
> will silently serve a stale cached APK with old UI and themes.

### Debug Builds (for testing / development)

```powershell
# Build all three flavors at once (recommended)
.\gradlew assemblePlaystoreDebug assembleFdroidDebug assembleUniversalDebug

# Or build individually
.\gradlew assemblePlaystoreDebug   # Google Play Store flavor
.\gradlew assembleFdroidDebug      # F-Droid / offline license flavor
.\gradlew assembleUniversalDebug   # Universal / sideload flavor
```

APK output paths:

| Flavor | APK Location |
| --- | --- |
| playstore | `app/build/outputs/apk/playstore/debug/app-playstore-debug.apk` |
| fdroid | `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk` |
| universal | `app/build/outputs/apk/universal/debug/app-universal-debug.apk` |

---

## Product Flavors

| Flavor | Description | Google Services | Billing |
| --- | --- | --- | --- |
| `playstore` | Google Play Store distribution | Included: Firebase, Play Billing | Play Billing |
| `universal` | Sideload / alternative stores | Included: Firebase | Play Billing |
| `fdroid` | F-Droid / fully open source | None: Pure FOSS | Offline ECDSA license |

Each flavor has its own source set under `app/src/<flavor>/java/` for swapping
billing and sign-in implementations without `#ifdef`-style hacks.

---

## Android Studio Setup

1. Open the project root in Android Studio.
2. Open the **Build Variants** panel (`View -> Tool Windows -> Build Variants`).
3. Set the **Active Build Variant** for `:app` to one of:
   - `playstoreDebug`
   - `fdroidDebug`
   - `universalDebug`
4. Hit **Run** as normal.

> If the Build Variants panel shows just `debug` (no flavor prefix), the IDE has not yet
> synced the new flavor configuration. Run **File -> Sync Project with Gradle Files** to fix it.

---

## If the Build Shows Old UI

If you see the legacy purple gradient background, old DeenShield icons, or the old
Settings bottom-nav tab instead of the new Blocks tab, the Gradle build cache is serving
a stale APK. Fix it by doing a clean build:

```powershell
# Step 1: Wipe all build outputs and cached intermediates
.\gradlew clean --no-daemon

# Step 2: Rebuild fresh (--no-daemon forces a new JVM, bypassing any stale cached daemon)
.\gradlew assemblePlaystoreDebug assembleFdroidDebug assembleUniversalDebug --no-daemon
```

The `--no-daemon` flag is the key - without it, Gradle may reuse an old daemon process
pinned to a previous Gradle version that has incorrect cached fingerprints.

---

## Release Strategy

### Which APK Goes Where?

| Flavor | Published to | Audience |
| --- | --- | --- |
| `universal` | **GitHub Releases** | Standard Android - sideloading with Google Play Services |
| `fdroid` | **GitHub Releases** | De-Googled phones (GrapheneOS, CalyxOS, LineageOS without GMS) |
| `playstore` | **Play Console only** | Google Play Store users - never published to GitHub |

> `universal` and `fdroid` are **not interchangeable**. The `universal` flavor depends on
> Google Play Services for billing and sign-in. Users on de-Googled ROMs must use the
> `fdroid` APK, which has zero Google dependencies and uses offline ECDSA licensing instead.

### Automated GitHub Releases (CI)

The [release.yml](.github/workflows/release.yml) workflow fires automatically when you
push a version tag. It builds, signs, and publishes both APKs to GitHub Releases:

```bash
# Tag a release (triggers the workflow)
git tag v1.2.3
git push origin v1.2.3
```

For Self Build:

```powershell
# GitHub Releases (the two public APKs)
.\gradlew assembleUniversalRelease
.\gradlew assembleFdroidRelease

# Play Store (submitted via Play Console, not GitHub)
.\gradlew assemblePlaystoreRelease
```

> **Never commit `keystore.properties` or `.jks` files to version control.**
> These are already listed in `.gitignore`.

---

## Contributing & Bug Reports

Contributions, bug reports, and feedback are warmly welcomed!

- **Report a Bug**: If you notice unexpected behavior, crashes, or unblocked surfaces, please open a [Bug Report](https://github.com/alhaq-studio/amnishield-android/issues/new?template=bug_report.md).
- **Request a Feature**: Have an idea for a new filter, accessibility improvement, or custom rule? Submit a [Feature Request](https://github.com/alhaq-studio/amnishield-android/issues/new?template=feature_request.md).
- **Google Play Beta Feedback**: You can also submit private beta feedback directly through Google Play: [Join Open Testing](https://play.google.com/apps/testing/com.alhaq.deenshield).
- **Email Support**: Reach our engineering team directly at `support@alhaq.uk`.

---

## Project Documentation

| File | Description |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture, blocker pipeline & logging invariants |
| [ROADMAP.md](ROADMAP.md) | Feature roadmap and recent changelog |
| [TESTING_GUIDE.md](TESTING_GUIDE.md) | Full manual test scenarios for all features |
| [PRIVACY_POLICY.md](PRIVACY_POLICY.md) | App privacy policy |
| [TERMS_OF_SERVICE.md](TERMS_OF_SERVICE.md) | Terms of service |
| [FDROID_PUBLISHING_GUIDE.md](FDROID_PUBLISHING_GUIDE.md) | Steps to publish on F-Droid |

---

## Technical Note: Package Name & Application ID

For historical reasons (AmniShield was rebranded from DeenShield), there is a mismatch in the project identifiers:

- **`applicationId`**: `com.alhaq.deenshield`
- **`namespace`**: `com.alhaq.amnishield`

Because the Google Play Store registry binds permanently to the `applicationId`, changing it would require publishing a completely new listing. Consequently, we **maintain the legacy applicationId (`com.alhaq.deenshield`)** to avoid disrupting existing users, while codebase packages and code structures use the modern namespace `com.alhaq.amnishield`. Do not change the `applicationId` when making code updates.

---

## Acknowledgments & Open-Source Credits

- **[Curbox](https://github.com/curbox-app/curbox-android)** by **Nethical** (GPL-3.0-or-later):
  Reel & short-form video surface detection patterns and floating WindowManager overlay mechanics are inspired by Curbox.

---

## License

This project is licensed under the **GNU General Public License v3.0**.
See [LICENSE](LICENSE) for the full text.

# نور البصيرة | Noor Al-Basirah

Erthi | إرثي — Free & Open-Source Islamic Software
https://erthi.org · admin@erthi.org

Android alpha for offline Quran memorization, built around selectively migrated MIT HifzAI engines and their original tests. Includes a byte-verified 6236-ayah Tanzil corpus, source-based search and surah/ayah navigation, display-only word masking, recall/bridge stages, bounded self-assessment, spaced review, local session/progress and a daily target. External reciter playback and the old app’s full feature set have not yet been migrated.

## Test status and scope
This is 0.1.0-alpha, for technical testing. No scholarly certification, production readiness or physical-device validation is claimed. Erthi is a personal free/open-source initiative; the GitHub organization is not asserted to be an independent legal entity. No legal HifzAI IP assignment is claimed.

- Kotlin / Jetpack Compose / Material 3 / ViewModel, Navigation and private local persistence.
- Arabic-first resources plus English, French, Turkish, Urdu, Indonesian, German and Spanish. AppCompat app-language settings recreate safely; RTL/LTR follows locale. Native-speaker review remains pending.
- Dark/light/system appearance, high contrast, large system fonts, ordered full-width controls and accessible labels. Real TalkBack testing remains outstanding.
- No Internet, microphone, location or broad storage permission; Android backup disabled. AndroidX may declare its app-local signature permission for protected dynamic receivers.
- [Privacy](PRIVACY.md), [sources](docs/CONTENT_REGISTRY.json), [notices](NOTICE.md), [dependency policy](docs/DEPENDENCIES.md), [remaining release gates](docs/RELEASE_CHECKLIST.md).

## Build and checks
Use Java 17 and Android SDK 35:

```sh
python3 tools/prepare_assets.py
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:dependencyInventory
python3 tools/verify_foundation.py
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. It uses a disposable Debug signature, not a production release key. GitHub Actions uploads a test-only APK with SHA-256 only after the build job succeeds. Emulator tests run separately on API 26 and 35. Always check the exact commit/run and test reports before attributing results. Production packaging is blocked pending review.

## Next priorities
Finish the safe migration of tested audio/progress/bookmark components from the preserved HifzAI reference; provide an explicit old-data import path; verify text rendering and TalkBack on physical devices. [Detailed HifzAI audit and migration provenance](docs/HIFZAI_AUDIT_2026-11-09.md). The upstream repository retains the original history. The migration manifest records the source commit, individual digests, and the checksum of the local history backup. Imported files retain original MIT attribution.

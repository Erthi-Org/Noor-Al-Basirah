# Noor Al-Basirah — HifzAI reuse and migration plan

Status: the project initiator has approved pursuing ownership transfer to Erthi. This is authorization to plan and document the transfer, NOT evidence that a legal IP assignment or GitHub transfer has occurred.

## Existing source
- https://github.com/nexvary/NEXVARY-HifzAI
- Relevant development branches: `dev/foundation`, `feature/noor-al-basirah-accessibility`.
- Existing repository declares MIT for original code (copyright 2026 NEXVARY), while this repository uses Apache-2.0.

## Decision
Build Noor Al-Basirah as Erthi's standalone Quran memorization and accessibility application. Reuse verified, compatible HifzAI components selectively, rather than deleting or automatically transferring the existing repository.

## Ownership transition decision
- Intended public project home: Erthi-Org/Noor-Al-Basirah, retaining full commit history and contributor attribution.
- Erthi-Org is a GitHub organization, not automatically a separate legal person capable of holding IP. Identify the actual legal rights-holder and intended transferee before signing any assignment.
- The original repository declares copyright 2026 NEXVARY. Confirm whether the owner is an individual or Nexvary Inc and whether company approvals are required.
- Prepare a signed written IP assignment or an appropriate irrevocable license to a legally identified holder, then update notices; do not claim ownership has already changed.
- Do not overwrite or delete the existing Noor-Al-Basirah repository; integrate via reviewed history-preserving migration and PRs.

## Before any reuse
1. Inventory actual source files, dependencies, contributors, code quality, tests, and build status.
2. Check copyright ownership and contributors' rights. Keep original MIT notices in copied files and retain attribution. A separate written rights assignment is required if ownership itself is to transfer.
3. Audit Quran text, translations, recitations, fonts, images and APIs individually. MIT/Apache-2.0 for software does not cover these assets.
4. Import only reviewed code via documented commits/PRs with source revision and attribution.
5. Keep HifzAI intact and publicly attributed until migration is independently validated.

## Product requirements
- Kotlin / Jetpack Compose, Android-first, offline-first.
- Quran memorization, recall tests, spaced review, verse transitions and progress stored locally.
- TalkBack, semantic labels, large text, high contrast, voice navigation and non-visual feedback.
- Localization architecture: Arabic, English, French, Turkish, Urdu, Indonesian, German, Spanish. RTL for Arabic and Urdu; LTR for other languages.
- Preserve verified Arabic Quran text verbatim; translations are separately licensed translations of meanings.
- No claims of certified tajweed or scholarly approval until independently verified.

## Release gates
- Reproducible build and CI; dependency and license inventory.
- Tests for text integrity, accessibility, RTL/LTR, offline behavior, privacy and data export.
- Human scholarly review of religious content before publication.
- Real Android device validation before claiming release readiness.

# Easel Mobile (P2.1 in progress)

This repository is a fork of [Mika](https://github.com/wangzan101/Mika) used as the
Android host for Easel. The goal is a phone-only social-content workspace: the
agent runs on the phone, operates the real apps, creates artifacts locally, and
publishes without a desktop browser.

## What is already wired

- **Android host**: Mika's Compose UI, foreground task service, accessibility
  automation, virtual background display, built-in WebView/CDP, and Alpine
  sandbox (PRoot, Python, Node).
- **Easel identity**: `core/src/main/resources/prompts/system/easel.md` adds the
  five-layer content workflow (discover, plan, create, publish, attribute) to
  every run.
- **Account profile**: `内容工作台 → 账号画像` stores a markdown profile in
  `files/easel/profile.md`; the profile is injected into every task's context.
- **Content library**: `内容工作台 → 内容库` lists files exported by
  `share_file` under `Download/Easel` and opens/shares them with the system.
- **Content calendar**: the phone keeps plans and a publishing log in app-private
  storage. The Agent uses `content_plan_*` and `content_publish_log_*`; dates are
  validated as real local dates and published records update their plan.
- **Five-stage workflow**: `内容工作台 → 五层工作流` shows discover, plan,
  create, publish and attribute with live counts from the local calendar and
  publishing log. Each stage links to the next phone screen or copies a ready
  prompt for the Agent.
- **Publish center**: ready and scheduled plans form a local publishing queue.
  Copy the publish prompt, run it with the phone Agent, verify in the app, then
  record the real result. Published and failed records can be filtered.
- **Metrics and attribution**: `content_metrics_add` records real performance
  snapshots in `files/easel/metrics.json`; the Agent can list, delete and
  summarize them with `content_attribution_summary`. The attribution screen
  shows platform totals, top posts and recent snapshots, using a deterministic
  engagement score rather than inventing causality.
- **Pre-publish risk scan**: `content_risk_scan` checks text locally for likely
  secrets, private network addresses, private paths, identity/phone numbers,
  absolute advertising claims and common medical/financial promises. Evidence
  is masked. This is a checklist, not a platform review or plagiarism check.
- **Material library**: `内容工作台 → 素材库` imports files from the system picker
  into `Download/Easel`, then previews, shares or deletes them. It only reads
  files this app created; it does not scan the rest of the phone.
- **Card rendering**: `render_card` turns self-contained HTML in `/workspace`
  into a PNG on the phone. The Android renderer uses a private VirtualDisplay
  with Presentation, WindowManager and software-Canvas fallbacks, disables
  JavaScript/network/local-file access, and returns a deterministic layout audit.
- **Skills**: 60 bundled skills cover writing, planning, Xiaohongshu, Douyin,
  Kuaishou, Bilibili, Zhihu, WeChat, cross-platform publishing, scheduling,
  analytics, postmortems, account diagnosis, topic evaluation, quality gates,
  video/audio/image processing and asset management.
  They are indexed at build time from `core/src/main/resources/skills/`.
- **Sandbox media**: the bundled Alpine runtime now includes `ffmpeg`, so the
  phone can transcode, crop, reframe, extract audio and burn subtitles offline.
- **App identity**: label `Easel`, application id `com.easel.mobile`.

## Build on GitHub

APKs are built by GitHub Actions, not on the development machine. The workflow
is [`.github/workflows/android.yml`](.github/workflows/android.yml).

- Push to `main` or `easel-mobile-p0`, open a pull request, or run **Build Easel
  APK** manually from the Actions tab.
- The workflow runs the core and app unit tests, then builds `:app:assemblePerf`
  and uploads `Easel-<version>.apk`, `Easel-<version>-arm64.apk`, and
  `SHA256SUMS.txt` as the `easel-apk-<commit>` artifact.
- Push a `v*` tag to attach the same APKs and checksums to a GitHub Release.

The perf build is optimized, debug-signed, and can be installed over the debug
build while keeping app data. It is suitable for testing, not store publishing.

## Install and first run

1. Install the APK on an Android 12+ phone (Android 14+ recommended).
2. Add a model provider in Settings.
3. Complete `设置手机操作` (wireless debugging pairing + accessibility).
4. Open `内容工作台`, fill the account profile, save.
5. Start a chat: for example, `帮我写一条小红书笔记并发布`.

`xhs-publish` uses the Xiaohongshu app on the phone. It checks the draft, asks
for confirmation when needed, publishes, and verifies the new note. If the app
shows a login, captcha, risk prompt, or payment step, the agent hands the phone
back to the user.

## Milestones

- M0 complete (internal engineering milestone): phone-only Android host with Mika's accessibility, browser and
  sandbox capabilities, plus the Easel identity and content profile.
- M1 complete: native content calendar and publishing log in app-private files,
  exposed as `content_plan_*` and `content_publish_log_*`.
- M2 complete: native WebView card renderer, `render_card`, and the
  `card-design` / `card-quote` skills. No Playwright, Chromium download or CDN
  fonts are needed.
- P1 complete (product roadmap): five-stage workflow MVP, content calendar,
  material library, publish center, 30-40 high-frequency skills, WebView card
  rendering, and bundled ffmpeg. Real-device publishing still needs a final
  acceptance pass on the user's phone.
- P2.1 in progress: local metrics snapshots and attribution summary, deterministic
  pre-publish risk scan, and the first 21 additional skills (60 total).

## Next milestones

- Map Easel's 6-file profile format to the single mobile profile file, or split
  it into sections in a later schema migration.
- Verify card rendering on real Android 12/14/15 devices, including system font
  fallback and VirtualDisplay behavior under battery saver.
- P2 remaining: grow from 60 skills to the full 115-skill catalog, finish
  platform-specific adaptation and risk controls, and complete the audio/video
  pipelines (chapters, highlights, video-to-article, subtitle translation,
  audio mixing and slideshow video).

## Licensing

Easel and Mika are Apache-2.0. Mika's APK also ships GPL/LGPL components (PRoot,
Alpine packages); keep the corresponding-source offer when distributing a
modified APK. See `LICENSE`, `NOTICE`, and `licenses/THIRD_PARTY_NOTICES.md`.

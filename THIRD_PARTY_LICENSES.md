# Third-Party Licenses

风铃code bundles, links, or depends on the following third-party components. Versions reflect the current source tree; license types were verified against each project's repository (GitHub license metadata / LICENSE files).

## Native C/C++ dependencies (`deps/`)

| Component | Version / Source | License | Notes |
|---|---|---|---|
| [proot](https://github.com/OpenMinis/proot) (fork) | git submodule `deps/proot` | **GPL-2.0** | Linux sandbox on Android (`libproot.so`, `proot-aarch64`) |
| [FFmpeg](https://ffmpeg.org) | 6.1.2 | **LGPL-2.1-or-later** (built without `--enable-gpl` / `--enable-nonfree`) | Keep the LGPL configuration |
| [LAME](https://lame.sourceforge.io) | 3.100, vendored at `deps/lame-3.100` | **LGPL-2.0-or-later** | MP3 encoder, linked into FFmpeg via `--enable-libmp3lame` |
| [talloc](https://talloc.samba.org) (Samba) | vendored at `deps/talloc` | **LGPL-3.0-or-later** | Memory allocator required by proot |
| [cppjieba](https://github.com/yanyiwu/cppjieba) | vendored (Android `jieba_jni`) | **MIT** | Chinese word segmentation (header-only + dictionaries) |
| Alpine Linux minirootfs | downloaded at build time by `scripts/prepare_android_sandbox.sh` | Aggregate of package licenses (musl **MIT**, BusyBox **GPL-2.0**, etc.) | Not stored in this repo; bundled into app builds as the default rootfs |

## Android — Gradle dependencies

| Library | Version | License |
|---|---|---|
| AndroidX / Jetpack (Compose BOM 2025.09.00, core-ktx, lifecycle, activity, navigation, Room, DataStore, security-crypto, browser, webkit, exifinterface) | see `app/build.gradle.kts` | **Apache-2.0** (Google / AOSP) |
| OkHttp + okhttp-sse | 4.12.0 | **Apache-2.0** |
| kotlinx-serialization-json | 1.7.3 | **Apache-2.0** |
| kotlinx-coroutines-android | 1.9.0 | **Apache-2.0** |
| Coil (coil-compose) | 2.7.0 | **Apache-2.0** |
| multiplatform-markdown-renderer (+ m3) — mikepenz | 0.33.0 | **Apache-2.0** |
| Reorderable (sh.calvin.reorderable) | 2.4.0 | **Apache-2.0** |
| ACRA (acra-core) | 5.12.0 | **Apache-2.0** |
| Shizuku API + provider (dev.rikka.shizuku) | 13.1.5 | **MIT** |

Test-only dependencies: JUnit 4.13.2 (**EPL-1.0**), MockWebServer 4.12.0 (**Apache-2.0**), kotlinx-coroutines-test 1.9.0 (**Apache-2.0**), org.json 20231013 (**Public Domain / JSON License**).

## Bundled web/UI assets

| Asset | Location | License |
|---|---|---|
| KaTeX | Android `app/src/main/assets/katex/` | **MIT** |
| jieba dictionaries | Android `assets/jieba/` | **MIT** (cppjieba distribution) |

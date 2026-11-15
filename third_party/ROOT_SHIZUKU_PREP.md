# Shizuku 13.1.5 / libsu 6.0.0 dependency preparation

Offline dependency preparation and local source builds. Shizuku artifacts are pinned and hash-verified; the publisher libsu AAR endpoints returned HTTP 500, while patched local AARs are available as recorded below.

## Shizuku API 13.1.5

Official source: [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API); repository used: [Maven Central `dev.rikka.shizuku`](https://repo.maven.apache.org/maven2/dev/rikka/shizuku/13.1.5/). Files: `third_party/shizuku-13.1.5/`.

| Artifact | POM packaging/scope | Bytes | SHA-256 | Upstream SHA-1 sidecar |
|---|---|---:|---|---|
| `api-13.1.5.aar` | AAR; compile `aidl`, `shared`; runtime AndroidX Annotation | 24,652 | `4DEF9BDE498EF8626614C2FC5DB9AF4749C86F16F6C33E3F5658D35E70BAB59B` | `1f760c56493bdbc01462930e75bb325033b36fe2` |
| `provider-13.1.5.aar` | AAR; runtime `api`, AndroidX Annotation | 7,056 | `B0F18CD9812464EC171C53CAC93A819FE411718A3965C311F01EB4DE265381B3` | `4135b99cae9362fe2f63380230d04a9bcba9be3f` |
| `aidl-13.1.5.aar` | AAR; no POM dependencies | 23,673 | `33FE7191CDD69FCB66D649264F3B0C47ACB2F3D6343AFC05B98DBBFF6F221963` | `457504c277d796adea698b80b4e765f98dd0799a` |
| `shared-13.1.5.aar` | AAR; runtime `aidl`, AndroidX Annotation | 2,614 | `4659642C9339BE0A26E9C65BB8648F7AD6D8F4A465F557993CCBC78802381635` | `8c4e73ce466cd27aff10565d2ec49809c258f974` |
| `annotation-1.3.0.jar` | Runtime transitive; Google Maven | 31,705 | `97DC45AFEFE3A1E421DA42B8B6E9F90491477C45FC6178203E3A5E8A05EE8553` | `021f49f5f9b85fc49de712539f79123119740595` |

The four AARs were opened as ZIP archives and each contained `AndroidManifest.xml` and `classes.jar`. Every published SHA-1 sidecar downloaded for the AARs/POMs and AndroidX POM/JAR matched the local SHA-1 calculation. Maven Central did not publish `.sha256` sidecars for these paths. POMs and `.sha1` files are retained beside the artifacts. Shizuku upstream LICENSE (1,062 B, SHA-256 `64870037F294B6F2B75C8EBE77CC0702ACB3B64F86B4B0E58BF8558525303686`) is retained as `third_party/shizuku-13.1.5/LICENSE`; the POM identifies MIT and links the upstream repository license. AndroidX Annotation POM identifies Apache-2.0.

Total AAR/JAR payload prepared: 89,700 bytes (not including POMs, sidecars or licenses). No Gradle configuration was changed during the initial Shizuku artifact-preparation step.

## libsu 6.0.0

Official source: [topjohnwu/libsu tag 6.0.0](https://github.com/topjohnwu/libsu/tree/6.0.0). The exact requested JitPack POMs for `com.github.topjohnwu.libsu:core:6.0.0` and `service:6.0.0` were retrieved; both specify `<packaging>aar</packaging>`. The service POM has compile dependency on core. The exact requested AAR paths and their `.aar.sha1` sidecars returned HTTP 500 from `jitpack.io`; no alternate mirror was used. JitPack's official `6.0.0/build.log` reports `BUILD SUCCESSFUL` / tool exit 0 while showing 19 Javadoc `reference not found` errors and missing lint-resource reports, so the log does not explain the artifact-endpoint HTTP 500. The log is retained at `build/v2.4.5-audit/source-inspection/libsu-jitpack-build.log` (63,510 B); publisher AAR retrieval remains unavailable, while patched local source-built AARs are recorded below.

The official GitHub tag source archive was inspected read-only and retained at `build/v2.4.5-audit/source-inspection/libsu-6.0.0-source.zip` (206,045 B, SHA-256 `6090C040D561D181E64A2688A6D5BC820EF3BE1B1DBFA59C081821C81A85F9CB`). It includes core/service Gradle source, a Gradle 8.7 wrapper, and `service/src/main/assets/main.jar` (3,656 B; contains `classes.dex`). The service Gradle file registers a task that rebuilds this `main.jar` using AGP's R8 over `RootServerMain*` and `IRootServiceManager*` class files. The isolated local-build project compiles the official tag source with AGP 9.0.1, compileSdk 36, and offline dependencies. Publisher AAR retrieval remains blocked; the locally rebuilt AARs are not publisher artifacts.

`third_party/libsu-6.0.0/` contains the two POMs, upstream Apache-2.0 LICENSE (11,357 B; SHA-256 `C71D239DF91726FC519C6EB72D318EC65820627232B2F796219E87DCF35D0AB4`), and source/build provenance. Locally built core/service AARs reside in `local-build/output/`; the root package directory is not itself a complete Maven repository.

Useful upstream 6.0.0 source/API anchors:

- `Shell.Task.run(OutputStream stdin, InputStream stdout, InputStream stderr) throws IOException`, plus default `shellDied()`: [Shell.java](https://github.com/topjohnwu/libsu/blob/6.0.0/core/src/main/java/com/topjohnwu/superuser/Shell.java).
- `Shell.getCachedShell()` returns nullable cached active shell; `Shell.getShell()` can synchronously create one and may block on a root prompt; `Shell.cmd(String...)` creates a pending main-shell job. Prefer callback `Shell.getShell(executor, callback)` off the UI thread for acquisition.
- `RootService.bindOrTask(Intent, Executor, ServiceConnection): Shell.Task?`: [RootService.java](https://github.com/topjohnwu/libsu/blob/6.0.0/service/src/main/java/com/topjohnwu/superuser/ipc/RootService.java). It returns null when already bound/connected; otherwise execute the returned task with `Shell.execTask` to start the root process and bind. `RootService.bind(...)` wraps this and schedules the task on libsu's executor.

The signatures above are upstream API references. The app uses the local source-built AARs; device behavior remains unverified. Source references were checked at the fixed 6.0.0 GitHub tag.

### Local libsu AAR build update (2026-10-21)

The isolated project in `third_party/libsu-6.0.0/local-build/` builds core and service from official tag source plus the local `root-session-retry.patch`, using Gradle 9.1.0, AGP 9.0.1, SDK 36, and offline dependencies. The source is modified; these AARs are not publisher-signed or downloaded JitPack artifacts. The top-level Compose and serialization Kotlin plugins are declared `apply false` at 2.3.20 to align AGP's Kotlin plugin dependency with this machine's cache. This is a local build, not the publisher's JitPack AAR and not publisher-signed.

Artifacts are in `third_party/libsu-6.0.0/local-build/output/`: `libsu-core-6.0.0-locally-built.aar` and `libsu-service-6.0.0-locally-built.aar` (bytes and hashes are recorded in provenance). Both AARs passed ZIP checks for `classes.jar` and `AndroidManifest.xml`. The service AAR's `assets/main.jar` is byte-identical to the upstream tag asset (3,656 B; SHA-256 `AE7FDB8B70BD2C1CC2C27043E985F8DC5CFFF2CB9142A1576DCDBC889763EF7B`); the upstream R8 regeneration task was omitted.

Build provenance and artifact hashes are recorded in `third_party/libsu-6.0.0/local-build/output/provenance.json`; successful patched-source rebuild log: `build/v2.4.5-audit/source-inspection/libsu-local-build-attempt5.log`. The upstream service `main.jar` remained byte-identical (3,656 B; SHA-256 `AE7FDB8B70BD2C1CC2C27043E985F8DC5CFFF2CB9142A1576DCDBC889763EF7B`). The exact upstream licenses and a local-change notice are copied into `app/src/main/assets/third_party/`.



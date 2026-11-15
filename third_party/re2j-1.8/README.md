# Google RE2/J 1.8 provenance

- Maven Central artifact: `com.google.re2j:re2j:1.8`
- JAR: `app/libs/re2j-1.8.jar` (114,335 bytes)
- Source: `https://repo.maven.apache.org/maven2/com/google/re2j/re2j/1.8/re2j-1.8.jar`
- Official POM: `https://repo.maven.apache.org/maven2/com/google/re2j/re2j/1.8/re2j-1.8.pom`
- Maven Central SHA-1 sidecar: `https://repo.maven.apache.org/maven2/com/google/re2j/re2j/1.8/re2j-1.8.jar.sha1`
- SHA-1 sidecar and downloaded JAR match: `12c25e923e9e4fb1575a7640a2698745c6f19a94`
- Locally computed JAR SHA-256: `7B52C72156DD7F98B3237A5B35C1D34FBA381B21048C89208913AD80A45DFBD7`
- Upstream `.jar.sha256` sidecar was requested from Maven Central but returns 404; the local SHA-256 above is calculated from the TLS-downloaded artifact, not represented as an upstream-published digest.
- License: Go License, copied from the upstream `re2j-1.8` tag at `https://github.com/google/re2j/blob/re2j-1.8/LICENSE`; local copy is `LICENSE`.
- The same unmodified license text is bundled as `app/src/main/assets/licenses/re2j-1.8-LICENSE.txt`.
- POM declares JUnit/GWT/Truth/SLF4J dependencies with `test` scope only; the pinned runtime JAR adds no Gradle transitive dependency.
- Full source URLs, POM/license hashes, and verification timestamp: `provenance.json`.

Gradle pins the local file from `app/build.gradle.kts` for offline builds. No build or device test was run as part of fetching and pinning this artifact.

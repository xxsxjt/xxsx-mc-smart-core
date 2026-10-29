# Xxsx Smart Core v1.1.12

**Minecraft 26.2 / NeoForge 26.2.0.7-beta / Java 25 / Client only**

## Highlights

- Fixed the JPMS split-package conflict between Smart Core and the optional Model Import extension by moving the bridge API to `com.xxsx.builder.api.modelimport`.
- Smart Core remains usable on its own. PMX/OBJ import and voxel building require the separate Model Import jar.
- The extension requires Smart Core v1.1.12 or later.
- Retains v1.1.11 runtime registry lookup and bilingual AI behavior.

## Files

- Smart Core (`xxsx_mc_smart_core`): `xxsx-mc-smart-core-neoforge-26.2-1.1.12.jar`
- Optional Model Import (`xxsx_builder`): `xxsx-mc-model-import-neoforge-26.2-1.1.12.jar`

Install both matching jars only when PMX/OBJ model import is needed. The Smart Core jar alone provides the assistant and registry features.

## Verification

- `neoforge-26.2/gradlew.bat clean build --no-daemon --offline` with Java 25: passed.
- JAR class package sets: no overlap; bridge API is in Smart Core and the handler/mod entrypoint are in Model Import.
- Client startup, in-game smoke test, deployment, and publication: not performed.

Artifact sizes and SHA-256 values are recorded in `.github/release-notes/neoforge-client.md`.

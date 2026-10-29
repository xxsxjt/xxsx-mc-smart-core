# Xxsx Smart Core - 1.20.1 / 1.21.1 / 1.21.11 补丁发布

本次发布补齐三个旧版本模块，使其与 26.2 使用相同的双 jar 结构与内置免费模型通道。

| Minecraft | Loader | Mod 版本 | Smart Core jar | Model Import jar（可选） |
|---|---|---|---|---|
| MC 1.20.1 | Forge 47.2.0+ | v1.1.1 | `xxsx-mc-smart-core-forge-1.20.1-1.1.1.jar` | `xxsx-mc-model-import-forge-1.20.1-1.1.1.jar` |
| MC 1.21.1 | NeoForge 21.1.138 | v1.1.1 | `xxsx-mc-smart-core-neoforge-1.21.1-1.1.1.jar` | `xxsx-mc-model-import-neoforge-1.21.1-1.1.1.jar` |
| MC 1.21.11 | NeoForge 21.11.42 | v1.1.2 | `xxsx-mc-smart-core-neoforge-1.21.11-1.1.2.jar` | `xxsx-mc-model-import-neoforge-1.21.11-1.1.2.jar` |

主要变化：

- 默认 AI 通道由 Agnes 换为内置 Kilo 免费模型：`reflection/beam-free` → `kilo-auto/free` → `recursive/flint-v1.0:free` 依次尝试，无需 API Key。原硬编码 Agnes Key 已移除。
- 推理型模型把答案放在 `reasoning` 字段而不填 `content`，解析器已兼容该情况，避免出现空回复。
- 配置界面新增“使用内置免费模型”按钮（1.21.11 为改文案），登录提示与 `/ai addmodel` 帮助文本同步更新；Agnes 官网链接改为免费模型说明链接。
- 桥接 API 移至 `com.xxsx.builder.api.modelimport`，Smart Core 与模型导入扩展的 Java 包集合无交集，消除 split-package 冲突。
- 修复 `build.gradle` 中 `sourceSets` 定义晚于 `neoForge` 造成的配置期失败。
- 修复 `forge-1.20.1` / `neoforge-1.21.1` 使用 Forge 1.20.1 不支持的 `@Mod(dist = ...)` 导致的编译失败。
- 修复 `ModelImportHandler.java` 中被剥离反斜杠的转义引号（`"\""` 退化为 `"""`）；该文件自 `a6d4bea` 起从未编译通过。
- 修复 mod 元数据不一致：核心 jar 的 `mods.toml` / `neoforge.mods.toml` 原先声明 `xxsx_builder`，而 `@Mod` 主类使用 `xxsx_mc_smart_core`，加载器会因找不到 mod 主类而失败。现统一为 `xxsx_mc_smart_core`，`EventBusSubscriber` 与配置文件（`xxsx_mc_smart_core-common.toml`）同步改名。
- 为 model-import jar 补齐 `mods.toml` / `neoforge.mods.toml` 并声明对 Smart Core 的 `AFTER` 依赖；此前该 jar 只打包 `MANIFEST.MF`，无法被识别为独立 mod。

验证：

- 三个模块 `gradlew build modelImportJar` 均构建成功（Java 17 / 21）。
- 每个模块的 Smart Core 与 Model Import jar 的 class 与 package 集合重叠数均为 0。
- 两个 jar 的 `modId` 分别为 `xxsx_mc_smart_core` 与 `xxsx_builder`，互不冲突。
- 免费模型通道实测（9 次连续请求）：全部 HTTP 200，响应 1.0–2.2 秒，主模型 `reflection/beam-free` 一次命中、未触发回退；中文回复与多轮历史均正常。

| 模块 | Smart Core SHA-256 | Model Import SHA-256 |
|---|---|---|
| forge-1.20.1 | `db42b6010bb9562fb5e9520873f931038ae2cf94e0db9b605bd7a495cdc8d58a` | `c85017a9e224cb24d7b71d78ca3b05d876843792c13e87d2a5ed4c69ba875495` |
| neoforge-1.21.1 | `0e67eacc3de847b30c1d7e94514ab7a8ce58c9bc365ac208e5b088c08043d57d` | `3bd4fa3b3ae6ccb582e30431b893165823e225b31ebc9950981dc7a7c265f2c2` |
| neoforge-1.21.11 | `10134d7863ef80d25c8456ddb21c1ca98c0958e2fb07a8d0d9a8556d1eea1aef` | `08e3e6be6595b9000214c56bddacb64535e7583da0c4463d5d19650b6edd85e9` |

尚未执行：三个版本的客户端启动 smoke test。

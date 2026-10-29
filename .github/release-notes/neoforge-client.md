# Xxsx Smart Core - MC 26.2 JPMS compatibility update

本次候选更新面向 Minecraft 26.2 / NeoForge 26.2.0.7-beta / Java 25，版本为 v1.1.12。发行包由以下两个独立客户端 mod jar 组成：

| Mod | Mod ID | Jar | 安装要求 |
|---|---|---|---|
| Xxsx Smart Core | `xxsx_mc_smart_core` | `xxsx-mc-smart-core-neoforge-26.2-1.1.12.jar` | 独立运行 |
| Xxsx Model Import | `xxsx_builder` | `xxsx-mc-model-import-neoforge-26.2-1.1.12.jar` | 可选；需要 Smart Core `[1.1.12,)` |

模型导入扩展提供 PMX/OBJ 导入和体素建造。只使用 Smart Core 时无需安装该扩展。

本候选版本不更新 Forge 1.20.1 或 NeoForge 1.21.11；NeoForge 1.21.1 和 Forge 26.x 仍为未发布的本地试验模块。

## 主要变化

- 修复 Smart Core 与模型导入扩展同时导出 `com.xxsx.builder.modelimport` 引起的 JPMS split-package 启动冲突；桥接 API 使用独立的 `com.xxsx.builder.api.modelimport` 包。
- 保持扩展到 Smart Core 的运行时桥接注册/注销能力，并维持核心不依赖扩展的可选安装关系。
- 内置默认 provider 由 Agnes 换为 Kilo AI 网关免费层：`reflection/beam-free` → `kilo-auto/free` → `recursive/flint-v1.0:free` 依次尝试，无 Key；原硬编码 Agnes Key 已移除。推理型模型的答案位于 `reasoning` 字段，解析器已兼容，避免空回复。
- 继承 v1.1.11 的运行时注册表查询、中英文 AI prompt 与游戏语言自适应反馈。

## 验证状态

- 构建：`neoforge-26.2/gradlew.bat clean build --no-daemon --offline`，使用 Java 25，成功。
- Smart Core：`neoforge-26.2/build/libs/xxsx-mc-smart-core-neoforge-26.2-1.1.12.jar`，174,966 bytes，SHA-256 `d43c5675ea598610eacd6fd75cab3834605a82ace8610b62a9c361feb8716888`。
- Model Import：`neoforge-26.2/build/libs/xxsx-mc-model-import-neoforge-26.2-1.1.12.jar`，47,893 bytes，SHA-256 `65b8e55d36f701259028f25aa06e02df509d28270706af613e7d5195a301339c`。
- 两个 jar 的 class 包集合无交集；桥接 API 位于 Smart Core，处理器和 mod entrypoint 位于 Model Import；扩展元数据依赖 Smart Core `[1.1.12,)`。
- 免费模型通道实测：9 次连续请求全部 HTTP 200，响应 1.0–2.2 秒，主模型一次命中未回退；中文回复与多轮历史正常。
- 客户端启动和游戏内 smoke test：尚未执行；内置免费模型成功应答（HTTP 200）尚未实测。
- 未部署、未发布。

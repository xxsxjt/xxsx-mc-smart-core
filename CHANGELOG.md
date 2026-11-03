# 更新日志

## v1.1.13 (2026-10-29, MC 26.1.2 NeoForge)

- 新增 `neoforge-26.1.2` 模块，面向 Minecraft 26.1.2 / NeoForge 26.1.2.124（Java 25）。
- 适配 MC 26.1.2 客户端 GUI API（`Minecraft.screen` 字段与 `Minecraft.setScreen(...)` 方法，区别于 26.2 的 `Minecraft.gui.screen()` 与 `Minecraft.gui.setScreen(...)`）。
- 保持双 jar 架构：`xxsx-mc-smart-core-neoforge-26.1.2-1.1.13.jar` 与可选扩展 `xxsx-mc-model-import-neoforge-26.1.2-1.1.13.jar`（依赖 Smart Core `[1.1.13,)`），默认 AI 通道为 Kilo AI 网关免费层（`Beam → Auto → Flint`）。

## v1.1.2 (2026-10-28, MC 1.21.11 NeoForge)

- 内置默认 provider 改用 Kilo AI 网关的免密钥免费层（`https://api.kilo.ai/api/gateway`），模型链 `reflection/beam-free` → `kilo-auto/free` → `recursive/flint-v1.0:free` 依次回退。
- 推理型模型会把答案放在 `reasoning` 字段而不填 `content`，解析器已做兼容：`content` 为空时回退到 `reasoning`，避免空回复。
- 补齐双 jar 结构：桥接 API 移至 `com.xxsx.builder.api.modelimport`，与模型导入扩展的包集合无交集。
- 配置界面与登录提示同步更新为新模型链。
- 修复 `build.gradle` 中 `sourceSets` 定义晚于 `neoForge` 导致配置期失败的问题。
- 修复 mod 元数据：核心 jar 的 `neoforge.mods.toml` 由 `xxsx_builder` 改为 `xxsx_mc_smart_core`，与 `@Mod` 主类一致；此前两者不符会导致加载器找不到 mod 主类。
- 为 model-import jar 补齐 `neoforge.mods.toml`（此前只打包 `MANIFEST.MF`，无法被识别为独立 mod）。

## v1.1.1 (2026-10-28, MC 1.21.1 / 1.20.1)

- `neoforge-1.21.1`：补齐双 jar 结构、修复 `sourceSets` 顺序、新增"使用内置免费模型"按钮。
- `forge-1.20.1`：修复 Forge 1.20.1 不支持的 `@Mod(dist = ...)`、补齐双 jar 结构与内置模型按钮。
- 两个模块的默认 provider 均改用 Kilo AI 网关免费层（`Beam → Auto → Flint`）。
- 修复 `ModelImportHandler.java` 中被剥离反斜杠的转义引号（`"\""` 退化为 `"""`），该文件此前从未编译通过。
- 修复 mod 元数据：核心 jar 的 `mods.toml` / `neoforge.mods.toml` 由 `xxsx_builder` 改为 `xxsx_mc_smart_core`，与 `@Mod` 主类一致；`EventBusSubscriber` 与配置文件 `xxsx_mc_smart_core-common.toml` 同步改名。
- 为 model-import jar 补齐 `mods.toml` / `neoforge.mods.toml`（此前只打包 `MANIFEST.MF`，无法被识别为独立 mod）。

## v1.1.12 (2026-10-27, MC 26.2 NeoForge)

- 修复 Smart Core 与可选模型导入扩展共同导出 `com.xxsx.builder.modelimport` 导致的 JPMS split-package 冲突；桥接 API 移至独立包，保持核心与扩展间的可选运行时连接。
- 两个 mod 和 jar 均使用 v1.1.12；模型导入扩展依赖 Smart Core `[1.1.12,)`。
- 使用 Java 25 构建并检查两个 jar 的 Java 包集合及 mod 依赖元数据；客户端启动验证尚未执行。详见 `.github/release-notes/neoforge-client.md`。
- 内置默认 provider 由 Agnes 换为 Kilo 免费模型通道：`reflection/beam-free` → `kilo-auto/free` → `recursive/flint-v1.0:free` 依次尝试；原硬编码 Agnes Key 已移除。

## v1.0.5 (2026-06-26)

### 体素算法重写
- **逐体素 UV 纹理采样**：每个体素通过 3D 重心坐标反推 UV 独立采样纹理，放大倍数 = 更多色彩细节（旧版整个面同色）
- **2×2×2 超采样抗锯齿**：边缘体素测 8 个子采样点，≥4 命中才填充，大幅减少锯齿
- **Double 精度重心坐标**：修复 scale>200 时 float 精度崩坏导致全灰的 bug

### 建造体验
- **浮点倍数**：`/ai 2.5` 支持小数倍率，精细控制尺寸
- **脚底对齐**：模型最低点 = 玩家脚底，不再半截入地
- **速度持久化修复**：`/ai build speed` 现正确保存（修复目录不存在导致写失败）
- **默认速度下调**：2000→1000 方块/tick（40000→20000/秒）

### 配置
- `/ai addmodel` 无参数时显示配置文件编辑指引，不再依赖聊天框输长 URL

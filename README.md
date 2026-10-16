# xxsx-mc-smart-core

xxsx 的智能核心 — Minecraft 多版本客户端智能辅助模组。

当前 26.2 待发布版本 `neoforge-26.2-v1.1.11` 只更新并验证 MC 26.2 / NeoForge 26.2.0.7-beta 的客户端 jar；Forge 1.20.1 仍停留在上一轮 v1.1.0，NeoForge 1.21.11 仍停留在上一轮 v1.1.1，NeoForge 1.21.1 未测试、未发布。

当前本地 26.2 模块新增中英文 AI system prompt、按游戏语言自动切换的模型回答与知识摘要，以及运行时基础注册表查询能力，版本为 v1.1.11；物品、方块、实体、状态效果、药水、附魔、结构和生物群系均先查当前实例的精确注册 ID。本轮没有更新 Forge 1.20.1 和 NeoForge 1.21.11。v1.1.11 保持 26.2 身份命名：modid 使用合法下划线形态 `xxsx_mc_smart_core`，jar 文件名按仓库名使用 `xxsx-mc-smart-core`。

## 功能

| 功能 | 说明 |
|------|------|
| `/ai <消息>` | 自然语言 AI 对话，自动执行指令 |
| `/ai build <PMX路径>` | 解析 PMX 模型 → 体素建筑，`/ai <倍数>` 确认比例 |
| `/ai api/model` | 切换 AI 供应商和模型 |
| `/ai addmodel` | 添加个人 API 密钥 |
| `[CMD]` 标签 | AI 输出指令自动执行；状态变更命令先等待玩家确认 |
| `[QUERY]` 标签 | 查询游戏状态、运行时注册表（物品/方块/实体/状态效果/附魔/结构等）、配方与世界 |
| 联动动作目录 | 分类查询 Curios、虚无终端、Earth Human、地球工业、超凡与 JEI 入口 |
| `[KNOWLEDGE]` 标签 | 查询知识库（支持 jar 内置 + 外置扩展） |
| 会话持久化 | 同一玩家重连保留对话历史 |
| 上下文压缩 | 超限自动截断或 AI 总结 |
| PlayerConfig | 每玩家独立 API 配置 |
| 中英文 AI 回复 | 随游戏语言自动选择中文或英文 system prompt；知识库摘要同样跟随 |
| `[QUERY]registry` | 直接扫描运行时基础注册表 ID，优先为 `/give`、`/summon`、`/effect`、`/locate` 等查找精确目标 |

## 快速开始

1. 将 jar 放入 `mods/` 目录
2. 启动游戏，进入世界
3. 输入 `/ai 你好` 测试对话
4. 输入 `/ai addmodel <API地址> <Key> <模型名>` 添加你自己的模型

## 视觉功能（v1.0.5）

- **PMX 体素建筑**：解析 MMD 模型（PMX+OBJ）→ 逐体素 UV 纹理采样 + 法线着色 → Minecraft 彩色方块
- **逐体素纹理采样**：3D 重心坐标反推 UV，每个体素独立采样纹理像素（旧版整面同色）
- **超采样抗锯齿**：2×2×2 子采样点边缘平滑，大幅减少锯齿
- **PNG + TGA 纹理**：自动识别格式，PNG 通过 ImageIO 转换
- **CIELAB 颜色匹配**：107 种方块颜色数据库（混凝土/羊毛/陶瓦/玻璃/建筑方块），CIE76 色差最小匹配
- **浮点倍数**：`/ai 2.5` 支持小数倍率精细控制
- **异步构建**：ForkJoinPool 解析+体素化 → ServerTickEvent 分步放置
- 示例：`/ai build "C:\模型.pmx"` → 输入 `/ai 3` 确认 3 倍

## 配置

主配置文件：`config/xxsx_mc_smart_core-common.toml`

```toml
[ai]
provider_url = ""           # 留空使用内置 Agnes API
provider_key = ""
provider_model = ""

[chat]
session_persistence = true
context_max_tokens = 500000
context_compression = "truncate"  # 或 "summarize"

[build]
ask_clear = true            # 建造前询问清除区域
speed = -1                  # -1=使用 blocks_per_tick，或 /ai build speed 持久化
blocks_per_tick = 1000      # 20000 方块/秒
default_scale = 300

[world]
height_multiplier = 1       # 世界高度倍率提示；不单独修改旧世界高度

[safety]
confirm_state_changes = true # AI 改动玩家/世界/服务器前要求 /ai y 确认

[integration.nihility_terminal]
include_hotbar = false       # 默认不收入热键栏
skip_damaged_items = true
skip_enchanted_items = true
skip_named_items = true
protected_items = ""         # 额外保护的物品 ID，逗号分隔
```

## 依赖 / 版本对应

| Minecraft | Loader | Mod 版本 | 本轮状态 |
|---|---|---|---|
| MC 1.20.1 | Forge 47.2.0+ | v1.1.0 | 本轮未更新 |
| MC 1.21.11 | NeoForge 21.11.42 | v1.1.1 | 上一轮已发布，本轮未更新 |
| MC 26.2 | NeoForge 26.2.0.7-beta | v1.1.11 | 中英文 AI 回复、语言自适应知识摘要与运行时基础注册表查询 |

NeoForge 1.21.1 仅保留为本地试验模块，本轮未测试、未发布。

## 技术栈

- Java 17 / 21 / 25，按目标 MC 版本独立构建
- ForgeGradle 6.x / ModDevGradle 2.x，按子工程隔离
- OpenAI 兼容 API（Agnes / DeepSeek / 自定义）
- PMX 2.0 二进制解析
- JOML 向量数学
- CIELAB 色彩空间

## 开源协议

**AGPL-3.0** — GNU Affero General Public License v3.0

任何修改和衍生作品必须同样以 AGPL-3.0 开源，包括通过网络提供服务的场景。

## 当前状态 (2026-07-18)

v1.1.11-26.2 — MC 26.2 NeoForge 客户端辅助运行时注册表版，jar 文件名为 `xxsx-mc-smart-core-neoforge-26.2-1.1.11.jar`；模型会优先用 `[QUERY]registry <类型> <命名空间/关键词>` 或对应快捷写法扫描当前实例的物品、方块、实体、状态效果、药水、附魔、结构和生物群系精确 ID，再生成对应原版命令，不再先依赖联动文档或源码枚举目标。

上一轮 GitHub Release 为 v1.1.1-neoforge — NeoForge 客户端辅助专项更新（仅 MC 1.21.11 / MC 26.2）。

注意：这一轮没有更新 Forge 1.20.1；NeoForge 1.21.1 只保留为本地试验构建，未纳入已测试发布。

### 支持版本 / 发布矩阵

| 模块目录 | Loader | 目标版本 | Mod 版本 | 本轮状态 | 产物名 |
|---------|--------|----------|----------|----------|--------|
| `forge-1.20.1/` | Forge 47.2.0+ | MC 1.20.1 | v1.1.0 | 本轮未更新 | `xxsx-builder-forge-1.20.1-1.1.0.jar` |
| `neoforge-1.21.11/` | NeoForge 21.11.42 | MC 1.21.11 API 族 | v1.1.1 | 上一轮已发布，本轮未更新 | `xxsx-builder-neoforge-1.21.11-1.1.1.jar` |
| `neoforge-26.2/` | NeoForge 26.2.0.7-beta | MC 26.2 | v1.1.11 | 运行时基础注册表查询与双语模型回复，modid `xxsx_mc_smart_core` | `xxsx-mc-smart-core-neoforge-26.2-1.1.11.jar` |

### 项目结构

```text
xxsx-mc-smart-core/
├── common/                # 共享 Java 逻辑
├── forge-1.20.1/          # Forge 1.20.1 独立子工程
├── neoforge-1.21.11/      # NeoForge 1.21.11 独立子工程
├── neoforge-26.2/         # NeoForge 26.2 独立子工程
├── release-assets/        # GitHub Release 用的已验证 jar
└── .github/workflows/     # GitHub Actions 发布流水线
```

当前实践采用 **common + 独立版本子工程**：
- 共享逻辑放在 `common/src/main/java/`
- 每个版本独立维护 `gradlew` / `settings.gradle` / `build.gradle` / metadata
- 子工程通过 `sourceSets.main.java.srcDirs = ['src/main/java', '../common/src/main/java']` 引入共享源码
- 构建系统、资源入口、mod metadata 与版本特定 API 保持隔离，避免单 `build.gradle` 强兼容

### 发布方式

- GitHub `v*` tag 自动触发 `.github/workflows/release.yml`，用于完整发布矩阵
- GitHub `neoforge-v*` tag 自动触发 `.github/workflows/release-neoforge.yml`，从 `release-assets/<tag>/` 上传已本地构建验证的 NeoForge 1.21.11 / 26.2 客户端 jar
- 完整发布 workflow 分三套 JDK 构建：
  - Forge 1.20.1 → JDK 17
  - NeoForge 1.21.11 → JDK 21
  - NeoForge 26.2 → JDK 25
- 三个构建产物会在 release job 中汇总上传为同一个 GitHub Release 附件
- NeoForge 专项 workflow 只上传两个已测试 NeoForge 产物，不包含 Forge 1.20.1 和本地试验的 NeoForge 1.21.1
- 本地批量构建可使用 `build-all.sh`

### 发布验证闭环

1. 在对应模块执行 `./gradlew build --no-daemon --offline`
2. 确认目标 jar 出现在各自 `build/libs/`
3. 部署到目标实例 `mods/` 目录
4. 启动游戏并检查日志无关键报错
5. 执行关键命令 / 核心功能 smoke test（如 `/ai`、`/ai build`、`[KNOWLEDGE]`）

已实现：
- `/ai` 自然语言 Agent 循环，AI 自主决策执行指令并反馈结果
- `[QUERY]` 游戏状态查询（player/block/item/recipe/nearby/world/mods/file/pmx）和运行时注册表查询（items/blocks/entities/effects/potions/enchantments/structures/biomes）
- `[QUERY]commands` 联动动作目录，覆盖 Curios、虚无终端、Earth Human、地球工业、魔法、玄幻与 JEI 导航
- `[KNOWLEDGE]` 知识库（jar 内置 ftbquests/bloodmagic，外置可扩展）
- `[CMD]` 指令自动执行，状态变更命令等待 `/ai y` 或 `/ai confirm` 后执行
- 虚无终端一键存入默认保护热键栏、耐久/附魔/自定义名称物品，并支持额外物品 ID 保护列表
- PMX 体素建筑：逐体素 UV 纹理采样 + 超采样抗锯齿 + CIELAB 107 色匹配
- PNG + TGA 纹理自动识别加载
- 浮点倍数 `/ai 2.5`，脚底对齐放置
- `/ai build speed` 持久化速度控制
- 模型切换 `/ai api` + `/ai model` + `/ai addmodel`
- 会话持久化 + 上下文自动压缩
- 聊天折叠显示（悬停全文/点击复制）

待优化：
- 多纹理混合（toon/sphere 贴图）
- 更丰富的光照模型（环境光遮蔽）

## 作者

xxsx

## 知识库

内置知识库会随各版本模块一起打包，外置覆盖路径仍为 `config/xxsx_mc_smart_core/knowledge/`。

当前内置资源目录：
- `forge-1.20.1/src/main/resources/knowledge/`
- `neoforge-1.21.11/src/main/resources/knowledge/`
- `neoforge-26.2/src/main/resources/knowledge/`

外置覆盖目录：
- `config/xxsx_mc_smart_core/knowledge/`

```text
knowledge/
├── ftbquests/      # FTB Quests 任务系统
├── bloodmagic/     # Blood Magic 血魔法
└── ...             # 自行添加更多模组
```

维护约定：
- 新增共享 Java 逻辑优先放 `common/`
- 新增内置知识库内容时，要同步三个目标版本模块的 `src/main/resources/knowledge/`
- 用户自定义/扩展文档优先放外置 `config/xxsx_mc_smart_core/knowledge/`

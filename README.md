# xxsx-mc-smart-core

xxsx的智能核心 — Minecraft 1.20.1 Forge AI 助手模组。

## 功能

| 功能 | 说明 |
|------|------|
| `/ai <消息>` | 自然语言 AI 对话，自动执行指令 |
| `/ai build <PMX路径>` | 解析 PMX 模型 → 体素建筑，`/ai <倍数>` 确认比例 |
| `/ai api/model` | 切换 AI 供应商和模型 |
| `/ai addmodel` | 添加个人 API 密钥 |
| `[CMD]` 标签 | AI 输出指令自动执行并反馈结果 |
| `[QUERY]` 标签 | 查询游戏状态（物品/方块/实体/配方/世界） |
| `[KNOWLEDGE]` 标签 | 查询知识库（支持 jar 内置 + 外置扩展） |
| 会话持久化 | 同一玩家重连保留对话历史 |
| 上下文压缩 | 超限自动截断或 AI 总结 |
| PlayerConfig | 每玩家独立 API 配置 |

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

主配置文件：`config/xxsx_builder-common.toml`

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
```

## 依赖

- Minecraft 1.20.1
- Forge 47.2.0+

## 技术栈

- Java 17
- ForgeGradle 6.x / Gradle 8.8
- OpenAI 兼容 API（Agnes / DeepSeek / 自定义）
- PMX 2.0 二进制解析
- JOML 向量数学
- CIELAB 色彩空间

## 开源协议

**AGPL-3.0** — GNU Affero General Public License v3.0

任何修改和衍生作品必须同样以 AGPL-3.0 开源，包括通过网络提供服务的场景。

## 当前状态 (2026-07-05)

v1.1.0 — 多版本发布同步更新（Forge 1.20.1 / NeoForge 1.21.11 / NeoForge 26.2）。

### 支持版本 / 发布矩阵

| 模块目录 | Loader | 目标版本 | JDK | 产物名 |
|---------|--------|----------|-----|--------|
| `forge-1.20.1/` | Forge 47.2.0+ | MC 1.20.1 | 17 | `xxsx-builder-forge-1.20.1-1.1.0.jar` |
| `neoforge-1.21.11/` | NeoForge 21.11.42 | MC 1.21.11 API 族 | 21 | `xxsx-builder-neoforge-1.21.11-1.1.0.jar` |
| `neoforge-26.2/` | NeoForge 26.2.0.7-beta | MC 26.2 | 25 | `xxsx-builder-neoforge-26.2-1.1.0.jar` |

### 项目结构

```text
xxsx_builder/
├── common/                # 共享 Java 逻辑
├── forge-1.20.1/          # Forge 1.20.1 独立子工程
├── neoforge-1.21.11/      # NeoForge 1.21.11 独立子工程
├── neoforge-26.2/         # NeoForge 26.2 独立子工程
└── .github/workflows/     # GitHub Actions 发布流水线
```

当前实践采用 **common + 独立版本子工程**：
- 共享逻辑放在 `common/src/main/java/`
- 每个版本独立维护 `gradlew` / `settings.gradle` / `build.gradle` / metadata
- 子工程通过 `sourceSets.main.java.srcDirs = ['src/main/java', '../common/src/main/java']` 引入共享源码
- 构建系统、资源入口、mod metadata 与版本特定 API 保持隔离，避免单 `build.gradle` 强兼容

### 发布方式

- GitHub `v*` tag 自动触发 `.github/workflows/release.yml`
- workflow 分三套 JDK 构建：
  - Forge 1.20.1 → JDK 17
  - NeoForge 1.21.11 → JDK 21
  - NeoForge 26.2 → JDK 25
- 三个构建产物会在 release job 中汇总上传为同一个 GitHub Release 附件
- 本地批量构建可使用 `build-all.sh`

### 发布验证闭环

1. 在对应模块执行 `./gradlew build --no-daemon --offline`
2. 确认目标 jar 出现在各自 `build/libs/`
3. 部署到目标实例 `mods/` 目录
4. 启动游戏并检查日志无关键报错
5. 执行关键命令 / 核心功能 smoke test（如 `/ai`、`/ai build`、`[KNOWLEDGE]`）

已实现：
- `/ai` 自然语言 Agent 循环，AI 自主决策执行指令并反馈结果
- `[QUERY]` 游戏状态查询（player/block/item/recipe/nearby/world/mods/file/pmx）
- `[KNOWLEDGE]` 知识库（jar 内置 ftbquests/bloodmagic，外置可扩展）
- `[CMD]` 指令自动执行，错误反馈给 AI
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

内置知识库会随各版本模块一起打包，外置覆盖路径仍为 `config/xxsx_builder/knowledge/`。

当前内置资源目录：
- `forge-1.20.1/src/main/resources/knowledge/`
- `neoforge-1.21.11/src/main/resources/knowledge/`
- `neoforge-26.2/src/main/resources/knowledge/`

外置覆盖目录：
- `config/xxsx_builder/knowledge/`

```text
knowledge/
├── ftbquests/      # FTB Quests 任务系统
├── bloodmagic/     # Blood Magic 血魔法
└── ...             # 自行添加更多模组
```

维护约定：
- 新增共享 Java 逻辑优先放 `common/`
- 新增内置知识库内容时，要同步三个目标版本模块的 `src/main/resources/knowledge/`
- 用户自定义/扩展文档优先放外置 `config/xxsx_builder/knowledge/`

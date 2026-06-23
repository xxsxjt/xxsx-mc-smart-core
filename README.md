# xxsx-mc-smart-core

xxsx的智能核心 — Minecraft 1.20.1 Forge AI 助手模组。

## 功能

| 功能 | 说明 |
|------|------|
| `/ai <消息>` | 自然语言 AI 对话，自动执行指令 |
| `/ai build <PMX路径> [比例]` | 解析 PMX 模型生成体素建筑 |
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

## 视觉功能（v1.0.0 预览实验版）

- **PMX 体素建筑**：读取 MMD 模型的顶点/面/材质颜色，通过表面体素化+法线着色转换为 Minecraft 彩色方块建筑
- **CIELAB 颜色匹配**：RGB→LAB→80 种方块颜色数据库（混凝土/羊毛/陶瓦/玻璃/带釉陶瓦），CIE76 色差最小匹配
- **异步构建**：ForkJoinPool 解析+体素化 → ServerTickEvent 分步放置（避免卡服）
- 示例：`/ai build "C:\模型.pmx" 150`

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
default_scale = 150
blocks_per_tick = 200
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

## 作者

xxsx

## 知识库

内置知识库位于 `resources/knowledge/`（jar 内），外置覆盖路径 `config/xxsx_builder/knowledge/`。

```
knowledge/
├── ftbquests/      # FTB Quests 任务系统
├── bloodmagic/     # Blood Magic 血魔法
└── ...             # 自行添加更多模组
```

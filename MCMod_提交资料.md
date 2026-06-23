# MCMod 提交资料 — xxsx的智能核心

## 基本信息

| 字段 | 内容 |
|------|------|
| **主要名称** | xxsx的智能核心 |
| **英文名** | xxsx's Smart Core |
| **作者** | xxsx（虚无圣熙） |
| **运作方式** | Forge |
| **支持平台** | JAVA版 (JAVA Edition) |
| **支持MC版本** | 1.20.1 |
| **模组元素**（核心） | 能源 / 存储 / 指南 / 指令 |
| **模组元素**（杂项） | 中式、国创 |

## 简介

xxsx的智能核心是一个 AI 驱动的 Minecraft 模组助手。玩家可以通过自然语言与 AI 对话，AI 会自动分析意图并执行对应的 Minecraft 指令。核心特色功能包括：

- **自然语言操控**：`/ai <消息>` 即可与 AI 自由对话，AI 自主判断使用什么指令
- **指令执行**：AI 通过 `[CMD]` 标签输出指令，服务端自动执行并将结果反馈给 AI
- **PMX 体素建筑**：读取 MMD 模型文件，通过表面体素化 + CIELAB 颜色匹配转换为 Minecraft 彩色方块建筑
- **知识库系统**：内置模组知识库（FTB Quests / Blood Magic 等），AI 可自动查询；支持外置扩展
- **多模型切换**：支持 Agnes / DeepSeek / 自定义 API，游戏内一键切换
- **个人 API 配置**：每玩家独立 API Key，不影响他人

## 详细描述

### AI 对话与指令执行

玩家输入 `/ai 给我一把时运3的钻石镐`，AI 理解意图后回复并输出：
```
[CMD] /give @s minecraft:diamond_pickaxe{Enchantments:[{id:"minecraft:fortune",lvl:3s}]} 1 [/CMD]
```
服务端自动执行指令，结果反馈给 AI。AI 可多轮迭代完成复杂任务。

### PMX 体素建筑

玩家输入 `/ai build "模型文件.pmx" 150` 即可将 MMD 模型（47k+ 顶点、61k+ 三角形）转换为 Minecraft 建筑：

1. 二进制解析 PMX 2.0 格式（顶点/法线/UV/面/材质/纹理）
2. 表面体素化：三角形 AABB → 重心坐标点测试 → 填充正方体
3. 法线着色：三角面法线方向调制颜色明暗（0.6~1.0），不同朝向呈现色差
4. CIELAB 颜色匹配：RGB→XYZ→LAB + 80 种方块颜色数据库 → CIE76 最小色差 → 选择最优方块
5. 异步构建：ForkJoinPool 解析 + ServerTickEvent 分步放置（200~2000 方块/tick）

### 技术栈
- Java 17 + Forge 47.2.0
- ForgeGradle 6.x / Gradle 8.8
- OpenAI 兼容 API
- JOML 向量数学 / CIELAB 色彩空间

## 相关链接

| 类型 | 链接 | 备注 |
|------|------|------|
| GitHub | （待创建） | 源码 + 发布 |
| Agnes AI | https://apihub.agnes-ai.com | 内置 API 供应商 |
| DeepSeek | https://api.deepseek.com | 可选 API 供应商 |

## 封面

（待上传 720x450 截图）

## 开源协议

AGPL-3.0（GNU Affero General Public License v3.0）
强制衍生作品同样开源，包括网络服务场景。

## 改动附言

首次提交。模组已完成 v1.0.0 开发，可在 1.20.1 Forge 环境稳定运行。

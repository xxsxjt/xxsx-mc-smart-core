# Xxsx Smart Core - MC 26.2 Runtime Registry Update

本次发布只更新并验证以下 Minecraft 版本：

| Minecraft | Loader | Mod 版本 | Jar |
|---|---|---|---|
| MC 26.2 | NeoForge 26.2.0.7-beta | v1.1.11 | `xxsx-mc-smart-core-neoforge-26.2-1.1.11.jar` |

未包含：
- Forge 1.20.1：本轮未更新，仍使用上一轮 `v1.1.0` 产物。
- NeoForge 1.21.11：本轮未更新，仍使用上一轮 `v1.1.1` 产物。
- NeoForge 1.21.1：仅本地试验模块，未纳入本次已测试发布。
- Forge 26.x：仅本地试验模块，未纳入本次已测试发布。

主要变化：
- 新增统一 `[QUERY]registry <类型> [命名空间/关键词]` 和类别快捷写法：直接枚举当前实例运行时注册的物品、方块、实体、状态效果、药水、附魔、结构和生物群系 ID。模型为 `/give`、`/setblock`、`/summon`、`/effect`、附魔组件和 `/locate` 发现目标时，先查注册表；只有需要行为、掉落、配方或玩法细节时才读取联动文档或源码。
- AI 回复按 Minecraft 游戏语言自动切换：简体中文使用中文 system prompt，英文语言使用完整英文 system prompt；语言切换后会自动重建客户端 provider，保留会话历史。
- `[QUERY]summarize` 的模型生成知识摘要同样跟随游戏语言；内置查询、确认、建造和虚无终端固定反馈已补充 `zh_cn` / `en_us` 资源。
- 新增可扩展的联动动作目录：`[QUERY]commands ...[/QUERY]` 现在按饰品、虚无仓储、生存状态、地质工业、超凡状态和 JEI 导航分类查询。
- 修正自研生态识别：检测实际 modid `earth_on_minecraft`、`earth_human`、`earth_online_magic`、`earth_online_xuanhuan`，不再把不存在的 `earth_online` 当作唯一入口。
- AI 发出的状态变更命令不再直接执行：会返回 `confirmation_required=true` 并等待 `/ai y` 或 `/ai confirm`；`/ai n`、`/ai cancel`、`/ai stop` 可取消。查询和打开原生终端等安全操作仍可直接完成。
- 虚无终端一键存入默认保护热键栏、终端本体、耐久物品、附魔物品和自定义名称物品；可通过 `integration.nihility_terminal.protected_items` 追加物品 ID，只有明确配置后才会包含热键栏。
- 修正 26.2 身份命名：modid 改为合法下划线形态 `xxsx_mc_smart_core`，发布 jar 按仓库名使用 `xxsx-mc-smart-core` 前缀。
- 收窄背包/容器按钮：移除通用整理、容器到背包、背包到容器功能，仅保留虚无终端联动的一键存入入口。
- 与虚无圣殿的虚无终端联动：仅在 Curios 佩戴虚无终端时，在背包/容器界面提供一键存入入口；虚无终端自身界面不显示智能核心按钮。
- 增加 AI 工具结果自动回灌，让模型根据 `[TOOL_RESULT]` 自行判断是否继续。
- 增加便携分类指令库：`[QUERY]commands ...[/QUERY]` / `[QUERY]curios ...[/QUERY]`。
- 修复打开原版背包崩溃：26.2 的 `InventoryMenu.getType()` 会抛 `UnsupportedOperationException`，现在已安全绕过。
- 新增 `world.height_multiplier` 配置项与设置面板入口；这是供 Earth on Minecraft 等兼容模组/新世界预设读取的高度倍率提示，不会单独修改旧世界高度。
- 继续保持纯客户端辅助定位；专用服务端安装无效。

验证：
- 本地 `neoforge-26.2` 模块构建成功。
- 已部署到 MC 26.2 / NeoForge 26.2.0.7-beta 实例并确认只有一个 active 智能核心 jar。
- 本地构建 jar 与部署 jar SHA256 一致：`C36A0C86731000CF10A8CBCB813617A427F9F27D939B9800A3FEBFD662E809E0`。
- 部署发生在最近一次客户端日志之后，需要重启 MC 26.2 客户端后完成运行时 smoke test。

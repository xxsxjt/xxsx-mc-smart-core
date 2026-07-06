# Xxsx Smart Core - MC 26.2 Hotfix

本次发布只更新并验证以下 Minecraft 版本：

| Minecraft | Loader | Mod 版本 | Jar |
|---|---|---|---|
| MC 26.2 | NeoForge 26.2.0.7-beta | v1.1.5 | `xxsx-builder-neoforge-26.2-1.1.5.jar` |

未包含：
- Forge 1.20.1：本轮未更新，仍使用上一轮 `v1.1.0` 产物。
- NeoForge 1.21.11：本轮未更新，仍使用上一轮 `v1.1.1` 产物。
- NeoForge 1.21.1：仅本地试验模块，未纳入本次已测试发布。
- Forge 26.x：仅本地试验模块，未纳入本次已测试发布。

主要变化：
- 新增背包/容器右上角辅助按钮：整理、容器到背包、背包到容器。
- 与虚无圣殿的虚无终端联动：携带或佩戴终端时提供一键收入入口。
- 增加 AI 工具结果自动回灌，让模型根据 `[TOOL_RESULT]` 自行判断是否继续。
- 增加便携分类指令库：`[QUERY]commands ...[/QUERY]` / `[QUERY]curios ...[/QUERY]`。
- 修复打开原版背包崩溃：26.2 的 `InventoryMenu.getType()` 会抛 `UnsupportedOperationException`，现在已安全绕过。
- 继续保持纯客户端辅助定位；专用服务端安装无效。

验证：
- 本地 `neoforge-26.2` 模块构建成功。
- 已部署到 MC 26.2 / NeoForge 26.2.0.7-beta 实例并确认只有一个 active 智能核心 jar。
- 本地构建 jar、部署 jar 与 release asset SHA256 一致：`125B94144384986447A1BB459CCD7DCAA47EE20C28016534A916E23545A50081`。

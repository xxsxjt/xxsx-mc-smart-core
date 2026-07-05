# FTB Quests SNBT 格式规范

## 文件位置
- 任务数据: `config/ftbquests/quests/`
- 分组定义: `config/ftbquests/quests/chapter_groups.snbt`
- 章节文件: `config/ftbquests/quests/chapters/<name>.snbt`
- 全局设置: `config/ftbquests/quests/data.snbt`

## 指令
- `/ftbquests reload` — 重载任务文件
- `/ftbquests open` — 打开任务书
- `/ftbquests editing_mode` — 切换编辑模式
- `/ftbquests block_reward` — 设置任务奖励方块

## 致命错误（会导致任务书全部加载失败）
1. **BOM**: 文件必须 UTF-8 无 BOM
2. **ID 格式**: 所有 ID 必须用 16 字节大写 hex，首字符 0-7（≤7FFFFFFFFFFFFFFF）。Python 用 `getrandbits(63)` 而非 64
3. **缺失字段**: 章节文件缺任何必需字段都会导致整个任务书变空
4. **加载失败会回写空壳**: reload 失败时 FTB Quests 会回写所有章节为空壳（丢失 title/icon/description）

## chapter_groups.snbt
```snbt
{
    chapter_groups: [
        { id: "HEX_ID_16位大写", title: "§x✦ 分组名" }
    ]
}
```

## 章节必需字段
```snbt
{
    default_hide_dependency_lines: false
    default_quest_shape: "circle"
    filename: "文件名(不含.snbt)"
    group: "HEX_ID16位(对应chapter_groups)"
    id: "HEX_ID16位"
    order_index: 0
    quest_links: [ ]
    title: "章节名"
    quests: [ ... ]
}
```

## Quest 格式
```snbt
{
    id: "HEX_ID16位"
    title: "任务名"
    subtitle: "副标题"           // 可选
    description: ["描述行1", "行2"]
    icon: {id: "modid:itemid"}  // 必须有
    x: 0.0d  y: 0.0d
    dependencies: ["前置Quest的HEX_ID"]
    tasks: [{id: "TASK_HEX_ID", type: "item", item: "modid:itemid", count: 1}]
    rewards: [{id: "REWARD_HEX_ID", type: "item", item: "modid:itemid", count: 1}]
}
```

## 常用任务类型
- `type: "item"` — 提交物品
- `type: "dimension"` — 到达维度
- `type: "kill"` — 击杀实体
- `type: "checkmark"` — 手动勾选
- `type: "xp"` — 经验等级

## 常用奖励类型
- `type: "item"` — 物品奖励
- `type: "command"` — 执行指令
- `type: "xp"` — 经验奖励
- `type: "xp_levels"` — 经验等级
- `type: "random"` — 随机奖励
- `type: "choice"` — 选择奖励

## 当前整合包章节结构
- 根目录：序章-植物魔法, 仓储（独立标签页）
- 发展线：植物魔法→自然灵气→机械动力→新生魔艺→血魔法→魔法艺术→铁魔法→黑暗仪式→神秘学→神化→无尽贪婪→神秘辣条→更多→冰火传说
- 探索线：维度巡礼→Boss图鉴→装备与武器→饰品→魂石
- 终局：终焉→永恒之门→深渊之座→终焉之证
- 法术修炼：铁魔法学校体系

## 注意事项
- **不要在游戏运行时改文件**，先关游戏再改
- 备份优先：`ftbquests_backup_*` 目录有历史备份
- Python 脚本写中文路径文件用 `WriteAllBytes`，`WriteAllText` 会静默失败

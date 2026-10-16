package com.xxsx.builder.ai;

import com.xxsx.builder.config.SmartCoreText;

import java.util.List;
import java.util.Locale;

/**
 * Single source of truth for optional 26.2 integrations and AI-visible actions.
 * It deliberately describes capabilities without creating compile-time mod dependencies.
 */
public final class IntegrationCatalog {
    public enum CommandRisk {
        SAFE("risk.safe"),
        CONFIRM("risk.confirm");

        private final String translationKey;

        CommandRisk(String translationKey) {
            this.translationKey = translationKey;
        }

        public String description() {
            return queryText(translationKey);
        }
    }

    public record IntegrationDef(String modId, String topic, String name, String role, List<String> aliases) {
        public String displayName() {
            return queryText("integration." + modId + ".name");
        }

        public String displayRole() {
            return queryText("integration." + modId + ".role");
        }

        public boolean matches(String query) {
            String normalized = normalize(query);
            return normalized.isEmpty()
                || modId.equals(normalized)
                || topic.equals(normalized)
                || name.toLowerCase(Locale.ROOT).contains(normalized)
                || normalized.contains(modId)
                || normalized.contains(topic)
                || aliases.stream().anyMatch(alias -> normalized.contains(alias) || alias.contains(normalized));
        }
    }

    public record ActionDef(String id, String category, String modId, String label, String commandTemplate,
                            String queryHint, CommandRisk risk, List<String> aliases) {
        public String displayCategory() {
            return queryText("category." + categoryKey(category));
        }

        public String displayLabel() {
            return queryText("action." + id + ".label");
        }

        public String displayQueryHint() {
            return queryText("action." + id + ".hint");
        }

        public boolean matches(String query) {
            String normalized = normalize(query);
            return normalized.isEmpty()
                || id.contains(normalized)
                || normalized.contains(id)
                || category.toLowerCase(Locale.ROOT).contains(normalized)
                || normalized.contains(category.toLowerCase(Locale.ROOT))
                || modId.contains(normalized)
                || normalized.contains(modId)
                || label.toLowerCase(Locale.ROOT).contains(normalized)
                || normalized.contains(label.toLowerCase(Locale.ROOT))
                || aliases.stream().anyMatch(alias -> normalized.contains(alias) || alias.contains(normalized));
        }
    }

    private static final List<IntegrationDef> INTEGRATIONS = List.of(
        new IntegrationDef("jei", "jei", "JEI 物品管理器", "配方/用途提示，配合 [QUERY]recipe 和 [QUERY]jei 使用", List.of("recipe", "配方", "用途")),
        new IntegrationDef("justenoughprofessions", "jei", "JEI 工作方块", "村民职业与工作方块提示", List.of("职业", "工作方块")),
        new IntegrationDef("jade", "jade", "玉 Jade", "准星方块/实体识别，辅助建筑定位和调试", List.of("玉", "准星", "方块信息")),
        new IntegrationDef("appleskin", "appleskin", "AppleSkin", "食物营养/饱和度提示，补充 [QUERY]item 食物信息", List.of("食物", "饱和度")),
        new IntegrationDef("waystones", "waystones", "Waystones", "传送点/旅行网络规划", List.of("传送石碑", "旅行")),
        new IntegrationDef("naturescompass", "compasses", "Nature's Compass", "生物群系定位建议", List.of("生物群系", "自然罗盘")),
        new IntegrationDef("explorerscompass", "compasses", "Explorer's Compass", "结构定位建议", List.of("结构", "探险者罗盘")),
        new IntegrationDef("sophisticatedbackpacks", "sophisticated", "Sophisticated Backpacks", "建筑材料随身携带与升级规划", List.of("精妙背包", "背包")),
        new IntegrationDef("sophisticatedstorage", "sophisticated", "Sophisticated Storage", "大规模材料仓储规划", List.of("精妙存储", "仓储")),
        new IntegrationDef("curios", "curios", "Curios", "饰品槽位和装备类模组识别", List.of("饰品", "饰品栏", "戒指")),
        new IntegrationDef("xaerominimap", "xaero", "Xaero's Minimap", "建筑点位、路径和地形导航", List.of("小地图", "xaero")),
        new IntegrationDef("xaeroworldmap", "xaero", "Xaero's World Map", "区域规划与大地图标记", List.of("世界地图", "xaero")),
        new IntegrationDef("enchdesc", "enchanting", "Enchantment Descriptions", "附魔说明", List.of("附魔")),
        new IntegrationDef("enchantmentinsights", "enchanting", "Enchantment Insights", "附魔台预览", List.of("附魔")),
        new IntegrationDef("easymagic", "enchanting", "Easy Magic", "附魔台便捷操作", List.of("附魔")),
        new IntegrationDef("taxfreelevels", "enchanting", "Tax Free Levels", "经验等级消耗优化", List.of("附魔", "经验")),
        new IntegrationDef("legendarytooltips", "clientui", "Legendary Tooltips", "物品提示框增强", List.of("提示框")),
        new IntegrationDef("controlling", "clientui", "Controlling", "键位冲突查询", List.of("键位", "冲突")),
        new IntegrationDef("mousetweaks", "clientui", "Mouse Tweaks", "库存操作优化", List.of("鼠标", "背包")),
        new IntegrationDef("lootr", "exploration", "Lootr", "探索战利品箱识别", List.of("战利品")),
        new IntegrationDef("shogi", "exploration", "Shogi", "可作为建筑/装饰主题参考", List.of("将棋", "装饰")),
        new IntegrationDef("earth_on_minecraft", "projectmods", "Earth on Minecraft / 我的地球", "现实地质、工业材料和机器路线", List.of("我的地球", "地球工业", "earth")),
        new IntegrationDef("earth_human", "projectmods", "Earth Human / 地球：人类", "身体、生存压力和护理状态", List.of("地球人类", "人体", "疲劳")),
        new IntegrationDef("earth_online_magic", "projectmods", "Earth Online: Magic", "奥术场、法力和魔法设施", List.of("魔幻", "奥术", "法力")),
        new IntegrationDef("earth_online_xuanhuan", "projectmods", "地球 Online：玄幻", "灵气场、修行和玄幻设施", List.of("玄幻", "灵气", "修行")),
        new IntegrationDef("templenihility", "projectmods", "Temple of Nihility / 虚无圣殿", "虚无终端、宝库和 Curios 饰品", List.of("虚无", "终端", "宝库"))
    );

    private static final List<ActionDef> ACTIONS = List.of(
        new ActionDef("curios.list", "饰品与装备", "curios", "列出 Curios 槽位来源", "curios list", "[QUERY]commands slots[/QUERY]", CommandRisk.SAFE, List.of("slots", "槽位", "饰品栏", "list", "列表")),
        new ActionDef("curios.add", "饰品与装备", "curios", "增加 Curios 槽位", "curios add <slot> @s [amount]", "[QUERY]commands add 饰品栏[/QUERY]", CommandRisk.CONFIRM, List.of("add", "添加", "增加", "扩容", "加", "戒指")),
        new ActionDef("curios.set", "饰品与装备", "curios", "设置 Curios 槽位数量", "curios set <slot> @s [amount]", "[QUERY]commands set ring 2[/QUERY]", CommandRisk.CONFIRM, List.of("set", "设置", "设为")),
        new ActionDef("curios.remove", "饰品与装备", "curios", "减少或清理 Curios 槽位", "curios remove <slot> @s [amount]", "[QUERY]curios remove ring[/QUERY]", CommandRisk.CONFIRM, List.of("remove", "减少", "清空", "掉落", "重置")),
        new ActionDef("nihility.open_terminal", "虚无仓储", "templenihility", "打开已绑定的虚无终端", "nihility terminal", "[QUERY]commands nihility[/QUERY]", CommandRisk.SAFE, List.of("nihility", "虚无", "终端", "宝库")),
        new ActionDef("nihility.safe_store", "虚无仓储", "templenihility", "虚无终端安全存入", null, "在背包或容器界面点击 ◆；仅 Curios 已佩戴终端时出现", CommandRisk.SAFE, List.of("存入", "收纳", "收入", "背包整理")),
        new ActionDef("earth_human.status", "生存状态", "earth_human", "查看 Earth Human 身体状态", "eh status", "[QUERY]mod earth_human[/QUERY]", CommandRisk.SAFE, List.of("earthhuman", "人体", "身体状态", "疲劳", "水分")),
        new ActionDef("earth_human.training", "生存状态", "earth_human", "查看 Earth Human 训练状态", "eh training", "[QUERY]mod earth_human[/QUERY]", CommandRisk.SAFE, List.of("训练", "锻炼")),
        new ActionDef("earth_on_minecraft.route", "地质工业", "earth_on_minecraft", "查询我的地球材料加工路线", null, "[QUERY]recipe <物品ID>[/QUERY] 或 [QUERY]doc projectmods[/QUERY]", CommandRisk.SAFE, List.of("earth_on_minecraft", "我的地球", "工业", "材料路线", "机器")),
        new ActionDef("earth_online_magic.status", "超凡状态", "earth_online_magic", "查看魔法路线联动状态", null, "[QUERY]mod earth_online_magic[/QUERY]", CommandRisk.SAFE, List.of("magic", "魔幻", "奥术", "aether")),
        new ActionDef("earth_online_xuanhuan.status", "超凡状态", "earth_online_xuanhuan", "查看玄幻路线联动状态", null, "[QUERY]mod earth_online_xuanhuan[/QUERY]", CommandRisk.SAFE, List.of("xuanhuan", "玄幻", "灵气", "修行")),
        new ActionDef("jei.recipe", "配方导航", "jei", "查询 JEI 配方与用途", null, "[QUERY]jei <物品ID>[/QUERY]", CommandRisk.SAFE, List.of("jei", "配方", "用途", "合成", "recipe", "uses"))
    );

    private IntegrationCatalog() {
    }

    private static String queryText(String key) {
        return SmartCoreText.text("query.xxsx_mc_smart_core." + key);
    }

    private static String categoryKey(String category) {
        return switch (category) {
            case "饰品与装备" -> "curios";
            case "虚无仓储" -> "nihility";
            case "生存状态" -> "survival";
            case "地质工业" -> "industry";
            case "超凡状态" -> "supernatural";
            case "配方导航" -> "recipes";
            default -> "other";
        };
    }

    public static List<IntegrationDef> integrations() {
        return INTEGRATIONS;
    }

    public static List<ActionDef> actions() {
        return ACTIONS;
    }

    public static List<ActionDef> findActions(String query) {
        return ACTIONS.stream().filter(action -> action.matches(query)).toList();
    }

    public static CommandRisk riskForCommand(String command) {
        String normalized = normalizeCommand(command);
        if (startsWithCommand(normalized, "curios list")
            || startsWithCommand(normalized, "nihility terminal")
            || startsWithCommand(normalized, "eh status")
            || startsWithCommand(normalized, "eh training")
            || startsWithCommand(normalized, "earthhuman status")
            || startsWithCommand(normalized, "earthhuman training")) {
            return CommandRisk.SAFE;
        }
        return CommandRisk.CONFIRM;
    }

    private static boolean startsWithCommand(String command, String prefix) {
        return command.equals(prefix) || command.startsWith(prefix + " ");
    }

    private static String normalizeCommand(String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("/") ? normalized.substring(1).trim() : normalized;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}

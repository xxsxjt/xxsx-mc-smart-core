package com.xxsx.builder.ai;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.config.BuilderConfig;
import com.xxsx.builder.config.ModLogger;
import com.mojang.brigadier.context.CommandContextBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CommandExecutor {
    private static final Pattern CMD_PATTERN = Pattern.compile("\\[CMD\\](.*?)\\[/CMD\\]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_LINE = Pattern.compile("^\\s*/(\\w+.*)$", Pattern.MULTILINE);
    private static final Pattern QUERY_PATTERN = Pattern.compile("\\[QUERY\\](.*?)\\[/QUERY\\]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private final MinecraftServer server;
    private final BuilderConfig config;
    private Set<String> blacklist;

    public CommandExecutor(MinecraftServer server, BuilderConfig config) {
        this.server = server;
        this.config = config;
        updateBlacklist();
    }

    public void updateBlacklist() {
        blacklist = new HashSet<>(Arrays.asList(config.commandBlacklist.split(",")));
    }

    public static String getPlayerName(CommandSourceStack source) {
        try {
            return source.getEntity() != null ? source.getEntity().getName().getString() : "SERVER";
        } catch (Exception e) { return "UNKNOWN"; }
    }

    public static String getPlayerPos(CommandSourceStack source) {
        var pos = source.getPosition();
        return String.format("%.1f %.1f %.1f", pos.x, pos.y, pos.z);
    }

    /**
     * 从 AI 回复提取并执行指令，同时处理 [QUERY] 查询。
     * 返回喂回 AI 的完整反馈。
     */
    public String executeCommands(String aiResponse, CommandSourceStack source) {
        if (aiResponse == null || aiResponse.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        int count = 0;

        // 0. 先处理 [QUERY] 查询
        String afterQuery = processQueries(aiResponse, source, result);

        // 1. [CMD] 标签
        Matcher m = CMD_PATTERN.matcher(afterQuery);
        while (m.find()) {
            String cmd = m.group(1).trim();
            if (cmd.isEmpty()) continue;
            if (executeSingle(cmd, source, result)) count++;
        }

        // 2. 裸 /指令行 fallback
        if (count == 0) {
            Matcher lineMatcher = COMMAND_LINE.matcher(afterQuery);
            while (lineMatcher.find()) {
                String cmd = "/" + lineMatcher.group(1).trim();
                if (cmd.isEmpty()) continue;
                if (executeSingle(cmd, source, result)) count++;
            }
        }

        if (count == 0 && result.isEmpty()) return "";
        return result.toString();
    }

    /** 处理 [QUERY] 标签：读文件/查方块/查实体 */
    private String processQueries(String text, CommandSourceStack source, StringBuilder result) {
        Matcher m = QUERY_PATTERN.matcher(text);
        while (m.find()) {
            String q = m.group(1).trim();
            String response = doQuery(q, source);
            if (response != null && !response.isEmpty()) {
                result.append("\n【查询结果】").append(response);
            }
        }
        return m.replaceAll(""); // 去掉 [QUERY] 块，避免干扰
    }

    /** 执行查询 */
    private String doQuery(String query, CommandSourceStack source) {
        String[] parts = query.split("\\s+", 3);
        if (parts.length == 0) return "无效查询";

        String type = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";
        String extra = parts.length > 2 ? parts[2] : "";

        try {
            switch (type) {
                case "file" -> {
                    return readFile(arg);
                }
                case "dir" -> {
                    return listDir(arg);
                }
                case "block" -> {
                    return queryBlock(source, arg);
                }
                case "nearby" -> {
                    return queryNearby(source, arg.isEmpty() ? "10" : arg);
                }
                case "player" -> {
                    return queryPlayer(source);
                }
                case "mods" -> {
                    return queryMods();
                }
                case "world" -> {
                    return queryWorld(source);
                }
                case "item" -> {
                    return queryItem(arg);
                }
                case "pmx" -> {
                    return queryPMX(arg);
                }
                case "recipe" -> {
                    return queryRecipes(arg);
                }
                case "help" -> {
                    return "可用查询: player, item <id>, block x y z, nearby <半径>, world, file <路径>, dir <目录>, mods, recipe <物品id>";
                }
                default -> {
                    return "未知查询类型: " + type + "，输入 [QUERY]help[/QUERY] 查看可用查询";
                }
            }
        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    private String readFile(String path) {
        // 安全检查：只允许读 config/ 和 logs/ 下的文件
        Path p = Paths.get(path);
        if (!p.normalize().toString().startsWith("config") && !p.normalize().toString().startsWith("logs")) {
            return "安全限制：只能读取 config/ 和 logs/ 目录下的文件";
        }
        if (!Files.isRegularFile(p)) return "文件不存在: " + path;
        try {
            String content = Files.readString(p);
            if (content.length() > 5000) content = content.substring(0, 5000) + "\n...[截断]";
            return "文件 " + path + ":\n" + content;
        } catch (IOException e) { return "读取失败: " + e.getMessage(); }
    }

    private String listDir(String path) {
        Path p = Paths.get(path.isEmpty() ? "." : path);
        if (!Files.isDirectory(p)) return "目录不存在: " + path;
        try {
            return Files.list(p).limit(50)
                .map(f -> (Files.isDirectory(f) ? "D " : "F ") + f.getFileName())
                .collect(Collectors.joining("\n"));
        } catch (IOException e) { return "列出失败: " + e.getMessage(); }
    }

    private String queryBlock(CommandSourceStack source, String coords) {
        BlockPos pos = parseCoords(coords, source);
        ServerLevel level = source.getLevel();
        if (level == null) return "无维度";
        BlockState state = level.getBlockState(pos);
        return "方块 " + pos.toShortString() + ": " + state.getBlock().builtInRegistryHolder().key().location();
    }

    private String queryNearby(CommandSourceStack source, String radius) {
        double r = Double.parseDouble(radius);
        Vec3 center = source.getPosition();
        ServerLevel level = source.getLevel();
        if (level == null) return "无维度";
        var entities = level.getEntities((Entity)null,
            new net.minecraft.world.phys.AABB(center.x - r, center.y - r, center.z - r,
                center.x + r, center.y + r, center.z + r),
            e -> true);
        StringBuilder sb = new StringBuilder("附近 " + entities.size() + " 个实体 (半径" + r + "):\n");
        entities.stream().limit(20).forEach(e ->
            sb.append(String.format("  %s [%s] (%.1f, %.1f, %.1f)\n",
                e.getName().getString(), e.getType().toShortString(), e.getX(), e.getY(), e.getZ())));
        if (entities.size() > 20) sb.append("  ...(共 " + entities.size() + " 个)");
        return sb.toString();
    }

    private String queryPlayer(CommandSourceStack source) {
        Entity e = source.getEntity();
        if (!(e instanceof ServerPlayer p)) return "不是玩家";
        return String.format("玩家=%s 维度=%s 坐标=%.1f %.1f %.1f 血量=%.1f/%s 经验=%d级 手持=%s 游戏模式=%s",
            p.getName().getString(),
            p.level().dimension().location(),
            p.getX(), p.getY(), p.getZ(),
            p.getHealth(), p.getMaxHealth(),
            p.experienceLevel,
            p.getMainHandItem().isEmpty() ? "空" : p.getMainHandItem().getDisplayName().getString(),
            p.gameMode.getGameModeForPlayer().getName());
    }

    private String queryWorld(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (level == null) return "无维度";
        return String.format("维度=%s 时间=%d ticks (%s) 天气=%s 难度=%s",
            level.dimension().location(),
            level.getDayTime() % 24000,
            level.isDay() ? "白天" : "夜晚",
            level.isRaining() ? (level.isThundering() ? "雷暴" : "下雨") : "晴朗",
            level.getDifficulty().name());
    }

    private String queryMods() {
        return net.minecraftforge.fml.ModList.get().getMods().stream()
            .map(m -> m.getModId() + " " + m.getVersion())
            .limit(50).collect(Collectors.joining("\n", "已装 mods:\n", ""));
    }

    private BlockPos parseCoords(String coords, CommandSourceStack source) {
        Vec3 p = source.getPosition();
        if (coords.isEmpty()) return BlockPos.containing(p);
        String[] s = coords.replace("~", "").split("[,\\s]+");
        try {
            double x = s.length > 0 ? (s[0].isEmpty() ? p.x : Double.parseDouble(s[0])) : p.x;
            double y = s.length > 1 ? (s[1].isEmpty() ? p.y : Double.parseDouble(s[1])) : p.y;
            double z = s.length > 2 ? (s[2].isEmpty() ? p.z : Double.parseDouble(s[2])) : p.z;
            return BlockPos.containing(x, y, z);
        } catch (NumberFormatException e) { return BlockPos.containing(p); }
    }

    /** 执行单条指令 */
    private boolean executeSingle(String cmd, CommandSourceStack source, StringBuilder result) {
        String enhanced = cmd.replace("@s", getPlayerName(source));
        enhanced = enhanced.replace("@p", getPlayerName(source));

        String raw = enhanced.startsWith("/") ? enhanced.substring(1) : enhanced;
        if (raw.isEmpty()) return false;

        if (isBlocked(raw)) {
            result.append("\n§c[禁止] /").append(raw);
            return false;
        }

        try {
            int rate = server.getCommands().getDispatcher().execute(raw, source);
            if (rate > 0) {
                result.append("\n§a[OK] /").append(raw).append(" (返回 ").append(rate).append(")");
            } else {
                result.append("\n§e[无变化] /").append(raw).append(" (返回 0)");
            }
            ModLogger.commandExec(raw, rate);
            return true;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            // 提取关键信息给 AI
            result.append("\n§c[失败] /").append(raw).append("\n  错误: ").append(msg);
            ModLogger.commandError(raw, msg);
            return true; // 也算执行了，让 AI 看到错误
        }
    }

    /** 从游戏注册表查询物品信息 */
    private String queryItem(String itemId) {
        if (itemId.isEmpty()) return "用法: item <物品ID>  例: item minecraft:diamond";
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
            new net.minecraft.resources.ResourceLocation(itemId));
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return "物品未找到: " + itemId;
        }
        var stack = new net.minecraft.world.item.ItemStack(item);
        StringBuilder sb = new StringBuilder();
        sb.append("物品: ").append(itemId).append("\n");
        sb.append("显示名: ").append(stack.getDisplayName().getString()).append("\n");
        sb.append("类型: ").append(item.getClass().getSimpleName()).append("\n");
        // 工具提示（含属性等）
        var tip = stack.getTooltipLines(null, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
        if (!tip.isEmpty()) {
            sb.append("提示:\n");
            for (var t : tip) {
                sb.append("  ").append(t.getString()).append("\n");
            }
        }
        // 最大堆叠
        sb.append("最大堆叠: ").append(item.getMaxStackSize());
        // 食用信息
        if (item.isEdible()) {
            var food = item.getFoodProperties();
            if (food != null) {
                sb.append("\n食物: ").append(food.getNutrition()).append("饱食度 +")
                  .append(String.format("%.1f", food.getSaturationModifier())).append("饱和度");
            }
        }
        return sb.toString();
    }

    /** AI 查询 PMX 模型元数据（顶点/面/包围盒/材质），据此决定构建参数 */
    private String queryPMX(String filePath) {
        if (filePath.isEmpty()) return "用法: pmx <文件路径>";
        // 去引号
        filePath = filePath.replace("\"", "").replace("\"", "").replace("\"", "").trim();
        java.nio.file.Path p = java.nio.file.Paths.get(filePath);
        if (!java.nio.file.Files.exists(p)) return "文件不存在: " + filePath;
        try {
            var parser = new com.xxsx.builder.voxel.PMXParser();
            var model = parser.parse(p);
            // 计算包围盒
            float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,minZ=Float.MAX_VALUE;
            float maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE,maxZ=-Float.MAX_VALUE;
            for (var v : model.vertices) {
                if (v.x<minX) minX=v.x; if (v.y<minY) minY=v.y; if (v.z<minZ) minZ=v.z;
                if (v.x>maxX) maxX=v.x; if (v.y>maxY) maxY=v.y; if (v.z>maxZ) maxZ=v.z;
            }
            float sx=maxX-minX, sy=maxY-minY, sz=maxZ-minZ;
            float maxDim = Math.max(sx, Math.max(sy, sz));
            int suggestedScale = (int)(50000.0 / Math.sqrt(model.vertices.size()));
            suggestedScale = Math.max(30, Math.min(400, suggestedScale));

            return String.format(
                "PMX模型: %s\n  顶点: %d  面三角形: %d  材质: %d  纹理: %d\n" +
                "  包围盒: %.2f x %.2f x %.2f (最大维: %.2f)\n" +
                "  建议比例: %d (范围 30-400, 越大越精细)\n" +
                "  估算方块: 约 %d 块 (比例=%d时)",
                p.getFileName(), model.vertices.size(), model.faces.size(),
                model.materials.size(), 0,
                sx, sy, sz, maxDim,
                suggestedScale,
                (int)(sx*suggestedScale/maxDim * sy*suggestedScale/maxDim * sz*suggestedScale/maxDim * 0.3),
                suggestedScale);
        } catch (Exception e) {
            return "PMX 解析失败: " + e.getMessage();
        }
    }

    private String queryRecipes(String itemId) {
        if (itemId.isEmpty()) return "用法: recipe <物品ID>";
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
            new net.minecraft.resources.ResourceLocation(itemId));
        if (item == null) return "物品未找到: " + itemId;
        var stack = new net.minecraft.world.item.ItemStack(item);
        var level = server.overworld();
        StringBuilder sb = new StringBuilder();
        sb.append(itemId).append(" 的合成/制作方式:\n");
        int found = 0;

        // 遍历所有配方类型
        var rm = level.getRecipeManager();
        for (var recipe : rm.getRecipes()) {
            try {
                var result = recipe.getResultItem(level.registryAccess());
                if (result.getItem() == item) {
                    found++;
                    if (found > 8) { sb.append("  ...(共 " + found + "+) 个配方)\n"); return sb.toString(); }
                    sb.append("  [" + recipe.getType().toString() + "] ");
                    for (var ing : recipe.getIngredients()) {
                        var items = ing.getItems();
                        if (items.length == 0) sb.append("[空] ");
                        else sb.append(items[0].getDisplayName().getString()).append(" ");
                    }
                    sb.append("→ ").append(result.getCount()).append("x ").append(item.getDescription().getString()).append("\n");
                }
            } catch (Exception ignored) {}
        }

        if (found == 0) sb.append("  未找到配方（可能由模组特殊方式制作）");
        return sb.toString();
    }

    private boolean isBlocked(String cmd) {
        String normalized = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        for (String prefix : blacklist) {
            if (normalized.startsWith(prefix.trim())) return true;
        }
        return false;
    }
}

package com.xxsx.builder.voxel;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.ai.ChatSession;
import com.xxsx.builder.config.BuilderConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 管理体素建筑任务。
 * 解析 PMX → 体素化 → 异步放置方块。
 */
public class VoxelBuildManager {
    private final MinecraftServer server;
    private final Map<String, BuildJob> jobs = new ConcurrentHashMap<>();

    public VoxelBuildManager(MinecraftServer server) {
        this.server = server;
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** 启动一个建筑任务 */
    public void startBuild(String playerName, String filePath, int scale, CommandSourceStack source) {
        // 检查是否已有任务
        if (jobs.containsKey(playerName)) {
            source.sendFailure(Component.literal("§c你已有正在执行的任务，使用 /ai stop 取消"));
            return;
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            source.sendFailure(Component.literal("§c文件不存在: " + filePath));
            return;
        }
        String ext = filePath.toLowerCase();
        if (!ext.endsWith(".pmx") && !ext.endsWith(".obj")) {
            source.sendFailure(Component.literal("§c仅支持 .pmx 和 .obj 格式"));
            return;
        }

        source.sendSuccess(() -> Component.literal("§e正在解析模型..."), false);

        // 异步解析和体素化
        CompletableFuture<BuildJob> future = CompletableFuture.supplyAsync(() -> {
            try {
                long t0 = System.currentTimeMillis();
                PMXModel model;
                if (ext.endsWith(".obj")) {
                    OBJParser objParser = new OBJParser();
                    OBJModel objModel = objParser.parse(path);
                    model = convertOBJ(objModel);
                } else {
                    PMXParser parser = new PMXParser();
                    model = parser.parse(path);
                }

                BuilderConfig config = XxsxBuilder.getInstance().getConfig();
                if (model.getVertexCount() > config.maxVertices) {
                    throw new RuntimeException("模型顶点数 " + model.getVertexCount() +
                            " 超过限制 " + config.maxVertices);
                }

                Voxelizer.VoxelGrid grid = Voxelizer.voxelize(model, scale);

                long t1 = System.currentTimeMillis();
                XxsxBuilder.LOGGER.info("[Build] 解析+体素化耗时: {}ms", t1 - t0);

                BuildJob job = new BuildJob(playerName, model, grid, source);

                // 生成统计信息
                int w = grid.width, h = grid.height, d = grid.depth;
                job.info = String.format(
                        "§e📐 模型: %s (%d 面, %d 顶点)\n" +
                        "§e📦 坐标范围: %d,%d,%d → %d,%d,%d  (%dx%dx%d)\n" +
                        "§e🧱 约 %d 方块 | 约 %d 秒",
                        path.getFileName().toString(),
                        model.getFaceCount(), model.getVertexCount(),
                        0, 0, 0, w - 1, h - 1, d - 1, w, h, d,
                        grid.filledCount,
                        grid.filledCount / Math.max(1, config.blocksPerTick) / 20);

                return job;
            } catch (Exception e) {
                XxsxBuilder.LOGGER.error("[Build] 解析失败", e);
                // 显示真实原因而非 null
                String reason = e.getMessage();
                if (reason == null) {
                    Throwable cause = e.getCause();
                    reason = (cause != null && cause.getMessage() != null)
                            ? cause.getMessage() : e.getClass().getSimpleName();
                }
                throw new RuntimeException("解析失败: " + reason);
            }
        });

        future.thenAcceptAsync(job -> {
            jobs.put(playerName, job);
            source.sendSuccess(() -> Component.literal(
                "§a开始生成! " + job.grid.width + "x" + job.grid.height + "x" + job.grid.depth
                + " (约" + job.grid.filledCount + "方块, 首帧将提示清除区域)"), false);
        }, server);

        future.exceptionally(e -> {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return null;
        });
    }

    /** 是否有活跃的建筑任务 */
    public boolean hasActiveJob(String playerName) {
        BuildJob job = jobs.get(playerName);
        return job != null && !job.finished && !job.cancelled;
    }

    /** y确认清除 */
    public boolean confirmClear(String playerName) {
        BuildJob job = jobs.get(playerName);
        if (job != null && job.clearState == 1) {
            job.clearState = 2; // → 清除中
            job.clearIndex = 0;
            job.source.sendSuccess(() -> Component.literal("§a开始清除区域..."), false);
            return true;
        }
        return false;
    }

    /** n跳过清除 */
    public boolean cancelClear(String playerName) {
        BuildJob job = jobs.get(playerName);
        if (job != null && job.clearState == 1) {
            job.clearState = 3; // → 已跳过
            job.source.sendSuccess(() -> Component.literal("§7跳过清除，直接建造"), false);
            return true;
        }
        return false;
    }

    /** 设置建造速度 */
    public boolean setSpeed(String playerName, int speed) {
        BuildJob job = jobs.get(playerName);
        if (job != null && !job.finished) {
            job.buildSpeed = Math.max(10, Math.min(5000, speed));
            job.source.sendSuccess(() -> Component.literal(
                "§e建造速度: " + job.buildSpeed + "/tick (" + (job.buildSpeed*20) + "/秒)"), false);
            return true;
        }
        return false;
    }

    /** 取消玩家的建筑任务 */
    public boolean cancelBuild(String playerName) {
        BuildJob job = jobs.remove(playerName);
        if (job != null) {
            job.cancelled = true;
            return true;
        }
        return false;
    }

    /** 每 tick 放置方块 */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!server.isRunning()) return;

        BuilderConfig config = XxsxBuilder.getInstance().getConfig();
        int perTick = config.blocksPerTick;

        for (BuildJob job : jobs.values()) {
            if (job.cancelled) continue;
            if (job.finished) continue;

            try {
                ServerLevel level = server.overworld();
                BlockPos center = new BlockPos(
                        (int) job.source.getPosition().x,
                        (int) job.source.getPosition().y,
                        (int) job.source.getPosition().z);

                Voxelizer.VoxelGrid grid = job.grid;
                int speed = job.buildSpeed > 0 ? job.buildSpeed : perTick;

                // === 清除区域 ===
                if (config.buildAskClear && job.clearState == 0) {
                    job.clearState = 1; // → 询问
                    final int hw = grid.width/2, hh = grid.height/2, hd = grid.depth/2;
                    final int count = (hw*2+1) * (hh*2+1) * (hd*2+1);
                    job.clearTotal = count;
                    net.minecraft.network.chat.MutableComponent msg = net.minecraft.network.chat.Component.literal(
                        "§e清除 " + (hw*2+1) + "x" + (hh*2+1) + "x" + (hd*2+1) + " 区域(" + count + "方块)? ");
                    net.minecraft.network.chat.MutableComponent yes = net.minecraft.network.chat.Component.literal("§a[ 是(Y) ]");
                    yes.setStyle(yes.getStyle().withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/ai build y")));
                    msg.append(yes).append(" ");
                    net.minecraft.network.chat.MutableComponent no = net.minecraft.network.chat.Component.literal("§c[ 否(N) ]");
                    no.setStyle(no.getStyle().withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/ai build n")));
                    msg.append(no);
                    job.source.sendSuccess(() -> msg, false);
                    return;
                }

                if (job.clearState == 2) {
                    // 分帧清除，和建造同速度
                    int cleared = 0;
                    int hw = grid.width/2, hh = grid.height/2, hd = grid.depth/2;
                    while (cleared < speed && job.clearIndex < job.clearTotal) {
                        int idx = job.clearIndex++;
                        int dx = (idx % (hw*2+1)) - hw;
                        int rem = idx / (hw*2+1);
                        int dy = (rem % (hh*2+1)) - hh;
                        int dz = (rem / (hh*2+1)) - hd;
                        try {
                            level.setBlock(center.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2); // flag2=无邻居更新
                            cleared++;
                        } catch (Exception ignored) {}
                    }
                    if (job.clearIndex % (speed * 20) == 0 || job.clearIndex >= job.clearTotal) {
                        int pct = job.clearIndex * 100 / Math.max(1, job.clearTotal);
                        job.source.sendSuccess(() -> Component.literal(
                            "§7清除: " + pct + "% (" + job.clearIndex + "/" + job.clearTotal + ", " + speed + "/tick)"), false);
                    }
                    if (job.clearIndex >= job.clearTotal) {
                        job.clearState = 3; // → 已清除
                        job.source.sendSuccess(() -> Component.literal(
                            "§a清除完成: " + job.clearTotal + " 方块"), false);
                    }
                    return;
                }

                // 等待确认或已跳过 → 开始建造
                if (job.clearState != 1) {
                int placed = 0;
                while (placed < speed && job.nextIndex < totalVoxels(grid)) {
                    int idx = job.nextIndex++;
                    int x = idx % grid.width;
                    int y = (idx / grid.width) % grid.height;
                    int z = idx / (grid.width * grid.height);

                    int color = grid.colors[x][y][z];
                    if (color != 0) {
                        int r = (color >> 16) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int b = color & 0xFF;

                        BlockColorMapper.BlockMatch match = BlockColorMapper.findClosest(r, g, b);
                        Block block = getBlock(match.blockId);
                        if (block != null && block != Blocks.AIR) {
                            BlockPos target = center.offset(
                                    x - grid.width / 2,
                                    y - grid.height / 2,
                                    z - grid.depth / 2);
                            level.setBlock(target, block.defaultBlockState(), 3);
                        }
                        placed++;
                        job.placedCount++;
                    }
                }

                // 进度报告
                int total = totalVoxels(grid);
                if (job.placedCount >= total || job.nextIndex >= total) {
                    job.finished = true;
                    job.source.sendSuccess(() -> Component.literal(
                            "§a✅ 建筑完成！共 " + job.placedCount + " 个方块 | "
                            + job.grid.width + "x" + job.grid.height + "x" + job.grid.depth), false);
                    // 反馈给 AI 会话，AI 可据此建议调整
                    var sm = XxsxBuilder.getInstance().getSessionManager();
                    if (sm != null) {
                        sm.addSystemMessage(job.playerName,
                            "【建筑完成】" + job.info + " 方块数=" + job.placedCount
                            + " 尺寸=" + job.grid.width + "x" + job.grid.height + "x" + job.grid.depth
                            + "。如果比例不合适，用户可以说'太大了'或'缩小到X'，你可用 /ai build 重新建。");
                    }
                } // end if clearState != 1
                } // end try
            } catch (Exception e) {
                XxsxBuilder.LOGGER.error("[Build] 放置方块异常", e);
                job.source.sendFailure(Component.literal("§c生成出错: " + e.getMessage()));
                job.finished = true;
            }
        }

        // 清理已完成的任务
        jobs.values().removeIf(j -> j.finished || j.cancelled);
    }

    private int totalVoxels(Voxelizer.VoxelGrid grid) {
        return grid.width * grid.height * grid.depth;
    }

    /** OBJModel → PMXModel 转换 */
    private static PMXModel convertOBJ(OBJModel o) {
        PMXModel m = new PMXModel();
        m.vertices.addAll(o.vertices);
        m.normals.addAll(o.normals);
        m.uvs.addAll(o.uvs);
        m.faces.addAll(o.faces);
        m.faceMaterials.addAll(o.faceMaterials);
        m.materials.addAll(o.materials);
        return m;
    }

    private Block getBlock(String blockId) {
        try {
            return net.minecraftforge.registries.ForgeRegistries.BLOCKS
                    .getValue(new net.minecraft.resources.ResourceLocation(blockId));
        } catch (Exception e) {
            return Blocks.WHITE_CONCRETE;
        }
    }

    /** 单个建筑任务 */
    public static class BuildJob {
        public final String playerName;
        public final PMXModel model;
        public final Voxelizer.VoxelGrid grid;
        public final CommandSourceStack source;
        public String info = "";
        public volatile boolean cancelled = false;
        public volatile boolean finished = false;
        // 0=未开始 1=询问中 2=清除中 3=已清除/已跳过
        public volatile int clearState = 0;
        public int clearIndex = 0;
        public int clearTotal = 0;
        public int buildSpeed = -1; // -1=用全局默认
        public int nextIndex = 0;
        public int placedCount = 0;
        public int lastReported = 0;

        void clearArea(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos center,
                       Voxelizer.VoxelGrid grid) {
            int hw = grid.width/2, hh = grid.height/2, hd = grid.depth/2, c = 0;
            for (int dx = -hw; dx <= hw; dx++)
                for (int dy = -hh; dy <= hh; dy++)
                    for (int dz = -hd; dz <= hd; dz++)
                        try {
                            level.setBlock(center.offset(dx,dy,dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                            c++;
                        } catch (Exception ignored) {}
            final int cc = c;
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "§7已清除 " + cc + " 方块"), false);
        }

        public BuildJob(String playerName, PMXModel model, Voxelizer.VoxelGrid grid, CommandSourceStack source) {
            this.playerName = playerName;
            this.model = model;
            this.grid = grid;
            this.source = source;
            // 初始用配置的速度
            var c = XxsxBuilder.getInstance().getConfig();
            this.buildSpeed = c.buildSpeed > 0 ? c.buildSpeed : c.blocksPerTick;
        }
    }
}

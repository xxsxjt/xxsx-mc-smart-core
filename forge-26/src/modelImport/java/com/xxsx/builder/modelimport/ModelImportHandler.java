package com.xxsx.builder.modelimport;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.ai.ChatSession;
import com.xxsx.builder.voxel.OBJModel;
import com.xxsx.builder.voxel.OBJParser;
import com.xxsx.builder.voxel.PMXModel;
import com.xxsx.builder.voxel.PMXParser;
import com.xxsx.builder.voxel.VoxelBuildManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** PMX/OBJ parsing and voxel building owned by the optional extension. */
public final class ModelImportHandler implements ModelImportBridge.Handler {
    private final VoxelBuildManager buildManager;
    public ModelImportHandler(net.minecraft.server.MinecraftServer server) {
        buildManager = new VoxelBuildManager(server);
        int speed = loadBuildSpeed();
        if (speed > 0) XxsxBuilder.getInstance().getConfig().buildSpeed = speed;
    }
    public boolean handleNumericInput(String input, CommandSourceStack source, ChatSession session) {
        final float multiplier;
        try { multiplier = Float.parseFloat(input); } catch (NumberFormatException e) { return false; }
        if (!Float.isFinite(multiplier) || session.pendingBuildPath == null) return false;
        if (multiplier <= 0 || multiplier > 100) { source.sendFailure(Component.literal("§c倍数范围: 0.1 ~ 100")); return true; }
        String path = session.pendingBuildPath;
        float scale = session.pendingBuildBaseScale * multiplier;
        session.pendingBuildPath = null;
        String shown = multiplier == (int) multiplier ? String.valueOf((int) multiplier) : String.format("%.1f", multiplier);
        source.sendSuccess(() -> Component.literal("§e倍数 " + shown + "x → 实际比例 " + Math.round(scale)), false);
        buildManager.startBuild(session.playerName, path, scale, source);
        if (session.pendingBuildSpeed > 0) buildManager.setSpeed(session.playerName, session.pendingBuildSpeed);
        return true;
    }
    public int handleBuildSpeed(CommandSourceStack source, String value) {
        try {
            int speed = Integer.parseInt(value);
            XxsxBuilder.getInstance().getConfig().buildSpeed = speed;
            saveBuildSpeed(speed);
            source.sendSuccess(() -> Component.literal("§e速度已保存: " + speed + "/tick (建造开始时生效)"), false);
            buildManager.setSpeed(source.getTextName(), speed);
        } catch (NumberFormatException e) { source.sendFailure(Component.literal("§c格式: /ai build speed <数字>")); }
        return 1;
    }
    public int handleBuildConfirm(CommandSourceStack source, boolean confirm) {
        boolean ok = confirm ? buildManager.confirmClear(source.getTextName()) : buildManager.cancelClear(source.getTextName());
        source.sendSuccess(() -> Component.literal(ok ? (confirm ? "§a已确认，正在清除..." : "§7已跳过清除") : "§c没有待确认的建造"), false);
        return 1;
    }
    public int handleBuildStop(CommandSourceStack source) {
        boolean ok = buildManager.cancelBuild(source.getTextName());
        if (ok) source.sendSuccess(() -> Component.literal("§a建造已停止"), false);
        else source.sendFailure(Component.literal("§c没有活跃的建造"));
        return 1;
    }
    public int handleBuildCommand(String args, CommandSourceStack source, String playerName) {
        String text = args.trim();
        if (text.startsWith(""") && text.indexOf(""", 1) > 0) text = text.substring(1, text.indexOf(""", 1));
        if (text.isEmpty()) { source.sendFailure(Component.literal("§c请指定 PMX/OBJ 路径")); return 1; }
        try {
            Path path = Paths.get(text);
            if (!Files.exists(path)) { source.sendFailure(Component.literal("§c文件不存在: " + text)); return 1; }
            PMXModel model = parseModel(path);
            if (model.textureBaseDir == null && path.getParent() != null) model.textureBaseDir = path.getParent().toString();
            float[] size = bounds(model);
            int suggested = suggestedScale(model.vertices.size());
            float max = Math.max(size[0], Math.max(size[1], size[2]));
            if (max <= 0) max = 1;
            int w = Math.max(1, Math.round(size[0] * suggested / max));
            int h = Math.max(1, Math.round(size[1] * suggested / max));
            int d = Math.max(1, Math.round(size[2] * suggested / max));
            source.sendSuccess(() -> Component.literal("§e" + path.getFileName() + " | 顶点" + model.vertices.size() + " 面" + model.faces.size() + " 材质" + model.materials.size()), false);
            source.sendSuccess(() -> Component.literal("§71x大小: " + w + "x" + h + "x" + d + " 方块 | 默认1x，可输入 /ai 3"), false);
            ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(playerName);
            session.pendingBuildPath = text;
            session.pendingBuildBaseScale = suggested;
        } catch (Exception e) { source.sendFailure(Component.literal("§cPMX/OBJ解析失败: " + e.getMessage())); }
        return 1;
    }
    public String queryPMX(String filePath) {
        if (filePath == null || filePath.isBlank()) return "[PMX] 用法: [QUERY]pmx 文件路径[/QUERY]";
        String clean = filePath.replace(""", "").trim();
        Path path = Paths.get(clean);
        if (!Files.exists(path)) return "[PMX] 文件不存在: " + clean;
        try {
            PMXModel model = parseModel(path); float[] size = bounds(model);
            int suggested = suggestedScale(model.vertices.size());
            return String.format("[PMX模型信息] 文件=%s, 顶点=%d, 面=%d, 材质=%d, 尺寸=%.2fx%.2fx%.2f, 建议基础比例=%d", path.getFileName(), model.vertices.size(), model.faces.size(), model.materials.size(), size[0], size[1], size[2], suggested);
        } catch (Exception e) { return "[PMX] 解析失败: " + e.getMessage(); }
    }
    public boolean cancelBuild(String playerName) { return buildManager.cancelBuild(playerName); }
    public boolean hasActiveJob(String playerName) { return buildManager.hasActiveJob(playerName); }
    private static PMXModel parseModel(Path path) throws Exception {
        if (path.toString().toLowerCase().endsWith(".obj")) {
            OBJModel obj = new OBJParser().parse(path); PMXModel model = new PMXModel();
            model.vertices.addAll(obj.vertices); model.normals.addAll(obj.normals); model.uvs.addAll(obj.uvs); model.faces.addAll(obj.faces); model.faceMaterials.addAll(obj.faceMaterials); model.materials.addAll(obj.materials); return model;
        }
        return new PMXParser().parse(path);
    }
    private static float[] bounds(PMXModel model) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (var v : model.vertices) { minX = Math.min(minX, v.x); minY = Math.min(minY, v.y); minZ = Math.min(minZ, v.z); maxX = Math.max(maxX, v.x); maxY = Math.max(maxY, v.y); maxZ = Math.max(maxZ, v.z); }
        if (model.vertices.isEmpty()) return new float[] {0, 0, 0};
        return new float[] {maxX - minX, maxY - minY, maxZ - minZ};
    }
    private static int suggestedScale(int vertices) { return Math.max(30, Math.min(400, (int) (50000.0 / Math.sqrt(Math.max(1, vertices))))); }
    private static void saveBuildSpeed(int speed) { try { Path p = Paths.get("config", "xxsx_mc_smart_core", "build_speed.txt"); Files.createDirectories(p.getParent()); Files.writeString(p, String.valueOf(speed)); } catch (Exception e) { XxsxBuilder.LOGGER.warn("[Model Import] 保存速度失败: {}", e.getMessage()); } }
    private static int loadBuildSpeed() { try { Path p = Paths.get("config", "xxsx_mc_smart_core", "build_speed.txt"); if (Files.exists(p)) return Integer.parseInt(Files.readString(p).trim()); } catch (Exception ignored) {} return -1; }
}

package com.xxsx.builder.modelimport;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.ai.ChatSession;
import com.xxsx.builder.api.modelimport.ModelImportBridge;
import com.xxsx.builder.config.SmartCoreText;
import com.xxsx.builder.voxel.OBJModel;
import com.xxsx.builder.voxel.OBJParser;
import com.xxsx.builder.voxel.PMXModel;
import com.xxsx.builder.voxel.PMXParser;
import com.xxsx.builder.voxel.VoxelBuildManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Model parsing and voxel-build implementation owned by the optional extension. */
public final class ModelImportHandler implements ModelImportBridge.Handler {
    private final VoxelBuildManager buildManager;

    public ModelImportHandler(net.minecraft.server.MinecraftServer server) {
        this.buildManager = new VoxelBuildManager(server);
        int savedSpeed = loadBuildSpeed();
        if (savedSpeed > 0) XxsxBuilder.getInstance().getConfig().buildSpeed = savedSpeed;
    }

    @Override
    public boolean handleNumericInput(String input, CommandSourceStack source, ChatSession session) {
        final float multiplier;
        try {
            multiplier = Float.parseFloat(input);
        } catch (NumberFormatException ignored) {
            return false;
        }
        if (!Float.isFinite(multiplier)) return false;
        if (session.pendingBuildPath == null) return false;
        if (multiplier <= 0 || multiplier > 100) {
            source.sendFailure(SmartCoreText.command("multiplier.range")
                .withStyle(ChatFormatting.RED));
            return true;
        }

        String path = session.pendingBuildPath;
        float actualScale = session.pendingBuildBaseScale * multiplier;
        session.pendingBuildPath = null;
        String mulStr = multiplier == (int) multiplier
            ? String.valueOf((int) multiplier) : String.format("%.1f", multiplier);
        source.sendSuccess(() -> SmartCoreText.command("multiplier.selected", mulStr,
            Math.round(actualScale)).withStyle(ChatFormatting.YELLOW), false);
        buildManager.startBuild(session.playerName, path, actualScale, source);
        if (session.pendingBuildSpeed > 0) {
            buildManager.setSpeed(session.playerName, session.pendingBuildSpeed);
        }
        return true;
    }

    @Override
    public int handleBuildSpeed(CommandSourceStack source, String value) {
        try {
            int speed = Integer.parseInt(value);
            XxsxBuilder.getInstance().getConfig().buildSpeed = speed;
            saveBuildSpeed(speed);
            String name = source.getTextName();
            if (buildManager.setSpeed(name, speed)) {
                source.sendSuccess(() -> SmartCoreText.command("build.speed_saved_active", speed, speed * 20)
                    .withStyle(ChatFormatting.YELLOW), false);
            } else {
                source.sendSuccess(() -> SmartCoreText.command("build.speed_saved", speed)
                    .withStyle(ChatFormatting.YELLOW), false);
            }
        } catch (NumberFormatException e) {
            source.sendFailure(SmartCoreText.command("build.speed_format")
                .withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    @Override
    public int handleBuildConfirm(CommandSourceStack source, boolean confirm) {
        String name = source.getTextName();
        if (confirm) {
            if (buildManager.confirmClear(name)) {
                source.sendSuccess(() -> SmartCoreText.command("build.clear_confirmed")
                    .withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendFailure(SmartCoreText.command("build.no_pending")
                    .withStyle(ChatFormatting.RED));
            }
        } else if (buildManager.cancelClear(name)) {
            source.sendSuccess(() -> SmartCoreText.command("build.clear_skipped")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendFailure(SmartCoreText.command("build.no_pending")
                .withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    @Override
    public int handleBuildStop(CommandSourceStack source) {
        if (buildManager.cancelBuild(source.getTextName())) {
            source.sendSuccess(() -> SmartCoreText.command("build.stopped")
                .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendFailure(SmartCoreText.command("build.no_active")
                .withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    @Override
    public int handleBuildCommand(String args, CommandSourceStack source, String playerName) {
        String path = args.trim();
        if (path.startsWith("\"") && path.indexOf("\"", 1) > 0) {
            path = path.substring(1, path.indexOf("\"", 1));
        }
        if (path.isEmpty()) {
            source.sendFailure(SmartCoreText.command("pmx.path_required")
                .withStyle(ChatFormatting.RED));
            return 1;
        }

        try {
            Path modelPath = Paths.get(path);
            if (!Files.exists(modelPath)) {
                source.sendFailure(SmartCoreText.command("pmx.file_missing", path)
                    .withStyle(ChatFormatting.RED));
                return 1;
            }
            PMXModel model = parseModel(modelPath);
            if (model.textureBaseDir == null && modelPath.getParent() != null) {
                model.textureBaseDir = modelPath.getParent().toString();
            }
            float[] size = bounds(model);
            int suggested = suggestedScale(model.vertices.size());
            float maxDim = Math.max(size[0], Math.max(size[1], size[2]));
            if (maxDim <= 0) maxDim = 1;
            int w1 = Math.max(1, Math.round(size[0] * suggested / maxDim));
            int h1 = Math.max(1, Math.round(size[1] * suggested / maxDim));
            int d1 = Math.max(1, Math.round(size[2] * suggested / maxDim));

            source.sendSuccess(() -> SmartCoreText.command("pmx.summary", modelPath.getFileName(),
                model.vertices.size(), model.faces.size(), model.materials.size())
                .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> SmartCoreText.command("pmx.size", w1, h1, d1,
                w1 * 3, h1 * 3, d1 * 3).withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> SmartCoreText.command("pmx.next_step")
                .withStyle(ChatFormatting.YELLOW), false);

            ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(playerName);
            session.pendingBuildPath = path;
            session.pendingBuildBaseScale = suggested;
        } catch (Exception e) {
            source.sendFailure(SmartCoreText.command("pmx.parse_failed", e.getMessage())
                .withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    @Override
    public String queryPMX(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return SmartCoreText.text("query.xxsx_mc_smart_core.pmx.usage");
        }
        String cleanPath = filePath.replace("\"", "").trim();
        Path path = Paths.get(cleanPath);
        if (!Files.exists(path)) {
            return SmartCoreText.text("query.xxsx_mc_smart_core.pmx.file_missing", cleanPath);
        }
        try {
            PMXModel model = parseModel(path);
            float[] size = bounds(model);
            float maxDim = Math.max(size[0], Math.max(size[1], size[2]));
            int suggested = suggestedScale(model.vertices.size());
            if (maxDim <= 0) maxDim = 1;
            int estimate = Math.max(1, (int) (size[0] * suggested / maxDim
                * size[1] * suggested / maxDim * size[2] * suggested / maxDim * 0.3));
            return SmartCoreText.text("query.xxsx_mc_smart_core.pmx.info", path.getFileName(),
                model.vertices.size(), model.faces.size(), model.materials.size(), 0,
                String.format("%.2f", size[0]), String.format("%.2f", size[1]), String.format("%.2f", size[2]),
                String.format("%.2f", maxDim), suggested, estimate, suggested);
        } catch (Exception e) {
            return SmartCoreText.text("query.xxsx_mc_smart_core.pmx.parse_failed", e.getMessage());
        }
    }

    @Override
    public boolean cancelBuild(String playerName) {
        return buildManager.cancelBuild(playerName);
    }

    @Override
    public boolean hasActiveJob(String playerName) {
        return buildManager.hasActiveJob(playerName);
    }

    private static PMXModel parseModel(Path path) throws Exception {
        if (path.toString().toLowerCase().endsWith(".obj")) {
            OBJModel obj = new OBJParser().parse(path);
            PMXModel model = new PMXModel();
            model.vertices.addAll(obj.vertices);
            model.normals.addAll(obj.normals);
            model.uvs.addAll(obj.uvs);
            model.faces.addAll(obj.faces);
            model.faceMaterials.addAll(obj.faceMaterials);
            model.materials.addAll(obj.materials);
            return model;
        }
        return new PMXParser().parse(path);
    }

    private static float[] bounds(PMXModel model) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (var vertex : model.vertices) {
            minX = Math.min(minX, vertex.x); minY = Math.min(minY, vertex.y); minZ = Math.min(minZ, vertex.z);
            maxX = Math.max(maxX, vertex.x); maxY = Math.max(maxY, vertex.y); maxZ = Math.max(maxZ, vertex.z);
        }
        if (model.vertices.isEmpty()) return new float[] {0, 0, 0};
        return new float[] {maxX - minX, maxY - minY, maxZ - minZ};
    }

    private static int suggestedScale(int vertices) {
        return Math.max(30, Math.min(400, (int) (50000.0 / Math.sqrt(Math.max(1, vertices)))));
    }

    private static void saveBuildSpeed(int speed) {
        try {
            Path path = Paths.get("config", "xxsx_mc_smart_core", "build_speed.txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, String.valueOf(speed));
        } catch (Exception e) {
            XxsxBuilder.LOGGER.warn("[Model Import] 保存速度失败: {}", e.getMessage());
        }
    }

    private static int loadBuildSpeed() {
        try {
            Path path = Paths.get("config", "xxsx_mc_smart_core", "build_speed.txt");
            if (Files.exists(path)) return Integer.parseInt(Files.readString(path).trim());
        } catch (Exception ignored) {
        }
        return -1;
    }
}

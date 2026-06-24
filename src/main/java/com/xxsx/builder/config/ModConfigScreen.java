package com.xxsx.builder.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** 主界面弹窗 —— 启动时展示版本+建筑高度设置 */
public class ModConfigScreen extends Screen {
    // Forge 集成服务器从运行目录读 server.properties
    private static final Path PROPS_FILE = java.nio.file.Paths.get(
        System.getProperty("user.dir"), "server.properties");
    private EditBox heightInput;
    private String currentHeight = "320";

    public ModConfigScreen() {
        super(Component.literal("xxsx 智能核心"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2, w = 240;

        // 标题
        this.addRenderableWidget(Button.builder(
            Component.literal("§bxxsx的智能核心 v1.0.1 已装载"), b -> {})
            .bounds(cx - w/2, 40, w, 20).build());

        // 说明
        this.addRenderableWidget(Button.builder(
            Component.literal("§7建造大模型可能受高度限制"), b -> {})
            .bounds(cx - w/2, 65, w, 20).build());

        // 高度输入
        this.addRenderableWidget(Button.builder(
            Component.literal("§7最大建筑高度:"), b -> {})
            .bounds(cx - w/2, 95, w, 20).build());

        this.heightInput = new EditBox(this.font, cx - 50, 120, 100, 20,
            Component.literal("高度"));
        this.heightInput.setValue(currentHeight);
        this.heightInput.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(this.heightInput);

        // 保存按钮
        this.addRenderableWidget(Button.builder(
            Component.literal("§a保存"), b -> {
                saveHeight();
                this.onClose();
            })
            .bounds(cx - 110, 160, 100, 20).build());

        // 退出按钮
        this.addRenderableWidget(Button.builder(
            CommonComponents.GUI_CANCEL, b -> this.onClose())
            .bounds(cx + 10, 160, 100, 20).build());

        // 加载当前值
        loadHeight();
    }

    private void loadHeight() {
        try {
            if (Files.exists(PROPS_FILE)) {
                Properties p = new Properties();
                try (var r = Files.newBufferedReader(PROPS_FILE)) { p.load(r); }
                currentHeight = p.getProperty("max-build-height", "320");
            }
        } catch (IOException ignored) {}
        if (heightInput != null) heightInput.setValue(currentHeight);
    }

    private void saveHeight() {
        try {
            Files.createDirectories(PROPS_FILE.getParent());
            Properties p = new Properties();
            if (Files.exists(PROPS_FILE)) {
                try (var r = Files.newBufferedReader(PROPS_FILE)) { p.load(r); }
            }
            p.setProperty("max-build-height", heightInput.getValue());
            try (var w = Files.newBufferedWriter(PROPS_FILE)) {
                p.store(w, "xxsx config");
            }
        } catch (IOException ignored) {}
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font, "§exxsx的智能核心 §7v1.0.1",
            this.width/2, 15, 0xFFFFFF);
        g.drawCenteredString(this.font, "§7建筑高度过低? 在此调整后新建世界即生效",
            this.width/2, this.height - 30, 0x888888);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null); // 回到主菜单
    }
}

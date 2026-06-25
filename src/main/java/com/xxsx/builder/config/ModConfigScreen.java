package com.xxsx.builder.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ModConfigScreen extends Screen {
    private EditBox mulBox;
    private int mul = 4;
    private static final int BASE = 384;

    public ModConfigScreen() {
        super(Component.literal("xxsx 智能核心"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // 倍数输入框 — 放在扩展文字后面
        this.mulBox = new EditBox(this.font, cx + 10, 160, 50, 20, Component.literal("mul"));
        this.mulBox.setValue(String.valueOf(mul));
        this.mulBox.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(this.mulBox);

        // 保存
        this.addRenderableWidget(Button.builder(
            Component.literal("保存"), b -> onClose())
            .bounds(cx - 55, 220, 50, 20).build());

        // 取消
        this.addRenderableWidget(Button.builder(
            CommonComponents.GUI_CANCEL, b -> onClose())
            .bounds(cx + 5, 220, 50, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        this.renderBackground(g);
        super.render(g, mx, my, delta);

        int cx = this.width / 2;
        try { mul = Integer.parseInt(mulBox.getValue()); } catch (Exception e) { mul = 4; }

        g.drawCenteredString(this.font, "§bxxsx的智能核心 §7v1.0.4", cx, 40, 0xFFFFFF);

        g.drawCenteredString(this.font, "§7建筑高度设置（仅对新世界生效，需重启）", cx, 70, 0xAAAAAA);
        g.drawCenteredString(this.font, "§7原版地表上限: " + BASE + " 格", cx, 95, 0xCCCCCC);

        g.drawCenteredString(this.font, "§7倍数: §f" + mul + "x", cx - 40, 165, 0xFFFFFF);
        g.drawCenteredString(this.font, "§e= 实际高度: " + (BASE * mul) + " 格", cx + 80, 165, 0xFFFF55);

        g.drawCenteredString(this.font, "§7建议 3-5 倍（1152-1920），高度须为 16 的倍数", cx, 190, 0x888888);
        g.drawCenteredString(this.font, "§8修改后新建世界生效，现有世界不变", cx, height - 20, 0x666666);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}

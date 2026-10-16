package com.xxsx.builder.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ModConfigScreen extends Screen {
    public ModConfigScreen() {
        super(Component.literal("xxsx 智能核心"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx - 30, 118, 60, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.fill(0, 0, this.width, this.height, 0xD0101018);
        g.centeredText(font, "xxsx 的智能核心", this.width / 2, 32, 0xFFFFFFFF);
        g.centeredText(font, "v1.0.5 已装载", this.width / 2, 48, 0xFFD8F4FF);
        g.centeredText(font, "Forge 26.2 启动配置界面（稳定回退版）", this.width / 2, 64, 0xFFAAB6C2);
        g.centeredText(font, "当前版本先确保不崩；完整配置 UI 后续再恢复。", this.width / 2, 80, 0xFFAAAAAA);
        g.centeredText(font, "进入世界后可继续使用 /ai、/ai api、/ai model、/ai addmodel。", this.width / 2, 92, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(null);
        } else {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}

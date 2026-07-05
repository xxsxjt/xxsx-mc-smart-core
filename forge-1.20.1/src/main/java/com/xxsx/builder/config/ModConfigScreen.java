package com.xxsx.builder.config;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.ai.ExternalProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModConfigScreen extends Screen {
    // === 世界高度 ===
    private EditBox mulBox;
    private int mul = 4;
    private static final int BASE = 384;
    private static final int MIN_MUL = 1, MAX_MUL = 16;

    // === API 配置 ===
    private EditBox urlBox, keyBox, modelBox, ctxBox;
    private boolean modelListOpen;
    private int modelListScroll;
    private List<String> fetchedModels = new ArrayList<>();
    private CompletableFuture<List<String>> fetchFuture;
    private String statusMsg = "";

    // 布局常量
    private int panelLeft, panelW, cx;
    private static final int LEFT_W = 70;

    public ModConfigScreen() {
        super(Component.literal("xxsx 智能核心"));
    }

    @Override
    protected void init() {
        cx = this.width / 2;
        panelW = 300;
        panelLeft = cx - panelW / 2;

        // ---- 世界高度 ----
        int hy = 48;
        mulBox = new EditBox(font, cx - 14, hy + 42, 32, 16, Component.literal("mul"));
        mulBox.setValue(String.valueOf(mul));
        mulBox.setFilter(s -> s.matches("\\d{0,2}"));
        mulBox.setResponder(s -> {
            try { int v = Integer.parseInt(s); if (v >= MIN_MUL && v <= MAX_MUL) mul = v; }
            catch (Exception ignored) {}
        });
        addRenderableWidget(mulBox);
        addRenderableWidget(Button.builder(Component.literal("§l-"), b -> {
            if (mul > MIN_MUL) { mul--; mulBox.setValue(String.valueOf(mul)); }
        }).bounds(cx - 54, hy + 41, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§l+"), b -> {
            if (mul < MAX_MUL) { mul++; mulBox.setValue(String.valueOf(mul)); }
        }).bounds(cx + 36, hy + 41, 18, 18).build());

        // ---- API 配置 ----
        int ay = 165;
        urlBox = new EditBox(font, panelLeft + LEFT_W, ay, panelW - LEFT_W, 16, Component.literal("url"));
        urlBox.setMaxLength(256);
        urlBox.setValue(XxsxBuilder.getInstance().getConfig().providerUrl());
        addRenderableWidget(urlBox);

        keyBox = new EditBox(font, panelLeft + LEFT_W, ay + 26, panelW - LEFT_W, 16, Component.literal("key"));
        keyBox.setMaxLength(256);
        keyBox.setValue(XxsxBuilder.getInstance().getConfig().providerKey());
        addRenderableWidget(keyBox);

        addRenderableWidget(Button.builder(Component.literal("§b获取模型列表"), b -> fetchModels())
                .bounds(panelLeft + LEFT_W, ay + 48, 80, 18).build());

        modelBox = new EditBox(font, panelLeft + LEFT_W, ay + 72, panelW - LEFT_W - 22, 16, Component.literal("model"));
        modelBox.setMaxLength(128);
        modelBox.setValue(XxsxBuilder.getInstance().getConfig().getEffectiveModel());
        addRenderableWidget(modelBox);

        addRenderableWidget(Button.builder(Component.literal(modelListOpen ? "▲" : "▼"), b -> {
            modelListOpen = !modelListOpen;
            modelListScroll = 0;
            clearWidgets();
            init();
        }).bounds(panelLeft + panelW - 18, ay + 71, 18, 18).build());

        ctxBox = new EditBox(font, panelLeft + LEFT_W, ay + 94, 60, 16, Component.literal("ctx"));
        ctxBox.setFilter(s -> s.matches("\\d*"));
        ctxBox.setValue("200000");
        addRenderableWidget(ctxBox);

        addRenderableWidget(Button.builder(Component.literal("§a✓ 一键设置"), b -> applyAndClose())
                .bounds(panelLeft + LEFT_W, ay + 120, 80, 18).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(panelLeft + LEFT_W + 90, ay + 120, 50, 18).build());
    }

    private void fetchModels() {
        String url = urlBox.getValue().trim();
        String key = keyBox.getValue().trim();
        if (url.isEmpty() || key.isEmpty()) {
            statusMsg = "§c请先填写 URL 和 Key";
            return;
        }
        statusMsg = "§e获取中...";
        fetchFuture = CompletableFuture.supplyAsync(() -> ExternalProvider.fetchModels(url, key));
    }

    private void applyAndClose() {
        XxsxBuilder.getInstance().getConfig().applyConfig(
            urlBox.getValue().trim(),
            keyBox.getValue().trim(),
            modelBox.getValue().trim()
        );
        XxsxBuilder.getInstance().getConfig().saveToFile();
        XxsxBuilder.getInstance().getSessionManager().reloadProvider();
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        this.renderBackground(g);
        super.render(g, mx, my, delta);

        // 异步获取结果
        if (fetchFuture != null && fetchFuture.isDone()) {
            try {
                fetchedModels = fetchFuture.get();
                modelListOpen = !fetchedModels.isEmpty();
                statusMsg = fetchedModels.isEmpty() ? "§c未获取到模型" : "§a已获取 " + fetchedModels.size() + " 个模型";
            } catch (Exception e) {
                statusMsg = "§c获取失败";
                fetchedModels = new ArrayList<>();
            }
            fetchFuture = null;
        }

        // === 标题 ===
        g.drawCenteredString(font, "§b§lxxsx 的智能核心", cx, 12, 0xFFFFFF);
        g.drawCenteredString(font, "§7v1.0.5  ·  AI 助手  ·  PMX 体素建筑", cx, 26, 0x888888);

        // === 世界高度区 ===
        int hy = 48;
        g.fill(panelLeft, hy, panelLeft + panelW, hy + 95, 0x18FFFFFF);
        g.drawCenteredString(font, "§e◆  世界高度  ◆", cx, hy + 6, 0xFFFF55);
        g.drawCenteredString(font, "§7原版地表  §f" + BASE + "  §7→  高度 = " + BASE + " × 倍数", cx, hy + 20, 0xAAAAAA);
        g.drawCenteredString(font, "§7倍数  §f" + mul + "x  §7= 实际  §e" + (BASE * mul) + " 格", cx, hy + 56, 0xFFFFFF);
        int bl = panelLeft + 30, br = panelLeft + panelW - 30, by = hy + 74, bh = 6;
        g.fill(bl, by, br, by + bh, 0x30FFFFFF);
        float ratio = (float)(mul - MIN_MUL) / (MAX_MUL - MIN_MUL);
        int fw = (int)((br - bl) * ratio);
        int bc = ratio < 0.5f ? lerpColor(0xFF55FF55, 0xFFFFFF55, ratio * 2)
                : lerpColor(0xFFFFFF55, 0xFFFF5555, (ratio - 0.5f) * 2);
        g.fill(bl, by, bl + fw, by + bh, bc);
        g.drawCenteredString(font, "§8仅对新世界生效", cx, hy + 82, 0x666666);

        // === 分隔线 ===
        int ay = 160;
        g.fill(panelLeft, ay, panelLeft + panelW, ay + 1, 0x40FFFFFF);
        g.drawCenteredString(font, "§e◆  API 配置  ◆", cx, ay + 4, 0xFFFF55);

        // 标签
        g.drawString(font, "§7API 地址", panelLeft + 4, ay + 22, 0xAAAAAA);
        g.drawString(font, "§7Key", panelLeft + 4, ay + 48, 0xAAAAAA);
        g.drawString(font, "§7模型", panelLeft + 4, ay + 74, 0xAAAAAA);
        g.drawString(font, "§7上下文", panelLeft + 4, ay + 96, 0xAAAAAA);
        String ctxStr = ctxBox.getValue();
        g.drawString(font, "§8" + (ctxStr.isEmpty() ? "200k" : (Integer.parseInt(ctxStr)/1000) + "k") + " tokens",
                panelLeft + LEFT_W + 66, ay + 96, 0x888888);

        if (!statusMsg.isEmpty())
            g.drawCenteredString(font, statusMsg, cx, ay + 42, 0xFFFF55);

        // === 模型列表（展开时） ===
        if (modelListOpen && !fetchedModels.isEmpty()) {
            int listY = ay + 90;
            int listH = Math.min(fetchedModels.size(), 8) * 12 + 4;
            g.fill(panelLeft + LEFT_W, listY, panelLeft + panelW, listY + listH, 0xCC222222);
            g.fill(panelLeft + LEFT_W, listY, panelLeft + panelW, listY + 1, 0x80FFFFFF);
            int start = Math.max(0, Math.min(modelListScroll, fetchedModels.size() - 8));
            for (int i = start; i < Math.min(start + 8, fetchedModels.size()); i++) {
                int iy = listY + 2 + (i - start) * 12;
                boolean hover = mx >= panelLeft + LEFT_W && mx <= panelLeft + panelW && my >= iy && my < iy + 12;
                if (hover) g.fill(panelLeft + LEFT_W, iy, panelLeft + panelW, iy + 12, 0x40FFFFFF);
                String label = fetchedModels.get(i).length() > 35
                        ? fetchedModels.get(i).substring(0, 34) + "…" : fetchedModels.get(i);
                g.drawString(font, hover ? "§e" + label : "§7" + label,
                        panelLeft + LEFT_W + 2, iy + 2, 0xFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (modelListOpen && !fetchedModels.isEmpty()) {
            int listY = 255;
            int start = Math.max(0, Math.min(modelListScroll, fetchedModels.size() - 8));
            for (int i = start; i < Math.min(start + 8, fetchedModels.size()); i++) {
                int iy = listY + 2 + (i - start) * 12;
                if (mx >= panelLeft + LEFT_W && mx <= panelLeft + panelW && my >= iy && my < iy + 12) {
                    modelBox.setValue(fetchedModels.get(i).replace(" (web)", "-web"));
                    modelListOpen = false;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scroll) {
        if (modelListOpen) {
            modelListScroll = Math.max(0, modelListScroll + (int) Math.signum(scroll));
            return true;
        }
        return super.mouseScrolled(mx, my, scroll);
    }

    private static int lerpColor(int c1, int c2, float t) {
        int r = (int)(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int)(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int)((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}

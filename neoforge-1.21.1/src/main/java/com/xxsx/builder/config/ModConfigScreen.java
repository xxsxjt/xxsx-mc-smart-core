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
import net.minecraft.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModConfigScreen extends Screen {
    // === 世界高度 ===
    private EditBox mulBox;
    private int mul = 1;
    private static final int BASE = 384;
    private static final int MIN_MUL = 1, MAX_MUL = 16;

    // === API 配置 ===
    private EditBox urlBox, keyBox, modelBox, ctxBox;
    private boolean modelListOpen;
    private int modelListScroll;
    private List<String> fetchedModels = new ArrayList<>();
    private CompletableFuture<List<String>> fetchFuture;
    private String statusMsg = "";

    // === 布局常量 ===
    private int panelLeft, panelW, cx;
    private static final int LEFT_W = 70; // 标签宽度

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

        // 获取模型按钮
        addRenderableWidget(Button.builder(Component.literal("§b获取模型列表"), b -> fetchModels())
                .bounds(panelLeft + LEFT_W, ay + 48, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§a使用内置免费模型"), b -> useBuiltinModel())
                .bounds(panelLeft + LEFT_W + 86, ay + 48, 92, 18).build());

        modelBox = new EditBox(font, panelLeft + LEFT_W, ay + 72, panelW - LEFT_W - 22, 16, Component.literal("model"));
        modelBox.setMaxLength(128);
        modelBox.setValue(XxsxBuilder.getInstance().getConfig().getEffectiveModel());
        addRenderableWidget(modelBox);

        // 展开/收起模型列表按钮
        addRenderableWidget(Button.builder(Component.literal(modelListOpen ? "▲" : "▼"), b -> {
            modelListOpen = !modelListOpen;
            modelListScroll = 0;
            clearWidgets();
            init();
        }).bounds(panelLeft + panelW - 18, ay + 71, 18, 18).build());

        // 上下文长度
        ctxBox = new EditBox(font, panelLeft + LEFT_W, ay + 94, 60, 16, Component.literal("ctx"));
        ctxBox.setFilter(s -> s.matches("\\d*"));
        ctxBox.setValue("200000");
        addRenderableWidget(ctxBox);

        // 一键设置按钮
        addRenderableWidget(Button.builder(Component.literal("§a✓ 一键设置"), b -> applyAndClose())
                .bounds(panelLeft + LEFT_W, ay + 120, 80, 18).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(panelLeft + LEFT_W + 90, ay + 120, 50, 18).build());
        addRenderableWidget(Button.builder(Component.literal("免费模型说明"), b -> openExternalUrl("https://kilo.ai", "免费模型说明"))
                .bounds(getTopLinkX(), 8, 86, 18).build());
        addRenderableWidget(Button.builder(Component.literal("DeepSeek官网"), b -> openExternalUrl("https://platform.deepseek.com/", "DeepSeek官网"))
                .bounds(getTopLinkX(), 30, 86, 18).build());
    }

    private void useBuiltinModel() {
        var config = XxsxBuilder.getInstance().getConfig();
        config.switchToProvider("Free Models");
        urlBox.setValue(config.providerUrl());
        keyBox.setValue(config.providerKey());
        modelBox.setValue(config.getEffectiveModel());
        statusMsg = "§a已切换到内置 Kilo 免费模型（Beam → Auto → Flint）";
    }

    private void fetchModels() {
        String url = urlBox.getValue().trim();
        String key = keyBox.getValue().trim();
        if (url.isEmpty() || key.isEmpty()) {
            statusMsg = "§c请先填写 URL 和 Key";
            return;
        }
        statusMsg = "§e获取中...";
        fetchedModels = new ArrayList<>();
        modelListOpen = false;
        fetchFuture = CompletableFuture.supplyAsync(() -> ExternalProvider.fetchModels(url, key));
    }

    private void applyAndClose() {
        XxsxBuilder.getInstance().getConfig().applyConfig(
            urlBox.getValue().trim(),
            keyBox.getValue().trim(),
            modelBox.getValue().trim(),
            getContextMaxTokens()
        );
        XxsxBuilder.getInstance().getConfig().saveToFile();
        XxsxBuilder.getInstance().getSessionManager().reloadProvider();
        onClose();
    }

    private void openExternalUrl(String url, String label) {
        try {
            Util.getPlatform().openUri(new URI(url));
            statusMsg = "§a已打开 " + label;
        } catch (Exception e) {
            statusMsg = "§c打开失败: " + label;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        this.renderBackground(g, mx, my, delta);
        super.render(g, mx, my, delta);

        // 检查异步获取结果
        if (fetchFuture != null && fetchFuture.isDone()) {
            try {
                List<String> models = fetchFuture.get();
                fetchedModels = models != null ? models : new ArrayList<>();
                modelListOpen = !fetchedModels.isEmpty();
                statusMsg = fetchedModels.isEmpty() ? "§c未获取到模型" : "§a已获取 " + fetchedModels.size() + " 个模型";
            } catch (Exception e) {
                String msg = e.getMessage();
                statusMsg = "§c获取失败: " + (msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg);
                fetchedModels = new ArrayList<>();
                modelListOpen = false;
            }
            fetchFuture = null;
        }

        // === 标题 ===
        drawCenteredStr(g, "§b§lxxsx 的智能核心", cx, 12, 0xFFFFFF);
        drawCenteredStr(g, "§7v1.1.1  ·  AI 助手  ·  PMX 体素建筑", cx, 26, 0x888888);

        // === 世界高度区 ===
        int hy = 48;
        g.fill(panelLeft, hy, panelLeft + panelW, hy + 95, 0x18FFFFFF);
        drawCenteredStr(g, "§e◆  世界高度  ◆", cx, hy + 6, 0xFFFF55);
        drawStr(g, "§7注：-64，+320", panelLeft - 4, hy + 20, 0xAAAAAA);
        drawStr(g, "§7倍数  §f" + mul + "x  §7= 实际  §e" + (BASE * mul) + " 格", panelLeft + panelW / 2 + 58, hy + 56, 0xFFFFFF);
        // 小可视化条
        int bl = panelLeft + 30, br = panelLeft + panelW - 30, by = hy + 74, bh = 6;
        g.fill(bl, by, br, by + bh, 0x30FFFFFF);
        float ratio = (float)(mul - MIN_MUL) / (MAX_MUL - MIN_MUL);
        int fw = (int)((br - bl) * ratio);
        int bc = ratio < 0.5f ? lerpColor(0xFF55FF55, 0xFFFFFF55, ratio * 2)
                : lerpColor(0xFFFFFF55, 0xFFFF5555, (ratio - 0.5f) * 2);
        g.fill(bl, by, bl + fw, by + bh, bc);
        drawCenteredStr(g, "§8仅对新世界生效", cx, hy + 82, 0x666666);

        // === 分隔线 ===
        int ay = getApiSectionTop();
        g.fill(panelLeft, ay, panelLeft + panelW, ay + 1, 0x40FFFFFF);

        // === API 配置区标题 ===
        drawCenteredStr(g, "§e◆  API 配置  ◆", cx, ay + 4, 0xFFFF55);

        // URL
        drawStr(g, "§7API 地址", panelLeft + 4, ay + 22, 0xAAAAAA);
        // Key
        drawStr(g, "§7Key", panelLeft + 4, ay + 48, 0xAAAAAA);
        // 模型
        drawStr(g, "§7模型", panelLeft + 4, ay + 74, 0xAAAAAA);
        // 上下文
        drawStr(g, "§7上下文", panelLeft + 4, ay + 96, 0xAAAAAA);
        drawStr(g, "§8" + getCtxLabel(), panelLeft + LEFT_W + 66, ay + 96, 0x888888);

        // 状态信息
        if (!statusMsg.isEmpty()) {
            drawCenteredStr(g, statusMsg, cx, ay + 42, 0xFFFF55);
        }

        // === 模型列表（展开时） ===
        if (modelListOpen && !fetchedModels.isEmpty()) {
            int listY = getModelListY();
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
                drawStr(g, hover ? "§e" + label : "§7" + label,
                        panelLeft + LEFT_W + 2, iy + 2, 0xFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 模型列表点击检测
        if (modelListOpen && !fetchedModels.isEmpty()) {
            int listY = getModelListY();
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
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (modelListOpen) {
            modelListScroll = Math.max(0, modelListScroll + (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private int getApiSectionTop() {
        return 160;
    }

    private int getModelListY() {
        return getApiSectionTop() + 90;
    }

    private int getTopLinkX() {
        return Math.max(this.width - 96, 4);
    }

    private int getContextMaxTokens() {
        if (ctxBox == null) return 200000;
        String value = ctxBox.getValue();
        if (value == null || value.isBlank()) return 200000;
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 200000;
        }
    }

    private String getCtxLabel() {
        return (getContextMaxTokens() / 1000) + "k tokens";
    }

    private static int lerpColor(int c1, int c2, float t) {
        int r = (int)(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int)(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int)((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    // MC 1.21.1: drawString/drawCenteredString 用 String 参数
    private void drawStr(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color);
    }

    private void drawCenteredStr(GuiGraphics g, String text, int centerX, int y, int color) {
        g.drawCenteredString(font, text, centerX, y, color);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}

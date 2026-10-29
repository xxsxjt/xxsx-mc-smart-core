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
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModConfigScreen extends Screen {
    private static final int BASE = 384;
    private static final int MIN_MUL = 1;
    private static final int MAX_MUL = 16;
    private static final int PANEL_W = 300;
    private static final int LEFT_W = 70;
    private static final int FIELD_H = 18;

    private EditBox mulBox;
    private EditBox urlBox;
    private EditBox keyBox;
    private EditBox modelBox;
    private EditBox ctxBox;
    private int mul = 1;
    private boolean modelListOpen;
    private int modelListScroll;
    private List<String> fetchedModels = new ArrayList<>();
    private CompletableFuture<List<String>> fetchFuture;
    private String statusMsg = "";
    private int panelLeft;
    private int cx;

    public ModConfigScreen() {
        super(Component.literal("xxsx 智能核心"));
    }

    @Override
    protected void init() {
        cx = this.width / 2;
        panelLeft = cx - PANEL_W / 2;

        int heightY = getWorldTop() + 18;
        mulBox = new EditBox(font, cx - 14, heightY, 32, 16, Component.literal("mul"));
        mulBox.setValue(String.valueOf(mul));
        mulBox.setFilter(s -> s.matches("\\d{0,2}"));
        mulBox.setResponder(s -> {
            try {
                int v = Integer.parseInt(s);
                if (v >= MIN_MUL && v <= MAX_MUL) mul = v;
            } catch (Exception ignored) {
            }
        });
        addRenderableWidget(mulBox);
        addRenderableWidget(Button.builder(Component.literal("§l-"), b -> {
            if (mul > MIN_MUL) {
                mul--;
                mulBox.setValue(String.valueOf(mul));
            }
        }).bounds(cx - 54, heightY - 1, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§l+"), b -> {
            if (mul < MAX_MUL) {
                mul++;
                mulBox.setValue(String.valueOf(mul));
            }
        }).bounds(cx + 36, heightY - 1, 18, 18).build());

        urlBox = new EditBox(font, fieldX(), getUrlY(), fieldW(), FIELD_H, Component.literal("url"));
        urlBox.setMaxLength(256);
        urlBox.setValue(XxsxBuilder.getInstance().getConfig().providerUrl());
        addRenderableWidget(urlBox);

        keyBox = new EditBox(font, fieldX(), getKeyY(), fieldW(), FIELD_H, Component.literal("key"));
        keyBox.setMaxLength(256);
        keyBox.setValue(XxsxBuilder.getInstance().getConfig().providerKey());
        addRenderableWidget(keyBox);

        addRenderableWidget(Button.builder(Component.literal("§b获取模型列表"), b -> fetchModels())
                .bounds(fieldX(), getFetchY(), 96, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§a使用内置免费模型"), b -> useBuiltinModel())
                .bounds(fieldX() + 102, getFetchY(), 90, 18).build());

        modelBox = new EditBox(font, fieldX(), getModelY(), fieldW() - 22, FIELD_H, Component.literal("model"));
        modelBox.setMaxLength(128);
        modelBox.setValue(XxsxBuilder.getInstance().getConfig().getEffectiveModel());
        addRenderableWidget(modelBox);

        addRenderableWidget(Button.builder(Component.literal(modelListOpen ? "▲" : "▼"), b -> {
            modelListOpen = !modelListOpen;
            modelListScroll = 0;
        }).bounds(fieldX() + fieldW() - 18, getModelY(), 18, 18).build());

        ctxBox = new EditBox(font, fieldX(), getContextY(), 60, FIELD_H, Component.literal("ctx"));
        ctxBox.setFilter(s -> s.matches("\\d*"));
        ctxBox.setValue(String.valueOf(getStoredContextTokens()));
        addRenderableWidget(ctxBox);

        addRenderableWidget(Button.builder(Component.literal("§a✓ 一键设置"), b -> applyAndClose())
                .bounds(fieldX(), getActionY(), 84, 18).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(fieldX() + 92, getActionY(), 50, 18).build());
        addRenderableWidget(Button.builder(Component.literal("免费模型说明"), b -> openExternalUrl("https://kilo.ai", "免费模型说明"))
                .bounds(getTopLinkX(), 8, 86, 18).build());
        addRenderableWidget(Button.builder(Component.literal("DeepSeek官网"), b -> openExternalUrl("https://platform.deepseek.com/", "DeepSeek官网"))
                .bounds(getTopLinkX(), 30, 86, 18).build());
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
        modelListScroll = 0;
        fetchFuture = CompletableFuture.supplyAsync(() -> ExternalProvider.fetchModels(url, key));
    }

    private void useBuiltinModel() {
        var config = XxsxBuilder.getInstance().getConfig();
        config.switchToProvider("Free Models");
        urlBox.setValue(config.providerUrl());
        keyBox.setValue(config.providerKey());
        modelBox.setValue(config.getEffectiveModel());
        statusMsg = "§a已切换到内置 Kilo 免费模型（Beam → Auto → Flint）";
    }

    private void openExternalUrl(String url, String label) {
        try {
            Util.getPlatform().openUri(new URI(url));
            statusMsg = "§a已打开 " + label;
        } catch (Exception e) {
            statusMsg = "§c打开失败: " + label;
        }
    }

    private void applyAndClose() {
        XxsxBuilder.getInstance().getConfig().applyConfig(
                urlBox.getValue().trim(),
                keyBox.getValue().trim(),
                modelBox.getValue().trim(),
                getContextMaxTokens()
        );
        XxsxBuilder.getInstance().getConfig().saveToFile();
        var sessionManager = XxsxBuilder.getInstance().getSessionManager();
        if (sessionManager != null) {
            sessionManager.reloadProvider();
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);

        if (fetchFuture != null && fetchFuture.isDone()) {
            try {
                List<String> models = fetchFuture.get();
                fetchedModels = models != null ? models : new ArrayList<>();
                modelListOpen = !fetchedModels.isEmpty();
                modelListScroll = 0;
                statusMsg = fetchedModels.isEmpty() ? "§c未获取到模型" : "§a已获取 " + fetchedModels.size() + " 个模型";
            } catch (Exception e) {
                String msg = e.getMessage();
                statusMsg = "§c获取失败: " + (msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg);
                fetchedModels = new ArrayList<>();
                modelListOpen = false;
            }
            fetchFuture = null;
        }

        drawCenteredStr(g, "§b§lxxsx 的智能核心", cx, 10, 0xFFFFFF);
        drawCenteredStr(g, "§b[xxsx的智能核心] §fv1.1.2 已装载 - MC 1.21.11", cx, 22, 0xD8F4FF);
        drawCenteredStr(g, "§7这是启动配置界面：先设置 API，再用 /ai 与智能核心对话", cx, 34, 0xAAB6C2);

        int worldTop = getWorldTop();
        int apiTop = getApiTop();

        g.fill(panelLeft, worldTop, panelLeft + PANEL_W, worldTop + 42, 0x18FFFFFF);
        drawCenteredStr(g, "§e◆ 世界高度 ◆", cx, worldTop + 4, 0xFFFF55);
        drawStr(g, "§7注：-64，+320", panelLeft - 4, worldTop + 14, 0xAAAAAA);
        drawStr(g, "§7倍数 §f" + mul + "x  §7= 实际 §e" + (BASE * mul) + " 格", panelLeft + PANEL_W / 2 + 58, worldTop + 27, 0xFFFFFF);
        int bl = panelLeft + 30;
        int br = panelLeft + PANEL_W - 30;
        int by = worldTop + 36;
        g.fill(bl, by, br, by + 4, 0x30FFFFFF);
        float ratio = (float) (mul - MIN_MUL) / (MAX_MUL - MIN_MUL);
        int fw = (int) ((br - bl) * ratio);
        int bc = ratio < 0.5f ? lerpColor(0xFF55FF55, 0xFFFFFF55, ratio * 2)
                : lerpColor(0xFFFFFF55, 0xFFFF5555, (ratio - 0.5f) * 2);
        g.fill(bl, by, bl + fw, by + 4, bc);

        g.fill(panelLeft, apiTop, panelLeft + PANEL_W, getApiBottom(), 0x18FFFFFF);
        g.fill(panelLeft, apiTop, panelLeft + PANEL_W, apiTop + 1, 0x40FFFFFF);
        drawCenteredStr(g, "§e◆ API 配置 ◆", cx, apiTop + 4, 0xFFFF55);

        if (statusMsg.isEmpty()) {
            drawCenteredStr(g, "§8先填 URL + Key，再点获取模型；模型也可手填", cx, apiTop + 16, 0x808080);
        } else {
            drawCenteredStr(g, statusMsg, cx, apiTop + 16, 0xFFFF55);
        }

        drawStr(g, "§7API 地址", panelLeft + 4, getUrlY() + 2, 0xAAAAAA);
        drawStr(g, "§7Key", panelLeft + 4, getKeyY() + 2, 0xAAAAAA);
        drawStr(g, "§7模型", panelLeft + 4, getModelY() + 2, 0xAAAAAA);
        drawStr(g, "§7上下文", panelLeft + 4, getContextY() + 2, 0xAAAAAA);
        drawStr(g, "§8" + getCtxLabel(), fieldX() + 66, getContextY() + 2, 0x888888);
        drawCenteredStr(g, "§8内置 Kilo 免费模型无需 Key，依次尝试 Beam → Auto → Flint", cx, apiTop + 134, 0x707070);
        drawCenteredStr(g, "§8保存后会写入 common.toml，重新进游戏仍然生效", cx, apiTop + 144, 0x707070);

        if (modelListOpen && !fetchedModels.isEmpty()) {
            int listY = getListY();
            int listH = getListHeight();
            g.fill(fieldX(), listY, fieldX() + fieldW(), listY + listH, 0xCC222222);
            g.fill(fieldX(), listY, fieldX() + fieldW(), listY + 1, 0x80FFFFFF);
            int visible = getVisibleModelCount();
            int maxStart = Math.max(0, fetchedModels.size() - visible);
            int start = Math.max(0, Math.min(modelListScroll, maxStart));
            for (int i = start; i < Math.min(start + visible, fetchedModels.size()); i++) {
                int iy = listY + 2 + (i - start) * 12;
                boolean hover = mx >= fieldX() && mx <= fieldX() + fieldW() && my >= iy && my < iy + 12;
                if (hover) g.fill(fieldX(), iy, fieldX() + fieldW(), iy + 12, 0x40FFFFFF);
                String label = shortenModelLabel(fetchedModels.get(i));
                drawStr(g, hover ? "§e" + label : "§7" + label, fieldX() + 2, iy + 2, 0xFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
        double mx = event.x();
        double my = event.y();
        if (modelListOpen && !fetchedModels.isEmpty()) {
            int listY = getListY();
            int visible = getVisibleModelCount();
            int maxStart = Math.max(0, fetchedModels.size() - visible);
            int start = Math.max(0, Math.min(modelListScroll, maxStart));
            for (int i = start; i < Math.min(start + visible, fetchedModels.size()); i++) {
                int iy = listY + 2 + (i - start) * 12;
                if (mx >= fieldX() && mx <= fieldX() + fieldW() && my >= iy && my < iy + 12) {
                    modelBox.setValue(fetchedModels.get(i).replace(" (web)", "-web"));
                    modelListOpen = false;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (modelListOpen && !fetchedModels.isEmpty()) {
            int visible = getVisibleModelCount();
            int maxStart = Math.max(0, fetchedModels.size() - visible);
            if (maxStart > 0) {
                modelListScroll = Math.max(0, Math.min(maxStart, modelListScroll - (int) Math.signum(scrollY)));
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private int getWorldTop() {
        return 46;
    }

    private int getApiTop() {
        return 92;
    }

    private int fieldX() {
        return panelLeft + LEFT_W;
    }

    private int fieldW() {
        return PANEL_W - LEFT_W;
    }

    private int getUrlY() {
        return getApiTop() + 28;
    }

    private int getKeyY() {
        return getApiTop() + 46;
    }

    private int getFetchY() {
        return getApiTop() + 64;
    }

    private int getModelY() {
        return getApiTop() + 82;
    }

    private int getListY() {
        return Math.max(getApiTop() + 24, getModelY() - getListHeight() - 4);
    }

    private int getContextY() {
        return getApiTop() + 100;
    }

    private int getActionY() {
        return getApiTop() + 118;
    }

    private int getApiBottom() {
        return getApiTop() + 170;
    }

    private int getTopLinkX() {
        return Math.max(this.width - 96, 4);
    }

    private int getVisibleModelCount() {
        return Math.min(Math.max(fetchedModels.size(), 1), 4);
    }

    private int getListHeight() {
        if (!modelListOpen || fetchedModels.isEmpty()) return 0;
        return getVisibleModelCount() * 12 + 4;
    }

    private int getStoredContextTokens() {
        int stored = XxsxBuilder.getInstance().getConfig().contextMaxTokens();
        return stored > 0 ? stored : 200000;
    }

    private int getContextMaxTokens() {
        if (ctxBox == null) return getStoredContextTokens();
        String value = ctxBox.getValue();
        if (value == null || value.isBlank()) return getStoredContextTokens();
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return getStoredContextTokens();
        }
    }

    private String getCtxLabel() {
        return (getContextMaxTokens() / 1000) + "k tokens";
    }

    private String shortenModelLabel(String model) {
        if (model == null || model.isBlank()) return "未设置";
        return model.length() > 35 ? model.substring(0, 34) + "…" : model;
    }

    private static int lerpColor(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawStr(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, stripLegacyCodes(text), x, y, ensureVisibleColor(color));
    }

    private void drawCenteredStr(GuiGraphics g, String text, int centerX, int y, int color) {
        g.drawCenteredString(font, stripLegacyCodes(text), centerX, y, ensureVisibleColor(color));
    }

    private int ensureVisibleColor(int color) {
        return (color >>> 24) == 0 ? color | 0xFF000000 : color;
    }

    private String stripLegacyCodes(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}

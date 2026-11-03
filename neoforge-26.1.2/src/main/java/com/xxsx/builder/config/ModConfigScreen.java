package com.xxsx.builder.config;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.ai.ExternalProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
    private static final int ACTION_SAVE_W = 58;
    private static final int ACTION_CANCEL_W = 50;
    private static final int ACTION_DISMISS_W = 96;
    private static final int ACTION_GAP = 6;

    private EditBox mulBox;
    private EditBox urlBox;
    private EditBox keyBox;
    private EditBox modelBox;
    private EditBox ctxBox;
    private Button modelToggleButton;

    private int mul = 1;
    private boolean modelListOpen;
    private int modelListScroll;
    private List<String> fetchedModels = new ArrayList<>();
    private CompletableFuture<List<String>> fetchFuture;
    private Component statusMsg = Component.empty();
    private int statusColor = 0xFF808080;
    private int panelLeft;
    private int panelW;
    private int cx;
    private final Screen parent;

    public ModConfigScreen() {
        this(null);
    }

    public ModConfigScreen(Screen parent) {
        super(SmartCoreText.gui("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cx = this.width / 2;
        panelW = Math.min(PANEL_W, Math.max(220, this.width - 24));
        panelLeft = cx - panelW / 2;

        int heightY = getWorldTop() + 18;
        mul = XxsxBuilder.getInstance().getConfig().worldHeightMultiplier();
        mulBox = new EditBox(font, cx - 14, heightY, 32, 16, SmartCoreText.gui("config.multiplier"));
        mulBox.setValue(String.valueOf(mul));
        mulBox.setResponder(s -> {
            Integer parsed = parseInt(s);
            if (parsed != null && parsed >= MIN_MUL && parsed <= MAX_MUL) {
                mul = parsed;
            }
        });
        addRenderableWidget(mulBox);
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            if (mul > MIN_MUL) {
                mul--;
                mulBox.setValue(String.valueOf(mul));
            }
        }).bounds(cx - 54, heightY - 1, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            if (mul < MAX_MUL) {
                mul++;
                mulBox.setValue(String.valueOf(mul));
            }
        }).bounds(cx + 36, heightY - 1, 18, 18).build());

        urlBox = new EditBox(font, fieldX(), getUrlY(), fieldW(), FIELD_H, SmartCoreText.gui("config.api_url"));
        urlBox.setMaxLength(256);
        urlBox.setValue(XxsxBuilder.getInstance().getConfig().providerUrl());
        addRenderableWidget(urlBox);

        keyBox = new EditBox(font, fieldX(), getKeyY(), fieldW(), FIELD_H, SmartCoreText.gui("config.key"));
        keyBox.setMaxLength(256);
        keyBox.setValue(XxsxBuilder.getInstance().getConfig().providerKey());
        addRenderableWidget(keyBox);

        addRenderableWidget(Button.builder(SmartCoreText.gui("button.fetch_models"), b -> fetchModels())
                .bounds(fieldX(), getFetchY(), 96, 18).build());
        addRenderableWidget(Button.builder(SmartCoreText.gui("button.use_agnes"), b -> useBuiltinModel())
                .bounds(fieldX() + 102, getFetchY(), Math.max(92, fieldW() - 102), 18).build());

        modelBox = new EditBox(font, fieldX(), getModelY(), fieldW() - 22, FIELD_H, SmartCoreText.gui("config.model"));
        modelBox.setMaxLength(128);
        modelBox.setValue(XxsxBuilder.getInstance().getConfig().getEffectiveModel());
        addRenderableWidget(modelBox);

        modelToggleButton = addRenderableWidget(Button.builder(Component.literal(modelListOpen ? "^" : "v"), b -> toggleModelList())
                .bounds(fieldX() + fieldW() - 18, getModelY(), 18, 18).build());

        ctxBox = new EditBox(font, fieldX(), getContextY(), 60, FIELD_H, SmartCoreText.gui("config.context"));
        ctxBox.setValue(String.valueOf(getStoredContextTokens()));
        addRenderableWidget(ctxBox);

        int actionX = getActionX();
        addRenderableWidget(Button.builder(SmartCoreText.gui("button.save"), b -> applyAndClose())
                .bounds(actionX, getActionY(), ACTION_SAVE_W, 18).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(actionX + ACTION_SAVE_W + ACTION_GAP, getActionY(), ACTION_CANCEL_W, 18).build());
        addRenderableWidget(Button.builder(SmartCoreText.gui("button.dont_show_again"), b -> dismissAndClose())
                .bounds(actionX + ACTION_SAVE_W + ACTION_GAP + ACTION_CANCEL_W + ACTION_GAP,
                        getActionY(), ACTION_DISMISS_W, 18).build());
        addRenderableWidget(Button.builder(SmartCoreText.gui("button.agnes_site"),
                b -> openExternalUrl("https://kilo.ai", SmartCoreText.gui("button.agnes_site")))
                .bounds(getTopLinkX(), 8, 86, 18).build());
        addRenderableWidget(Button.builder(SmartCoreText.gui("button.deepseek_site"),
                b -> openExternalUrl("https://platform.deepseek.com/", SmartCoreText.gui("button.deepseek_site")))
                .bounds(getTopLinkX(), 30, 86, 18).build());
    }

    private void toggleModelList() {
        if (fetchedModels.isEmpty()) {
            setStatus(SmartCoreText.gui("status.fetch_first"), 0xFFFFFF55);
            modelListOpen = false;
        } else {
            modelListOpen = !modelListOpen;
            modelListScroll = 0;
        }
        if (modelToggleButton != null) {
            modelToggleButton.setMessage(Component.literal(modelListOpen ? "^" : "v"));
        }
    }

    private void fetchModels() {
        String url = urlBox.getValue().trim();
        String key = keyBox.getValue().trim();
        if (url.isEmpty() || key.isEmpty()) {
            setStatus(SmartCoreText.gui("status.enter_url_key"), 0xFFFF5555);
            return;
        }
        setStatus(SmartCoreText.gui("status.loading"), 0xFFFFFF55);
        fetchedModels = new ArrayList<>();
        modelListOpen = false;
        modelListScroll = 0;
        if (modelToggleButton != null) {
            modelToggleButton.setMessage(Component.literal("v"));
        }
        fetchFuture = CompletableFuture.supplyAsync(() -> ExternalProvider.fetchModels(url, key));
    }

    private void useBuiltinModel() {
        var config = XxsxBuilder.getInstance().getConfig();
        config.switchToProvider("Free Models");
        urlBox.setValue(config.providerUrl());
        keyBox.setValue(config.providerKey());
        modelBox.setValue(config.getEffectiveModel());
        setStatus(SmartCoreText.gui("status.using_agnes"), 0xFF55FF55);
    }

    private void openExternalUrl(String url, Component label) {
        try {
            Util.getPlatform().openUri(new URI(url));
            setStatus(SmartCoreText.gui("status.opened", label), 0xFF55FF55);
        } catch (Exception e) {
            setStatus(SmartCoreText.gui("status.open_failed", label), 0xFFFF5555);
        }
    }

    private void applyAndClose() {
        XxsxBuilder.getInstance().getConfig().applyConfig(
                urlBox.getValue().trim(),
                keyBox.getValue().trim(),
                modelBox.getValue().trim(),
                getContextMaxTokens()
        );
        XxsxBuilder.getInstance().getConfig().setWorldHeightMultiplier(mul);
        XxsxBuilder.getInstance().getConfig().saveToFile();
        var sessionManager = XxsxBuilder.getInstance().getSessionManager();
        if (sessionManager != null) {
            sessionManager.reloadProvider();
        }
        onClose();
    }

    private void dismissAndClose() {
        var config = XxsxBuilder.getInstance().getConfig();
        config.setShowStartupConfig(false);
        config.saveToFile();
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        pollFetchFuture();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        pollFetchFuture();

        g.fill(0, 0, this.width, this.height, 0xD0101018);
        drawCenteredStr(g, SmartCoreText.gui("title"), cx, 10, 0xFFFFFFFF);
        drawCenteredStr(g, SmartCoreText.gui("config.version", XxsxBuilder.MOD_VERSION), cx, 22, 0xFFD8F4FF);
        drawCenteredStr(g, SmartCoreText.gui("config.intro"), cx, 34, 0xFFAAB6C2);

        int worldTop = getWorldTop();
        int apiTop = getApiTop();

        g.fill(panelLeft, worldTop, panelLeft + panelW, worldTop + 42, 0x24FFFFFF);
        g.outline(panelLeft, worldTop, panelW, 42, 0x28FFFFFF);
        drawCenteredStr(g, SmartCoreText.gui("config.world_height"), cx, worldTop + 4, 0xFFFFFF55);
        drawStr(g, SmartCoreText.gui("config.world_height_note"), panelLeft - 4, worldTop + 14, 0xFFAAAAAA);
        Component multiplierLabel = SmartCoreText.gui("config.multiplier", mul, BASE * mul);
        int multiplierX = Math.min(this.width - font.width(multiplierLabel.getString()) - 4,
            panelLeft + panelW / 2 + 58);
        drawStr(g, multiplierLabel, multiplierX, worldTop + 27, 0xFFFFFFFF);
        int bl = panelLeft + 30;
        int br = panelLeft + panelW - 30;
        int by = worldTop + 36;
        g.fill(bl, by, br, by + 4, 0x30FFFFFF);
        float ratio = (float) (mul - MIN_MUL) / (MAX_MUL - MIN_MUL);
        int fw = (int) ((br - bl) * ratio);
        int bc = ratio < 0.5f ? lerpColor(0xFF55FF55, 0xFFFFFF55, ratio * 2)
                : lerpColor(0xFFFFFF55, 0xFFFF5555, (ratio - 0.5f) * 2);
        g.fill(bl, by, bl + fw, by + 4, bc);

        g.fill(panelLeft, apiTop, panelLeft + panelW, getApiBottom(), 0x24FFFFFF);
        g.outline(panelLeft, apiTop, panelW, getApiBottom() - apiTop, 0x28FFFFFF);
        drawCenteredStr(g, SmartCoreText.gui("config.api"), cx, apiTop + 4, 0xFFFFFF55);
        boolean hasStatus = !statusMsg.getString().isEmpty();
        drawCenteredStr(g, trimToWidth((hasStatus ? statusMsg : SmartCoreText.gui("config.api_hint")).getString(), panelW - 8),
                cx, apiTop + 16, hasStatus ? statusColor : 0xFF808080);

        drawStr(g, SmartCoreText.gui("config.api_url"), panelLeft + 4, getUrlY() + 2, 0xFFAAAAAA);
        drawStr(g, SmartCoreText.gui("config.key"), panelLeft + 4, getKeyY() + 2, 0xFFAAAAAA);
        drawStr(g, SmartCoreText.gui("config.model"), panelLeft + 4, getModelY() + 2, 0xFFAAAAAA);
        drawStr(g, SmartCoreText.gui("config.context"), panelLeft + 4, getContextY() + 2, 0xFFAAAAAA);
        drawStr(g, getCtxLabel(), fieldX() + 66, getContextY() + 2, 0xFF888888);
        drawCenteredStr(g, trimToWidth(SmartCoreText.gui("config.provider_note").getString(), panelW - 8),
            cx, apiTop + 134, 0xFF707070);
        drawCenteredStr(g, trimToWidth(SmartCoreText.gui("config.save_note").getString(), panelW - 8),
            cx, apiTop + 144, 0xFF707070);

        super.extractRenderState(g, mx, my, delta);

        if (modelListOpen && !fetchedModels.isEmpty()) {
            extractModelList(g, mx, my);
        }
    }

    private void pollFetchFuture() {
        if (fetchFuture == null || !fetchFuture.isDone()) {
            return;
        }
        try {
            List<String> models = fetchFuture.get();
            fetchedModels = models != null ? models : new ArrayList<>();
            modelListOpen = !fetchedModels.isEmpty();
            modelListScroll = 0;
            setStatus(fetchedModels.isEmpty()
                    ? SmartCoreText.gui("status.no_models")
                    : SmartCoreText.gui("status.models_fetched", fetchedModels.size()),
                    fetchedModels.isEmpty() ? 0xFFFF5555 : 0xFF55FF55);
        } catch (Exception e) {
            String msg = e.getMessage();
            setStatus(SmartCoreText.gui("status.fetch_failed",
                    msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg), 0xFFFF5555);
            fetchedModels = new ArrayList<>();
            modelListOpen = false;
        }
        if (modelToggleButton != null) {
            modelToggleButton.setMessage(Component.literal(modelListOpen ? "^" : "v"));
        }
        fetchFuture = null;
    }

    private void extractModelList(GuiGraphicsExtractor g, int mx, int my) {
        int listY = getListY();
        int listH = getListHeight();
        g.fill(fieldX(), listY, fieldX() + fieldW(), listY + listH, 0xEE222222);
        g.outline(fieldX(), listY, fieldW(), listH, 0x80FFFFFF);
        int visible = getVisibleModelCount();
        int maxStart = Math.max(0, fetchedModels.size() - visible);
        int start = Math.max(0, Math.min(modelListScroll, maxStart));
        for (int i = start; i < Math.min(start + visible, fetchedModels.size()); i++) {
            int iy = listY + 2 + (i - start) * 12;
            boolean hover = mx >= fieldX() && mx <= fieldX() + fieldW() && my >= iy && my < iy + 12;
            if (hover) {
                g.fill(fieldX(), iy, fieldX() + fieldW(), iy + 12, 0x40FFFFFF);
            }
            String label = shortenModelLabel(fetchedModels.get(i));
            drawStr(g, label, fieldX() + 2, iy + 2, hover ? 0xFFFFFF55 : 0xFFCCCCCC);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
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
                    if (modelToggleButton != null) {
                        modelToggleButton.setMessage(Component.literal("v"));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (modelListOpen && !fetchedModels.isEmpty() && mx >= fieldX() && mx <= fieldX() + fieldW()
                && my >= getListY() && my <= getListY() + getListHeight()) {
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
        return Math.max(120, panelW - LEFT_W - 8);
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

    private int getActionX() {
        int totalWidth = ACTION_SAVE_W + ACTION_GAP + ACTION_CANCEL_W + ACTION_GAP + ACTION_DISMISS_W;
        return panelLeft + Math.max(0, (panelW - totalWidth) / 2);
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
        if (!modelListOpen || fetchedModels.isEmpty()) {
            return 0;
        }
        return getVisibleModelCount() * 12 + 4;
    }

    private int getStoredContextTokens() {
        int stored = XxsxBuilder.getInstance().getConfig().contextMaxTokens();
        return stored > 0 ? stored : 70000;
    }

    private int getContextMaxTokens() {
        if (ctxBox == null) {
            return getStoredContextTokens();
        }
        String value = ctxBox.getValue();
        if (value == null || value.isBlank()) {
            return getStoredContextTokens();
        }
        Integer parsed = parseInt(value.trim());
        return parsed == null ? getStoredContextTokens() : parsed;
    }

    private Component getCtxLabel() {
        return SmartCoreText.gui("config.context_value", getContextMaxTokens() / 1000);
    }

    private String shortenModelLabel(String model) {
        if (model == null || model.isBlank()) {
            return SmartCoreText.gui("model.unset").getString();
        }
        return model.length() > 35 ? model.substring(0, 34) + "..." : model;
    }

    private void setStatus(Component message, int color) {
        this.statusMsg = message == null ? Component.empty() : message;
        this.statusColor = color;
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int lerpColor(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawStr(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.text(font, text == null ? "" : text, x, y, ensureVisibleColor(color));
    }

    private void drawStr(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        drawStr(g, text == null ? "" : text.getString(), x, y, color);
    }

    private void drawCenteredStr(GuiGraphicsExtractor g, String text, int centerX, int y, int color) {
        g.centeredText(font, text == null ? "" : text, centerX, y, ensureVisibleColor(color));
    }

    private void drawCenteredStr(GuiGraphicsExtractor g, Component text, int centerX, int y, int color) {
        drawCenteredStr(g, text == null ? "" : text.getString(), centerX, y, color);
    }

    private int ensureVisibleColor(int color) {
        return (color >>> 24) == 0 ? color | 0xFF000000 : color;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String suffix = "...";
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width(suffix))) + suffix;
    }

    @Override
    public void onClose() {
        Minecraft client = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        client.setScreen(parent);
    }
}

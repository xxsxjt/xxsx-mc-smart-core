package com.xxsx.builder.client;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.config.BuilderConfig;
import com.xxsx.builder.config.SmartCoreText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class InventoryAssistClient {
    private static final Identifier NIHILITY_TERMINAL_ID =
        Identifier.fromNamespaceAndPath("templenihility", "nihility_terminal");
    private static final Identifier NIHILITY_TERMINAL_MENU_ID =
        Identifier.fromNamespaceAndPath("templenihility", "nihility_terminal");
    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_GAP = 2;
    private static int pendingTerminalDepositDelay = -1;
    private static int pendingTerminalDepositTries;

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        if (isNihilityTerminalMenu(menu)) {
            if (pendingTerminalDepositDelay >= 0) {
                pendingTerminalDepositDelay = Math.max(2, pendingTerminalDepositDelay);
            }
            return;
        }

        if (menu instanceof InventoryMenu) {
            List<ButtonSpec> buttons = new ArrayList<>();
            if (hasEquippedNihilityTerminal()) {
                buttons.add(new ButtonSpec("◆", SmartCoreText.message("inventory.button.inventory"),
                    button -> openTerminalAndDeposit()));
            }
            addButtonRow(event, screen, menu, buttons);
            return;
        }

        if (isPortableStorageMenu(menu)) {
            List<ButtonSpec> buttons = new ArrayList<>();
            if (hasEquippedNihilityTerminal()) {
                buttons.add(new ButtonSpec("◆", SmartCoreText.message("inventory.button.container"),
                    button -> collectContainerAndOpenTerminal(menu)));
            }
            addButtonRow(event, screen, menu, buttons);
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (pendingTerminalDepositDelay < 0) {
            return;
        }
        if (pendingTerminalDepositDelay > 0) {
            pendingTerminalDepositDelay--;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();
        if (screen instanceof AbstractContainerScreen<?> containerScreen
            && isNihilityTerminalMenu(containerScreen.getMenu())) {
            depositPlayerInventoryToTerminal(containerScreen.getMenu(), true);
            pendingTerminalDepositDelay = -1;
            pendingTerminalDepositTries = 0;
            return;
        }

        if (++pendingTerminalDepositTries > 80) {
            pendingTerminalDepositDelay = -1;
            pendingTerminalDepositTries = 0;
            clientMessage(SmartCoreText.message("inventory.terminal_unavailable"));
        } else {
            pendingTerminalDepositDelay = 1;
        }
    }

    private static void addButtonRow(ScreenEvent.Init.Post event, Screen screen, AbstractContainerMenu menu,
                                     List<ButtonSpec> specs) {
        if (specs.isEmpty()) {
            return;
        }
        int right = Math.min(screen.width - 4, (screen.width + 176) / 2 - 4);
        int top = Math.max(4, (screen.height - estimateImageHeight(menu)) / 2 + 4);
        int x = right - specs.size() * BUTTON_SIZE - (specs.size() - 1) * BUTTON_GAP;
        for (ButtonSpec spec : specs) {
            event.addListener(iconButton(x, top, spec.icon(), spec.tooltip(), spec.action()));
            x += BUTTON_SIZE + BUTTON_GAP;
        }
    }

    private static Button iconButton(int x, int y, String icon, Component tooltip, Consumer<Button> action) {
        return Button.builder(Component.literal(icon), action::accept)
            .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
            .tooltip(Tooltip.create(tooltip))
            .build();
    }

    private static int estimateImageHeight(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu chestMenu) {
            return 114 + chestMenu.getRowCount() * 18;
        }
        if (menu instanceof HopperMenu) {
            return 133;
        }
        return 166;
    }

    private static boolean isPortableStorageMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu;
    }

    private static void collectContainerAndOpenTerminal(AbstractContainerMenu menu) {
        if (!ensureCursorEmpty(menu)) {
            return;
        }
        MoveResult result = quickMoveSlots(menu, getContainerSlots(menu));
        if (result.protectedSlots() > 0) {
            clientMessage(SmartCoreText.message("inventory.protected_container", result.protectedSlots()));
        }
        openTerminalAndDeposit();
    }

    private static void openTerminalAndDeposit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        pendingTerminalDepositDelay = 6;
        pendingTerminalDepositTries = 0;
        minecraft.getConnection().sendCommand("nihility terminal");
        clientMessage(SmartCoreText.message("inventory.opening_terminal"));
    }

    private static void depositPlayerInventoryToTerminal(AbstractContainerMenu menu, boolean showMessage) {
        MoveResult result = quickMoveSlots(menu,
            getPlayerStorageSlots(menu, config().terminalDepositIncludeHotbar));
        if (showMessage) {
            Component text = result.movedSlots() > 0
                ? SmartCoreText.message("inventory.deposit_complete", result.movedSlots())
                : SmartCoreText.message("inventory.deposit_empty");
            if (result.protectedSlots() > 0) {
                text = SmartCoreText.message("inventory.deposit_with_protected", text, result.protectedSlots());
            }
            clientMessage(text);
        }
    }

    private static MoveResult quickMoveSlots(AbstractContainerMenu menu, List<Slot> slots) {
        if (!ensureCursorEmpty(menu)) {
            return MoveResult.EMPTY;
        }
        int moved = 0;
        int protectedSlots = 0;
        for (Slot slot : new ArrayList<>(slots)) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (isProtectedFromTerminalDeposit(stack)) {
                protectedSlots++;
                continue;
            }
            clickSlot(menu, slot, 0, ContainerInput.QUICK_MOVE);
            moved++;
        }
        return new MoveResult(moved, protectedSlots);
    }

    private static List<Slot> getContainerSlots(AbstractContainerMenu menu) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        Inventory inventory = player.getInventory();
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container != inventory && slot.isActive()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static List<Slot> getPlayerStorageSlots(AbstractContainerMenu menu, boolean includeHotbar) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        Inventory inventory = player.getInventory();
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container == inventory && isPlayerStorageSlot(slot, includeHotbar) && slot.isActive()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static boolean isPlayerStorageSlot(Slot slot, boolean includeHotbar) {
        int containerSlot = slot.getContainerSlot();
        return (containerSlot >= 9 && containerSlot < 36)
            || (includeHotbar && containerSlot >= 0 && containerSlot < 9);
    }

    private static boolean ensureCursorEmpty(AbstractContainerMenu menu) {
        if (menu.getCarried().isEmpty()) {
            return true;
        }
        clientMessage(SmartCoreText.message("inventory.cursor_not_empty"));
        return false;
    }

    private static void clickSlot(AbstractContainerMenu menu, Slot slot, int button, ContainerInput input) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        minecraft.gameMode.handleContainerInput(menu.containerId, slot.index, button, input, minecraft.player);
    }

    private static boolean hasEquippedNihilityTerminal() {
        Player player = Minecraft.getInstance().player;
        if (player == null || !isNihilityTerminalRegistered()) {
            return false;
        }
        return hasCuriosNihilityTerminal(player);
    }

    private static boolean hasCuriosNihilityTerminal(Player player) {
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getCuriosInventory = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Object handlerOptional = getCuriosInventory.invoke(null, player);
            if (!(handlerOptional instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }
            Class<?> handlerType = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Method findFirstCurio = handlerType.getMethod("findFirstCurio", Predicate.class);
            Object result = findFirstCurio.invoke(optional.get(), (Predicate<ItemStack>) InventoryAssistClient::isNihilityTerminal);
            return result instanceof Optional<?> curio && curio.isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isNihilityTerminalRegistered() {
        return BuiltInRegistries.ITEM.getOptional(NIHILITY_TERMINAL_ID).isPresent();
    }

    private static boolean isNihilityTerminal(ItemStack stack) {
        return !stack.isEmpty() && NIHILITY_TERMINAL_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static boolean isProtectedFromTerminalDeposit(ItemStack stack) {
        if (isNihilityTerminal(stack)) {
            return true;
        }
        BuilderConfig config = config();
        if (config.terminalDepositSkipDamagedItems && stack.isDamageableItem()) {
            return true;
        }
        if (config.terminalDepositSkipEnchantedItems && stack.isEnchanted()) {
            return true;
        }
        if (config.terminalDepositSkipNamedItems
            && !stack.getHoverName().equals(stack.getItem().getName(stack))) {
            return true;
        }
        String protectedItems = config.terminalDepositProtectedItems;
        if (protectedItems == null || protectedItems.isBlank()) {
            return false;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return Arrays.stream(protectedItems.split(","))
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .anyMatch(itemId::equals);
    }

    private static BuilderConfig config() {
        return XxsxBuilder.getInstance().getConfig();
    }

    private static boolean isNihilityTerminalMenu(AbstractContainerMenu menu) {
        try {
            return NIHILITY_TERMINAL_MENU_ID.equals(BuiltInRegistries.MENU.getKey(menu.getType()));
        } catch (UnsupportedOperationException ignored) {
            return false;
        }
    }

    private static void clientMessage(Component text) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(SmartCoreText.message("inventory.prefix").copy().append(text));
        }
    }

    private record MoveResult(int movedSlots, int protectedSlots) {
        private static final MoveResult EMPTY = new MoveResult(0, 0);
    }

    private record ButtonSpec(String icon, Component tooltip, Consumer<Button> action) {
    }

    private InventoryAssistClient() {
    }
}

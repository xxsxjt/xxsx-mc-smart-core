package com.xxsx.builder.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
            addButtonRow(event, screen, menu, List.of(
                new ButtonSpec("◆", "一键收入背包到虚无终端", button -> depositPlayerInventoryToTerminal(menu, true))
            ));
            if (pendingTerminalDepositDelay < 0) {
                return;
            }
            pendingTerminalDepositDelay = Math.max(2, pendingTerminalDepositDelay);
            return;
        }

        if (menu instanceof InventoryMenu) {
            List<ButtonSpec> buttons = new ArrayList<>();
            buttons.add(new ButtonSpec("⇅", "整理背包主体，不移动快捷栏", button -> sortPlayerInventory(menu)));
            if (hasUsableNihilityTerminal()) {
                buttons.add(new ButtonSpec("◆", "打开虚无终端并收入背包", button -> openTerminalAndDeposit()));
            }
            addButtonRow(event, screen, menu, buttons);
            return;
        }

        if (isPortableStorageMenu(menu)) {
            List<ButtonSpec> buttons = new ArrayList<>();
            buttons.add(new ButtonSpec("⇅", "整理当前容器", button -> sortContainer(menu)));
            buttons.add(new ButtonSpec("↓", "收取容器到背包", button -> quickMoveContainerToPlayer(menu)));
            buttons.add(new ButtonSpec("↑", "存入背包到容器", button -> quickMovePlayerToContainer(menu)));
            if (hasUsableNihilityTerminal()) {
                buttons.add(new ButtonSpec("◆", "收取当前容器后收入虚无终端", button -> collectContainerAndOpenTerminal(menu)));
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
            clientMessage("未能打开虚无终端，确认终端已绑定并且当前维度正确。");
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

    private static Button iconButton(int x, int y, String icon, String tooltip, Consumer<Button> action) {
        return Button.builder(Component.literal(icon), action::accept)
            .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE)
            .tooltip(Tooltip.create(Component.literal(tooltip)))
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

    private static void sortPlayerInventory(AbstractContainerMenu menu) {
        sortSlots(menu, getPlayerStorageSlots(menu, false), "背包主体已整理。");
    }

    private static void sortContainer(AbstractContainerMenu menu) {
        sortSlots(menu, getContainerSlots(menu), "当前容器已整理。");
    }

    private static void sortSlots(AbstractContainerMenu menu, List<Slot> slots, String doneMessage) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ensureCursorEmpty(menu)) {
            return;
        }
        if (slots.size() < 2) {
            return;
        }

        List<ItemStack> desired = slots.stream()
            .map(slot -> slot.getItem().copy())
            .filter(stack -> !stack.isEmpty())
            .sorted(InventoryAssistClient::compareStacks)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        while (desired.size() < slots.size()) {
            desired.add(ItemStack.EMPTY);
        }

        List<ItemStack> current = slots.stream()
            .map(slot -> slot.getItem().copy())
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        int clicks = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (ItemStack.matches(current.get(i), desired.get(i))) {
                continue;
            }
            int source = findMatchingStack(current, desired.get(i), i + 1);
            if (source < 0) {
                continue;
            }
            Slot sourceSlot = slots.get(source);
            Slot targetSlot = slots.get(i);
            clickSlot(menu, sourceSlot, 0, ContainerInput.PICKUP);
            clickSlot(menu, targetSlot, 0, ContainerInput.PICKUP);
            clickSlot(menu, sourceSlot, 0, ContainerInput.PICKUP);
            ItemStack oldTarget = current.get(i);
            current.set(i, current.get(source));
            current.set(source, oldTarget);
            clicks += 3;
        }

        if (clicks > 0) {
            clientMessage(doneMessage);
        }
    }

    private static int compareStacks(ItemStack left, ItemStack right) {
        Identifier leftId = BuiltInRegistries.ITEM.getKey(left.getItem());
        Identifier rightId = BuiltInRegistries.ITEM.getKey(right.getItem());
        return Comparator
            .comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
            .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())
            .thenComparing(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT))
            .thenComparing(stack -> stack.getComponentsPatch().toString())
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed())
            .compare(leftId == null ? ItemStack.EMPTY : left, rightId == null ? ItemStack.EMPTY : right);
    }

    private static int findMatchingStack(List<ItemStack> current, ItemStack desired, int start) {
        for (int i = start; i < current.size(); i++) {
            if (ItemStack.matches(current.get(i), desired)) {
                return i;
            }
        }
        return -1;
    }

    private static void quickMoveContainerToPlayer(AbstractContainerMenu menu) {
        int moved = quickMoveSlots(menu, getContainerSlots(menu), false);
        if (moved > 0) {
            clientMessage("已收取当前容器可移动物品。");
        }
    }

    private static void quickMovePlayerToContainer(AbstractContainerMenu menu) {
        int moved = quickMoveSlots(menu, getPlayerStorageSlots(menu, true), false);
        if (moved > 0) {
            clientMessage("已存入背包可移动物品。");
        }
    }

    private static void collectContainerAndOpenTerminal(AbstractContainerMenu menu) {
        if (!ensureCursorEmpty(menu)) {
            return;
        }
        quickMoveSlots(menu, getContainerSlots(menu), false);
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
        clientMessage("正在打开虚无终端并收入背包物品。");
    }

    private static void depositPlayerInventoryToTerminal(AbstractContainerMenu menu, boolean showMessage) {
        int moved = quickMoveSlots(menu, getPlayerStorageSlots(menu, true), true);
        if (showMessage) {
            clientMessage(moved > 0 ? "已尝试收入背包物品到虚无终端。" : "没有可收入的背包物品。");
        }
    }

    private static int quickMoveSlots(AbstractContainerMenu menu, List<Slot> slots, boolean skipNihilityTerminal) {
        if (!ensureCursorEmpty(menu)) {
            return 0;
        }
        int moved = 0;
        for (Slot slot : new ArrayList<>(slots)) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || (skipNihilityTerminal && isNihilityTerminal(stack))) {
                continue;
            }
            clickSlot(menu, slot, 0, ContainerInput.QUICK_MOVE);
            moved++;
        }
        return moved;
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
        return containerSlot >= 9 && containerSlot < 36
            || includeHotbar && containerSlot >= 0 && containerSlot < 9;
    }

    private static boolean ensureCursorEmpty(AbstractContainerMenu menu) {
        if (menu.getCarried().isEmpty()) {
            return true;
        }
        clientMessage("请先放下鼠标上的物品。");
        return false;
    }

    private static void clickSlot(AbstractContainerMenu menu, Slot slot, int button, ContainerInput input) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        minecraft.gameMode.handleContainerInput(menu.containerId, slot.index, button, input, minecraft.player);
    }

    private static boolean hasUsableNihilityTerminal() {
        Player player = Minecraft.getInstance().player;
        if (player == null || !isNihilityTerminalRegistered()) {
            return false;
        }

        Container inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isNihilityTerminal(inventory.getItem(slot))) {
                return true;
            }
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

    private static boolean isNihilityTerminalMenu(AbstractContainerMenu menu) {
        try {
            return NIHILITY_TERMINAL_MENU_ID.equals(BuiltInRegistries.MENU.getKey(menu.getType()));
        } catch (UnsupportedOperationException ignored) {
            return false;
        }
    }

    private static void clientMessage(String text) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal("[智能核心] " + text));
        }
    }

    private record ButtonSpec(String icon, String tooltip, Consumer<Button> action) {
    }

    private InventoryAssistClient() {
    }
}

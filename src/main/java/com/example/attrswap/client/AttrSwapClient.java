package com.example.attrswap.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.lwjgl.glfw.GLFW;

public class AttrSwapClient implements ClientModInitializer {
    private int slotA = -1;
    private int slotB = -1;
    private boolean enabled = false;
    private boolean swappedForAttack = false;

    private KeyMapping keyBindA, keyBindB, keyToggle, keyInfo;

    @Override
    public void onInitializeClient() {
        keyToggle = reg("key.attrswap.toggle", GLFW.GLFW_KEY_BACKSLASH);
        keyBindA  = reg("key.attrswap.bind_a", GLFW.GLFW_KEY_SEMICOLON);
        keyBindB  = reg("key.attrswap.bind_b", GLFW.GLFW_KEY_APOSTROPHE);
        keyInfo   = reg("key.attrswap.info", GLFW.GLFW_KEY_LEFT_BRACKET);

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            onAttack(player instanceof LocalPlayer lp ? lp : null);
            return InteractionResult.PASS;
        });

        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            dispatcher.register(ClientCommands.literal("attrswap")
                .then(ClientCommands.literal("bind1")
                    .then(ClientCommands.argument("slot", IntegerArgumentType.integer(1, 9))
                        .executes(c -> { slotA = IntegerArgumentType.getInteger(c, "slot") - 1;
                            msg(Minecraft.getInstance(), "§a[A] bound to slot " + (slotA + 1)); return 1; })))
                .then(ClientCommands.literal("bind2")
                    .then(ClientCommands.argument("slot", IntegerArgumentType.integer(1, 9))
                        .executes(c -> { slotB = IntegerArgumentType.getInteger(c, "slot") - 1;
                            msg(Minecraft.getInstance(), "§a[B] bound to slot " + (slotB + 1)); return 1; })))
                .then(ClientCommands.literal("toggle")
                    .executes(c -> { toggle(Minecraft.getInstance()); return 1; }))
                .then(ClientCommands.literal("info")
                    .executes(c -> { printInfo(Minecraft.getInstance()); return 1; }))
                .then(ClientCommands.literal("clear")
                    .executes(c -> { slotA = slotB = -1; enabled = false;
                        msg(Minecraft.getInstance(), "§7Bindings cleared."); return 1; })));
        });
    }

    private KeyMapping reg(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(
            new KeyMapping(name, InputConstants.Type.KEYSYM, key, KeyMapping.Category.MISC));
    }

    private void onAttack(LocalPlayer player) {
        if (player == null || !enabled || slotA < 0 || slotB < 0) return;
        if (player.getInventory().getSelectedSlot() == slotA) {
            player.getInventory().setSelectedSlot(slotB);
            swappedForAttack = true;
        }
    }

    private void onStartTick(Minecraft mc) {
        if (mc.player == null) return;
        while (keyToggle.consumeClick()) toggle(mc);
        while (keyBindA.consumeClick()) {
            slotA = mc.player.getInventory().getSelectedSlot();
            msg(mc, "§a[A] bound to slot " + (slotA + 1));
        }
        while (keyBindB.consumeClick()) {
            slotB = mc.player.getInventory().getSelectedSlot();
            msg(mc, "§a[B] bound to slot " + (slotB + 1));
        }
        while (keyInfo.consumeClick()) printInfo(mc);
    }

    private void onEndTick(Minecraft mc) {
        if (mc.player == null) return;
        if (swappedForAttack) {
            mc.player.getInventory().setSelectedSlot(slotA);
            swappedForAttack = false;
        }
        if (enabled) {
            mc.gui.setOverlayMessage(Component.literal(hud(mc)), false);
        }
    }

    private void toggle(Minecraft mc) {
        if (!enabled && (slotA < 0 || slotB < 0)) {
            msg(mc, "§cBind both slots first (; and ' or /attrswap bind1|bind2).");
            return;
        }
        enabled = !enabled;
        msg(mc, enabled ? "§a[ATTRSWAP] ON" : "§c[ATTRSWAP] OFF");
    }

    private String hud(Minecraft mc) {
        return "§a[ATTRSWAP] ON  §fA: " + itemName(mc, slotA) + "  §7→  §fB: " + itemName(mc, slotB);
    }

    private String itemName(Minecraft mc, int slot) {
        if (slot < 0 || mc.player == null) return "unbound";
        ItemStack s = mc.player.getInventory().getItem(slot);
        return s.isEmpty() ? "(empty)" : s.getHoverName().getString();
    }

    private void printInfo(Minecraft mc) {
        if (mc.player == null) return;
        if (slotA < 0 && slotB < 0) { msg(mc, "§c[ATTRSWAP] No slots bound."); return; }
        msg(mc, "§e--- ATTRSWAP INFO ---");
        printItemAttrs(mc, "§a[A] ", slotA);
        printItemAttrs(mc, "§b[B] ", slotB);
    }

    private void printItemAttrs(Minecraft mc, String prefix, int slot) {
        if (slot < 0) { msg(mc, prefix + "unbound"); return; }
        ItemStack stack = mc.player.getInventory().getItem(slot);
        msg(mc, prefix + "slot " + (slot + 1) + ": " + itemName(mc, slot));
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods == null || mods.modifiers().isEmpty()) { msg(mc, "§7  No attribute modifiers"); return; }
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            msg(mc, String.format("§7  %s %+.2f (%s)",
                e.attribute().getRegisteredName(), e.modifier().amount(), e.modifier().operation()));
        }
    }

    private void msg(Minecraft mc, String text) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }
}

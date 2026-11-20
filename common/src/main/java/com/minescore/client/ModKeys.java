package com.minescore.client;

import com.minescore.Tr;
import com.minescore.client.screen.ConfigScreen;
import com.minescore.config.MineScoreConfig;
import com.minescore.config.Setting;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * MineScore's hotkeys (SCOPE.md §9): Ctrl+M opens the config menu; the others are unbound until a player
 * picks keys for them. They're ordinary controls (in vanilla's Controls screen too), listed again in the
 * menu's Hotkeys tab, where they can be changed the same way.
 */
public final class ModKeys {
    /** Sets a key with its modifiers; the loader's own way of doing it, since vanilla keys have no modifiers. */
    public interface Binder {
        void bind(KeyMapping key, InputConstants.Key input, boolean ctrl, boolean shift, boolean alt);
    }

    public static Binder binder = (key, input, ctrl, shift, alt) -> key.setKey(input);
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static final List<Runnable> ACTIONS = new ArrayList<>();

    private ModKeys() {}

    /** What each hotkey does, by its id ({@code key.minescore.<id>}). Each loader makes the mapping and hands it here. */
    public static void add(KeyMapping key, String id) {
        Runnable action = switch (id) {
            case "config" -> () -> Minecraft.getInstance().setScreen(new ConfigScreen(Minecraft.getInstance().screen));
            case "invisible_wires" -> () -> toggle(MineScoreConfig.INVISIBLE_WIRES);
            case "room_sound" -> () -> toggle(MineScoreConfig.ROOM_SOUND);
            case "through_blocks" -> () -> toggle(MineScoreConfig.THROUGH_BLOCKS);
            default -> () -> {};
        };
        String label = switch (id) {
            case "config" -> Tr.en("Open the config menu");
            case "invisible_wires" -> Tr.en("Invisible wires on/off");
            case "room_sound" -> Tr.en("Room Sound Physics on/off");
            case "through_blocks" -> Tr.en("Sound through blocks on/off");
            default -> id;
        };
        KEYS.add(key);
        ACTIONS.add(action);
        MineScoreConfig.addHotkey(Setting.hotkey("key_" + id, label, Tr.en("Also in Minecraft's Controls, under MineScore."), key, action));
    }

    /** A client setting flipped by its hotkey, with a word above the hotbar to say so. */
    private static void toggle(Setting setting) {
        setting.decode(setting.next(false));
        MineScoreConfig.save(Setting.Side.CLIENT);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(setting.bool() ? "msg.minescore.config.on" : "msg.minescore.config.off",
                    Tr.c(setting.label)), true);
        }
    }

    /** Every tick: runs what was pressed (only in-game; menus take their own keys). */
    public static void tick() {
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        for (int i = 0; i < KEYS.size(); i++) {
            while (KEYS.get(i).consumeClick()) {
                ACTIONS.get(i).run();
            }
        }
    }

    /** Changes a hotkey from the config menu, and saves it with the game's controls. */
    public static void rebind(KeyMapping key, InputConstants.Key input, boolean ctrl, boolean shift, boolean alt) {
        binder.bind(key, input, ctrl, shift, alt);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }
}

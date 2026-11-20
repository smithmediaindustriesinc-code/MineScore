package com.minescore.registry;

import com.minescore.menu.DeskMenu;
import com.minescore.menu.LatheMenu;
import com.minescore.menu.MeuMenu;
import com.minescore.platform.PlatformHelper;
import com.minescore.platform.RegistryHelper;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    public static Supplier<MenuType<LatheMenu>> DISC_CUTTING_LATHE;
    public static Supplier<MenuType<MeuMenu>> MULTI_EFFECTS_UNIT;
    public static Supplier<MenuType<DeskMenu>> WRITERS_DESK;

    private ModMenus() {}

    public static void register(RegistryHelper r, PlatformHelper platform) {
        DISC_CUTTING_LATHE = r.register(Registries.MENU, "disc_cutting_lathe", () -> platform.menuType(LatheMenu::fromNetwork));
        MULTI_EFFECTS_UNIT = r.register(Registries.MENU, "multi_effects_unit", () -> platform.menuType(MeuMenu::fromNetwork));
        WRITERS_DESK = r.register(Registries.MENU, "writers_desk", () -> platform.menuType(DeskMenu::fromNetwork));
    }
}

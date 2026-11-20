package com.minescore.forge.platform;

import com.minescore.MineScore;
import com.minescore.platform.RegistryHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/** One {@link DeferredRegister} per vanilla registry, each attached to the mod event bus on first use. */
public final class ForgeRegistryHelper implements RegistryHelper {
    private final IEventBus modBus;
    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new HashMap<>();

    public ForgeRegistryHelper(IEventBus modBus) {
        this.modBus = modBus;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, I extends T> Supplier<I> register(ResourceKey<? extends Registry<T>> registry, String name, Supplier<I> factory) {
        DeferredRegister<T> register = (DeferredRegister<T>) registers.computeIfAbsent(registry, key -> {
            DeferredRegister<T> created = DeferredRegister.create(registry, MineScore.MOD_ID);
            created.register(modBus);
            return created;
        });
        return register.register(name, factory);
    }
}

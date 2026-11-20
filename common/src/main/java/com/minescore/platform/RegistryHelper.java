package com.minescore.platform;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Registers game objects in a loader-specific way. The returned supplier only resolves once the
 * loader has finished registration, so never call {@code get()} during mod construction.
 */
public interface RegistryHelper {
    <T, I extends T> Supplier<I> register(ResourceKey<? extends Registry<T>> registry, String name, Supplier<I> factory);
}

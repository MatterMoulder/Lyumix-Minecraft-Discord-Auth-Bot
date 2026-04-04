package com.matter_moulder.lyumixdiscordauth.models;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Interface for managing player authentication state and location data.
 * Implemented by ServerPlayerEntityMixin.
 */
public interface PlayerAuth {
    /**
     * Saves player's current location and state
     */
    void lda$saveLastLocation();

    void lda$saveLastDimension(RegistryKey<World> registryKey);

    /**
     * Restores player's saved location and state after authentication
     */
    void lda$restoreLastLocation();

    UUID lda$getUuid();
}
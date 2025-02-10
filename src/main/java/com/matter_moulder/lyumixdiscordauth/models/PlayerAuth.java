package com.matter_moulder.lyumixdiscordauth.models;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * Interface for managing player authentication state and location data.
 * Implemented by ServerPlayerEntityMixin.
 */
public interface PlayerAuth {
    /**
     * Saves player's current location and state
     * @param saveDimension Whether to save dimension information
     */
    void lda$saveLastLocation(boolean saveDimension);

    void lda$saveLastDimension(RegistryKey<World> registryKey);

    /**
     * Restores player's saved location and state after authentication
     */
    void lda$restoreLastLocation();

    String lda$getName();
} 
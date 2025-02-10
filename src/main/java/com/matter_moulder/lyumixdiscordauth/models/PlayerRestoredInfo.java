package com.matter_moulder.lyumixdiscordauth.models;

import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Stores player state information that needs to be restored after authentication.
 * Includes location, dimension, and vehicle data.
 */
public class PlayerRestoredInfo {

    public static final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    /** Player's location coordinates and rotation */
    @Expose
    public Location location;

    /** Whether player was dead when state was saved */
    @Expose
    @SerializedName("was_dead")
    public boolean wasDead;

    @Expose
    public ServerWorld dimension;

    @Expose
    public UUID ridingEntityUUID;

    public static PlayerRestoredInfo fromJson(ServerPlayerEntity player, String name) {
        PlayerRestoredInfo playerCache;
        playerCache = new PlayerRestoredInfo();
        if (player != null) {
            playerCache.location = Location.fromPlayer(player);
            playerCache.ridingEntityUUID = player.getVehicle() != null ? player.getVehicle().getUuid() : null;
            playerCache.wasDead = player.isDead();
            playerCache.dimension = player.getServerWorld();
        }

        return playerCache;
    }
} 
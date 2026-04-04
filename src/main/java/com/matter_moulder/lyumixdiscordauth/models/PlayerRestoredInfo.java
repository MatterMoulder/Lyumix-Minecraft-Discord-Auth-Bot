package com.matter_moulder.lyumixdiscordauth.models;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import com.matter_moulder.lyumixdiscordauth.Main;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Stores player state information that needs to be restored after authentication.
 * Includes location, dimension, and vehicle data.
 */
public class PlayerRestoredInfo {

    public static final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    @Expose
    public UUID playerUUID;

    @Expose
    public String playerName;

    /** Player's location coordinates and rotation */
    @Expose
    public Location location;

    /** Whether player was dead when state was saved */
    @Expose
    @SerializedName("was_dead")
    public boolean wasDead;

    @Expose
    public UUID ridingEntityUUID;

    private Path playerFilePath;

    public static PlayerRestoredInfo fromPlayer(ServerPlayerEntity player) {
        PlayerRestoredInfo playerCache;
        playerCache = new PlayerRestoredInfo();
        if (player != null) {
            playerCache.playerUUID = player.getUuid();
            playerCache.playerName = player.getName().getString();
            playerCache.location = Location.fromPlayer(player);
            playerCache.ridingEntityUUID = player.getVehicle() != null ? player.getVehicle().getUuid() : null;
            playerCache.wasDead = player.isDead();
            playerCache.playerFilePath = Main.getSessionDir().resolve(playerCache.playerUUID + ".json");
        }
        return playerCache;
    }

    public void load() {
        if (Files.exists(this.playerFilePath)) {
            try {
                PlayerRestoredInfo loadedInfo = gson.fromJson(Files.newBufferedReader(this.playerFilePath), PlayerRestoredInfo.class);
                this.playerUUID = loadedInfo.playerUUID;
                this.playerName = loadedInfo.playerName;
                this.location = loadedInfo.location;
                this.wasDead = loadedInfo.wasDead;
                this.ridingEntityUUID = loadedInfo.ridingEntityUUID;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(this.playerFilePath)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void delete() {
        try {
            Files.deleteIfExists(this.playerFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
} 
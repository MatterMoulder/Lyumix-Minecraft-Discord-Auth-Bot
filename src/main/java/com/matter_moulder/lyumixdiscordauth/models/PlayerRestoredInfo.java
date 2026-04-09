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

import com.matter_moulder.lyumixdiscordauth.Server;
import com.matter_moulder.lyumixdiscordauth.Utils;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Stores player state information that needs to be restored after authentication.
 * Includes location, dimension, and vehicle data.
 */
public class PlayerRestoredInfo {
    private static final String SESSION_FILE_EXTENSION = ".json";
    private static final String CORRUPT_FILE_EXTENSION = ".corrupt";

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

    /**
     * Creates a restorable snapshot from the current player state.
     */
    public static PlayerRestoredInfo fromPlayer(ServerPlayerEntity player) {
        PlayerRestoredInfo playerCache;
        playerCache = new PlayerRestoredInfo();
        if (player != null) {
            playerCache.playerUUID = player.getUuid();
            playerCache.playerName = player.getName().getString();
            playerCache.location = Location.fromPlayer(player);
            playerCache.ridingEntityUUID = player.getVehicle() != null ? player.getVehicle().getUuid() : null;
            playerCache.wasDead = player.isDead();
            playerCache.playerFilePath = Server.getSessionDir().resolve(playerCache.playerUUID + SESSION_FILE_EXTENSION);
        }
        return playerCache;
    }

    /**
     * Loads persisted restore state from disk when available.
     */
    public void load() {
        if (Files.exists(this.playerFilePath)) {
            try {
                PlayerRestoredInfo loadedInfo = gson.fromJson(Files.newBufferedReader(this.playerFilePath), PlayerRestoredInfo.class);
                this.playerUUID = loadedInfo.playerUUID;
                this.playerName = loadedInfo.playerName;
                this.location = loadedInfo.location;
                this.wasDead = loadedInfo.wasDead;
                this.ridingEntityUUID = loadedInfo.ridingEntityUUID;
            } catch (IOException | RuntimeException e) {
                // Corrupted restore files are quarantined and ignored to keep auth flow safe.
                Server.getPluginLogger().warn("Failed to load restored player file: {}", Utils.sanitizeLog(this.playerFilePath.getFileName().toString()), e);
                try {
                    Path corruptPath = this.playerFilePath.resolveSibling(this.playerFilePath.getFileName().toString() + CORRUPT_FILE_EXTENSION);
                    Files.move(this.playerFilePath, corruptPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException renameError) {
                    Server.getPluginLogger().warn("Failed to quarantine corrupted restore file: {}", Utils.sanitizeLog(this.playerFilePath.getFileName().toString()), renameError);
                    try {
                        Files.deleteIfExists(this.playerFilePath);
                    } catch (IOException deleteError) {
                        Server.getPluginLogger().warn("Failed to delete corrupted restore file: {}", Utils.sanitizeLog(this.playerFilePath.getFileName().toString()), deleteError);
                    }
                }

                this.playerUUID = null;
                this.playerName = null;
                this.location = null;
                this.wasDead = false;
                this.ridingEntityUUID = null;
            }
        }
    }

    /**
     * Persists the current restore state to disk.
     */
    public void save() {
        try (Writer writer = Files.newBufferedWriter(this.playerFilePath)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            Server.getPluginLogger().warn("Failed to save restored player file: {}", Utils.sanitizeLog(this.playerFilePath.getFileName().toString()), e);
        }
    }

    /**
     * Deletes the persisted restore state file when present.
     */
    public void delete() {
        try {
            Files.deleteIfExists(this.playerFilePath);
        } catch (IOException e) {
            Server.getPluginLogger().warn("Failed to delete restored player file: {}", Utils.sanitizeLog(this.playerFilePath.getFileName().toString()), e);
        }
    }
}

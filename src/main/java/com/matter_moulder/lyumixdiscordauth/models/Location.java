package com.matter_moulder.lyumixdiscordauth.models;

import com.google.gson.annotations.Expose;
import com.matter_moulder.lyumixdiscordauth.Server;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Immutable player position snapshot including world dimension and rotation.
 */
public class Location {
    @Expose
    private final String dimensionKey;

    @Expose
    private final double x;

    @Expose
    private final double y;

    @Expose
    private final double z;

    @Expose
    private final float yaw;

    @Expose
    private final float pitch;

    /**
     * Creates a location from a world key string and coordinates.
     */
    public Location(String dimensionKey, double x, double y, double z, float yaw, float pitch) {
        this.dimensionKey = dimensionKey;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Creates a location from a world instance and coordinates.
     */
    public Location(World world, double x, double y, double z, float yaw, float pitch) {
        this(
                world.getRegistryKey().getValue().toString(),
                x, y, z,
                yaw, pitch
        );
    }

    /**
     * Creates a location from a world registry key and coordinates.
     */
    public Location(RegistryKey<World> registryKey, double x, double y, double z, float yaw, float pitch) {
        this(
                registryKey.getValue().toString(),
                x, y, z,
                yaw, pitch
        );
    }

    /**
     * Creates an overworld location from coordinates and rotation.
     */
    public Location(double x, double y, double z, float yaw, float pitch) {
        this(
                Server.getServer().getOverworld().getRegistryKey().getValue().toString(),
                x, y, z,
                yaw, pitch
        );
    }

    /**
     * Returns the serialized world dimension key.
     */
    public String getDimensionKey() {
        return this.dimensionKey;
    }

    /**
     * Resolves the server world for this location, falling back to overworld.
     */
    public ServerWorld getWorld() {
        ServerWorld world = Server.getServer().getWorld(
                RegistryKey.of(RegistryKeys.WORLD, new Identifier(dimensionKey))
        );
        return world != null ? world : Server.getServer().getOverworld();
    }

    /**
     * Returns the vector position.
     */
    public Vec3d getPosition() {
        return new Vec3d(x, y, z);
    }

    /**
     * Returns the floored block position.
     */
    public BlockPos getBlockPos() {
        return BlockPos.ofFloored(getPosition());
    }

    /**
     * Returns the X coordinate.
     */
    public double getX() {
        return x;
    }

    /**
     * Returns the Y coordinate.
     */
    public double getY() {
        return y;
    }

    /**
     * Returns the Z coordinate.
     */
    public double getZ() {
        return z;
    }

    /**
     * Returns the yaw rotation.
     */
    public float getYaw() {
        return yaw;
    }

    /**
     * Returns the pitch rotation.
     */
    public float getPitch() {
        return pitch;
    }

    /**
     * Captures a location from the player's current world and position.
     */
    public static Location fromPlayer(ServerPlayerEntity player) {
        return new Location(
            player.getWorld(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYaw(),
            player.getPitch()
        );
    }

    /**
     * Captures a location from the player's position and an explicit dimension key.
     */
    public static Location fromPlayer(ServerPlayerEntity player, String dimensionKey) {
        return new Location(
                dimensionKey,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYaw(),
                player.getPitch()
        );
    }
}

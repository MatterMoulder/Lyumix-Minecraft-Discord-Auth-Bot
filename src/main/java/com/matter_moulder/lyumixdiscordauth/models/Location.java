package com.matter_moulder.lyumixdiscordauth.models;

import com.google.gson.annotations.Expose;
import com.matter_moulder.lyumixdiscordauth.Main;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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

    public Location(String dimensionKey, double x, double y, double z, float yaw, float pitch) {
        this.dimensionKey = dimensionKey;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Location(World world, double x, double y, double z, float yaw, float pitch) {
        this(
                world.getRegistryKey().getValue().toString(),
                x, y, z,
                yaw, pitch
        );
    }

    public Location(RegistryKey<World> registryKey, double x, double y, double z, float yaw, float pitch) {
        this(
                registryKey.getValue().toString(),
                x, y, z,
                yaw, pitch
        );
    }

    public Location(double x, double y, double z, float yaw, float pitch) {
        this(
                Main.getServer().getOverworld().getRegistryKey().getValue().toString(),
                x, y, z,
                yaw, pitch
        );
    }

    public String getDimensionKey() {
        return this.dimensionKey;
    }

    public ServerWorld getWorld() {
        ServerWorld world = Main.getServer().getWorld(
                RegistryKey.of(RegistryKeys.WORLD, new Identifier(dimensionKey))
        );
        return world != null ? world : Main.getServer().getOverworld();
    }

    public Vec3d getPosition() {
        return new Vec3d(x, y, z);
    }

    public BlockPos getBlockPos() {
        return BlockPos.ofFloored(getPosition());
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

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
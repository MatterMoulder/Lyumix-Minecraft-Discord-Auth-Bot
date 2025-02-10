package com.matter_moulder.lyumixdiscordauth.misc;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class Location {
    private final World world;
    private final Vec3d position;
    private final float yaw;
    private final float pitch;

    public Location(World world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.position = new Vec3d(x, y, z);
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Location(double x, double y, double z, float yaw, float pitch) {
        this(null, x, y, z, yaw, pitch);
    }

    public World getWorld() {
        return world;
    }

    public Vec3d getPosition() {
        return position;
    }

    public BlockPos getBlockPos() {
        return BlockPos.ofFloored(position);
    }

    public double getX() {
        return position.x;
    }

    public double getY() {
        return position.y;
    }

    public double getZ() {
        return position.z;
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
}
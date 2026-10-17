package com.alan.clients.util.pathfinding.custom.api;

import net.minecraft.util.Vec3i;

public class Point extends Vec3i {
    public Point(int xIn, int yIn, int zIn) {
        super(xIn, yIn, zIn);
    }

    public Point(double xIn, double yIn, double zIn) {
        super(xIn, yIn, zIn);
    }

    public Point(Vec3i point) {
        super(point.getX(), point.getY(), point.getZ());
    }

    // Rise added these to Vec3i itself
    public Point add(final int x, final int y, final int z) {
        return new Point(getX() + x, getY() + y, getZ() + z);
    }

    public double distance(final Vec3i to) {
        final double dx = getX() - to.getX();
        final double dy = getY() - to.getY();
        final double dz = getZ() - to.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

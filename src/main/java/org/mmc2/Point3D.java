package org.mmc2;

public class Point3D {
    public final double x, y, z;

    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double distanceTo(Point3D p) {
        double dx = x - p.x, dy = y - p.y, dz = z - p.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}


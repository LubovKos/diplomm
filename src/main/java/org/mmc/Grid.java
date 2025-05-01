package org.mmc;

import java.util.List;

public class Grid {
    public final int sizeX, sizeY, sizeZ;
    private final float[][][] values;
    private final float spacing;
    private final float minX, minY, minZ;

    public Grid(List<float[]> points, int size, float spacing) {
        this.sizeX = size;
        this.sizeY = size;
        this.sizeZ = size;
        this.spacing = spacing;
        this.values = new float[size][size][size];

        float[] bounds = getBounds(points);
        minX = bounds[0];
        minY = bounds[1];
        minZ = bounds[2];

        populateScalarField(points);
    }

    private float[] getBounds(List<float[]> points) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] p : points) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            maxZ = Math.max(maxZ, p[2]);
        }
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private void populateScalarField(List<float[]> points) {
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    float worldX = minX + x * spacing;
                    float worldY = minY + y * spacing;
                    float worldZ = minZ + z * spacing;
                    values[x][y][z] = minDistance(worldX, worldY, worldZ, points);
                }
            }
        }
    }

    private float minDistance(float x, float y, float z, List<float[]> points) {
        float minDist = Float.MAX_VALUE;
        for (float[] p : points) {
            float dist = (float) Math.sqrt((p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y) + (p[2] - z) * (p[2] - z));
            minDist = Math.min(minDist, dist);
        }
        return minDist;
    }

    public float getValue(int x, int y, int z) {
        return values[x][y][z];
    }
}

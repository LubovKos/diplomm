package org.mmc;

import java.io.IOException;

public class mainn {

    public static void main(String[] args) throws IOException, IOException {
        PointCloud cloud = new PointCloud();
        cloud.loadFromFile("point_cloud.xyz");

        Grid grid = new Grid(cloud.getPoints(), 100, 0.1f);
        ParallelMarchingCubes mc = new ParallelMarchingCubes(grid, 0.05f);
        mc.run();
        mc.saveToOBJ("output.obj");
    }
}

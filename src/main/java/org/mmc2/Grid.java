package org.mmc2;
import java.util.List;
public class Grid {private final int size;
    private final double[][][] sdf;
    private final double threshold;
    private final List<Point3D> points;
    private final KDTree tree;
    private final double cellSize;

    public Grid(int size, List<Point3D> points, double threshold) {
        this.size = size;
        this.points = points;
        this.threshold = threshold;
        this.tree = new KDTree(points);
        this.cellSize = 1.0 / (size - 1); // Предполагаем, что сетка в [0,1]^3
        this.sdf = new double[size][size][size];
        computeSDF();
    }

    private void computeSDF() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    Point3D cellCenter = new Point3D(
                            i * cellSize,
                            j * cellSize,
                            k * cellSize
                    );
                    Point3D nearest = tree.findNearest(cellCenter);
                    double dist = nearest.distanceTo(cellCenter);
                    sdf[i][j][k] = dist - threshold;
                }
            }
        }
    }

    public double getSDF(int x, int y, int z) {
        return sdf[x][y][z];
    }

    public int getSize() {
        return this.size;
    }
}



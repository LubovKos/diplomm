package org.mmc2;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.example.StlReader;
import org.example.StlVisualizer;

public class Main {
    public static void main(String[] args) throws IOException {
        // 1. Генерируем тестовое облако точек (сфера)
        List<Point3D> points = generateSpherePoints(1000, 0.5);

        // 2. Создаем грид
        int gridSize = 50;
        double threshold = 0.01;
        Grid grid = new Grid(gridSize, points, threshold);

        // 3. Извлекаем поверхность
        List<Triangle> mesh = MarchingCubes.extractSurface(grid);

        // Инициализация и запуск рендерера
        OpenGLRenderer renderer = new OpenGLRenderer();
        renderer.init(mesh);
        renderer.render(mesh);
        renderer.cleanup();

        OBJExporter exporter = new OBJExporter();
        exporter.export(mesh, "output.obj");
        System.out.println("Done! Exported " + mesh.size() + " triangles");
    }

    // Генератор случайных точек на сфере
    private static List<Point3D> generateSpherePoints(int numPoints, double radius) {
        List<Point3D> points = new ArrayList<>();
        for (int i = 0; i < numPoints; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.sin(phi) * Math.sin(theta);
            double z = radius * Math.cos(phi);
            points.add(new Point3D(x + 0.5, y + 0.5, z + 0.5)); // Центр в (0.5, 0.5, 0.5)
        }
        return points;
    }

    public static void render(List<StlReader.Triangle> args) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("output.stl"))) {
            writer.write("solid \n");

            // Стенки цилиндра (кружка)
            for (StlReader.Triangle t: args) {
                writer.write(facet(t.normal, t.vertices));
            }
            writer.write("endsolid cup\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String facet(float[] norm, float[][] v) {
        return String.format("  facet normal %.6f %.6f %.6f\n", norm[0], norm[1], norm[2]) +
                "    outer loop\n" +
                String.format("      vertex %.6f %.6f %.6f\n", v[0][0], v[0][1], v[0][2]) +
                String.format("      vertex %.6f %.6f %.6f\n", v[1][0], v[1][1], v[1][2]) +
                String.format("      vertex %.6f %.6f %.6f\n", v[2][0], v[2][1], v[2][2]) +
                "    endloop\n" +
                "  endfacet\n";
    }
}

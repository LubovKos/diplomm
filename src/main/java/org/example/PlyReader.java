package org.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PlyReader {

    public static class Point {
        public float x, y, z;

        public Point(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class Triangle {
        public Point[] vertices = new Point[3];

        public Triangle(Point v1, Point v2, Point v3) {
            vertices[0] = v1;
            vertices[1] = v2;
            vertices[2] = v3;
        }
    }

    public List<Triangle> readPlyFile(String filePath) throws IOException {
        List<Triangle> triangles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            List<Point> points = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length == 3) { // Координаты точки
                    float x = Float.parseFloat(tokens[0]);
                    float y = Float.parseFloat(tokens[1]);
                    float z = Float.parseFloat(tokens[2]);
                    points.add(new Point(x, y, z));
                } else if (tokens.length == 3) { // Индексы вершин треугольника
                    int i1 = Integer.parseInt(tokens[0]);
                    int i2 = Integer.parseInt(tokens[1]);
                    int i3 = Integer.parseInt(tokens[2]);
                    triangles.add(new Triangle(points.get(i1), points.get(i2), points.get(i3)));
                }
            }
        }
        return triangles;
    }
}

package org.example;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class CupModelGenerator {

    public static void main(String[] args) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("cup2.stl"))) {
            writer.write("solid cup\n");

            int numSegments = 1000 / 2; // Условно 1000 точек для модели

            // Стенки цилиндра (кружка)
            for (int i = 0; i < numSegments; i++) {
                float angle1 = (float) (2 * Math.PI * i / numSegments);
                float angle2 = (float) (2 * Math.PI * (i + 1) / numSegments);

                float x1 = (float) Math.cos(angle1);
                float y1 = (float) Math.sin(angle1);

                float x2 = (float) Math.cos(angle2);
                float y2 = (float) Math.sin(angle2);

                writer.write(facet(x1, y1, 1.0f, x2, y2, 1.0f, x2, y2, 0.0f));
                writer.write(facet(x1, y1, 1.0f, x2, y2, 0.0f, x1, y1, 0.0f));
            }

            // Дно кружки
            for (int i = 0; i < numSegments; i++) {
                float angle1 = (float) (2 * Math.PI * i / numSegments);
                float angle2 = (float) (2 * Math.PI * (i + 1) / numSegments);

                float x1 = (float) Math.cos(angle1);
                float y1 = (float) Math.sin(angle1);

                float x2 = (float) Math.cos(angle2);
                float y2 = (float) Math.sin(angle2);

                writer.write(facet(0.0f, 0.0f, 0.0f, x2, y2, 0.0f, x1, y1, 0.0f));
            }

            writer.write("endsolid cup\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String facet(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        return "  facet normal 0.0 0.0 0.0\n" +
                "    outer loop\n" +
                String.format("      vertex %.6f %.6f %.6f\n", x1, y1, z1) +
                String.format("      vertex %.6f %.6f %.6f\n", x2, y2, z2) +
                String.format("      vertex %.6f %.6f %.6f\n", x3, y3, z3) +
                "    endloop\n" +
                "  endfacet\n";
    }
}

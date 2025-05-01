package org.mmc;

import java.io.*;
import java.util.*;

public class PointCloud {
    private final List<float[]> points = new ArrayList<>();

    public void loadFromFile(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length != 3) continue;
                float x = Float.parseFloat(tokens[0]);
                float y = Float.parseFloat(tokens[1]);
                float z = Float.parseFloat(tokens[2]);
                points.add(new float[]{x, y, z});
            }
        }
    }

    public List<float[]> getPoints() {
        return points;
    }
}

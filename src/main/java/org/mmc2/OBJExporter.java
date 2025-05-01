package org.mmc2;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class OBJExporter {
    public static void export(List<Triangle> triangles, String path) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            for (Triangle tri : triangles) {
                out.printf("v %f %f %f\n", tri.v1.x, tri.v1.y, tri.v1.z);
                out.printf("v %f %f %f\n", tri.v2.x, tri.v2.y, tri.v2.z);
                out.printf("v %f %f %f\n", tri.v3.x, tri.v3.y, tri.v3.z);
            }
            for (int i = 0; i < triangles.size(); i++) {
                int base = i * 3 + 1;
                out.printf("f %d %d %d\n", base, base+1, base+2);
            }
        }
    }
}


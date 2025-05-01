package org.mmc2;

public class Triangle {
    public final Point3D v1, v2, v3;

    public Triangle(Point3D v1, Point3D v2, Point3D v3) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }

    // Опционально: геттеры, если нужен доступ к полям
    public Point3D getV1() { return v1; }
    public Point3D getV2() { return v2; }
    public Point3D getV3() { return v3; }

    // Для отладки
    @Override
    public String toString() {
        return String.format("Triangle: (%s), (%s), (%s)", v1, v2, v3);
    }
    // Вычисление нормали треугольника
    public Point3D calculateNormal() {
        Point3D edge1 = new Point3D(v2.x - v1.x, v2.y - v1.y, v2.z - v1.z);
        Point3D edge2 = new Point3D(v3.x - v1.x, v3.y - v1.y, v3.z - v1.z);

        // Векторное произведение edge1 × edge2
        double nx = edge1.y * edge2.z - edge1.z * edge2.y;
        double ny = edge1.z * edge2.x - edge1.x * edge2.z;
        double nz = edge1.x * edge2.y - edge1.y * edge2.x;

        // Нормализация
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new Point3D(nx / length, ny / length, nz / length);
    }
}


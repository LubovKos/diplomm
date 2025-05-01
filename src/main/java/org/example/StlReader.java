package org.example; // Определение пакета, в котором находится класс

import java.io.*; // Импорт классов для работы с вводом/выводом (файлы, потоки)
import java.util.*; // Импорт утилит, например, коллекций

public class StlReader { // Класс для чтения STL-файлов (как в формате ASCII, так и бинарном)

    // Внутренний статический класс, описывающий треугольник модели STL
    public static class Triangle {
        public float[] normal = new float[3]; // Массив для хранения координат нормали (x, y, z)
        public float[][] vertices = new float[3][3]; // Двумерный массив для хранения 3-х вершин,
        // каждая из которых содержит 3 координаты (x, y, z)

        // Конструктор, принимающий нормаль и массив вершин (не используется, так как тело конструктора пустое)
        public Triangle(float[] normal, float[][] floats) {
        }

        // Пустой конструктор по умолчанию
        public Triangle() {
        }
    }

    // Метод для чтения ASCII STL-файла, возвращающий список треугольников
    public List<Triangle> readStlFile(String filename) throws IOException {
        List<Triangle> triangles = new ArrayList<>(); // Создаем список для хранения треугольников

        // Используем try-with-resources для автоматического закрытия BufferedReader
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            // Читаем файл построчно, пока не достигнем конца файла
            while ((line = reader.readLine()) != null) {
                line = line.trim(); // Убираем лишние пробелы в начале и конце строки

                // Если строка начинается с "facet normal", это начало описания треугольника
                if (line.startsWith("facet normal")) {
                    Triangle triangle = new Triangle(); // Создаем новый объект треугольника
                    String[] normalValues = line.split("\\s+"); // Разбиваем строку на части по пробелам

                    // Парсим значения нормали из строки
                    try {
                        triangle.normal[0] = Float.parseFloat(normalValues[2]); // Читаем x-координату нормали
                        triangle.normal[1] = Float.parseFloat(normalValues[3]); // Читаем y-координату нормали
                        triangle.normal[2] = Float.parseFloat(normalValues[4]); // Читаем z-координату нормали
                    } catch (NumberFormatException e) {
                        // Если значения нормали не корректны, выбрасываем исключение с пояснением
                        throw new IOException("Invalid normal values in STL file", e);
                    }

                    // Пропускаем строку "outer loop", которая должна идти после описания нормали
                    line = reader.readLine();
                    if (line == null || !line.trim().equals("outer loop")) {
                        throw new IOException("Invalid STL file structure: expected 'outer loop'");
                    }

                    // Читаем три строки с вершинами треугольника
                    for (int i = 0; i < 3; i++) {
                        line = reader.readLine();
                        if (line == null) {
                            throw new IOException("Invalid STL file structure: expected vertex");
                        }

                        line = line.trim(); // Убираем лишние пробелы
                        String[] vertexValues = line.split("\\s+"); // Разбиваем строку на части

                        // Парсим координаты вершины
                        try {
                            // vertexValues[0] содержит слово "vertex", поэтому начинаем с индекса 1
                            triangle.vertices[i][0] = Float.parseFloat(vertexValues[1]); // x-координата вершины
                            triangle.vertices[i][1] = Float.parseFloat(vertexValues[2]); // y-координата вершины
                            triangle.vertices[i][2] = Float.parseFloat(vertexValues[3]); // z-координата вершины
                        } catch (NumberFormatException e) {
                            throw new IOException("Invalid vertex values in STL file", e);
                        }
                    }

                    // Пропускаем строку "endloop"
                    line = reader.readLine();
                    if (line == null || !line.trim().equals("endloop")) {
                        throw new IOException("Invalid STL file structure: expected 'endloop'");
                    }

                    // Пропускаем строку "endfacet"
                    line = reader.readLine();
                    if (line == null || !line.trim().equals("endfacet")) {
                        throw new IOException("Invalid STL file structure: expected 'endfacet'");
                    }
                    System.out.println("_________________Triangle________________:");
                    System.out.println("__Normal:");
                    System.out.println(triangle.normal[0]);
                    System.out.println(triangle.normal[1]);
                    System.out.println(triangle.normal[2]);

                    for (int i = 0; i < 3; i++) {
                        System.out.println("__Vertice:");
                        System.out.println(triangle.vertices[i][0]);
                        System.out.println(triangle.vertices[i][1]);
                        System.out.println(triangle.vertices[i][2]);
                    }
                    // Добавляем прочитанный треугольник в список
                    triangles.add(triangle);
                }
            }
        }

        return triangles; // Возвращаем список треугольников
    }

    // Метод для чтения бинарного STL-файла, возвращающий список треугольников
    public List<Triangle> readBinaryStlFile(String filename) throws IOException {
        List<Triangle> triangles = new ArrayList<>(); // Создаем список для хранения треугольников

        // Используем try-with-resources для автоматического закрытия DataInputStream
        try (DataInputStream input = new DataInputStream(new FileInputStream(filename))) {
            // Пропускаем заголовок размером 80 байт
            input.skipBytes(80);

            // Читаем количество треугольников. Используем reverseBytes для корректного порядка байт
            int numTriangles = Integer.reverseBytes(input.readInt());

            // Читаем каждый треугольник
            for (int i = 0; i < numTriangles; i++) {
                Triangle triangle = new Triangle(); // Создаем новый объект треугольника

                // Читаем нормаль треугольника (3 значения, по одному для каждой координаты)
                for (int j = 0; j < 3; j++) {
                    // Читаем int, меняем порядок байт и интерпретируем как float
                    triangle.normal[j] = Float.intBitsToFloat(Integer.reverseBytes(input.readInt()));
                }

                // Читаем координаты вершин треугольника
                for (int j = 0; j < 3; j++) { // Для каждой из 3-х вершин
                    for (int k = 0; k < 3; k++) { // Для каждой координаты вершины (x, y, z)
                        triangle.vertices[j][k] = Float.intBitsToFloat(Integer.reverseBytes(input.readInt()));
                    }
                }

                // Пропускаем атрибуты (2 байта, которые обычно не используются)
                input.skipBytes(2);

                // Добавляем прочитанный треугольник в список
                triangles.add(triangle);
            }
        }

        return triangles; // Возвращаем список треугольников
    }
}

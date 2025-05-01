package org.example;

import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

public class VertexClicker {
    private int selectedVertexId = -1;  // Идентификатор выбранной вершины

    // Массив, содержащий все вершины для выбора
    private List<float[]> vertices;

    public VertexClicker(List<float[]> vertices) {
        this.vertices = vertices;
    }

    // Функция для рисования объектов с уникальными цветами
    public void renderPicking() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);  // Сохраняем все атрибуты состояния OpenGL

        // Отключаем освещение, чтобы все вершины были видимы
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);

        // Устанавливаем в фоновый цвет черный (или любой другой)
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // Отрисовываем объект с уникальными цветами для каждой вершины
        GL11.glBegin(GL11.GL_POINTS);
        for (int i = 0; i < vertices.size(); i++) {
            float[] vertex = vertices.get(i);
            // Генерируем уникальный цвет для каждой вершины
            GL11.glColor3f((i & 0xFF) / 255.0f, ((i >> 8) & 0xFF) / 255.0f, ((i >> 16) & 0xFF) / 255.0f);
            GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
        }
        GL11.glEnd();

        GL11.glPopAttrib();  // Восстанавливаем состояние OpenGL
    }

    // Функция для обработки клика мыши
    public void handleMouseClick(double mouseX, double mouseY, int width, int height) {
        int viewport[] = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);  // Получаем текущие размеры окна
        FloatBuffer modelviewMatrix = BufferUtils.createFloatBuffer(16);  // Создаём буфер для матрицы модели
        FloatBuffer projectionMatrix = BufferUtils.createFloatBuffer(16);  // Создаём буфер для проекционной матрицы
        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, modelviewMatrix);  // Получаем модельно-вью матрицу
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, projectionMatrix);  // Получаем проекционную матрицу

        // Преобразуем координаты мыши в 3D пространство вручную (без GLU)
        double[] winX = new double[1];
        double[] winY = new double[1];
        double[] winZ = new double[1];

        // Преобразуем координаты из 2D в 3D с помощью glReadPixels
        IntBuffer buffer = BufferUtils.createIntBuffer(1);
        GL11.glReadPixels((int) mouseX, height - (int) mouseY, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, buffer);
        float depth = buffer.get(0) / 255.0f;

        // Получаем 3D-координаты с помощью матриц
        FloatBuffer windowCoords = BufferUtils.createFloatBuffer(3);

        float x = windowCoords.get(0);
        float y = windowCoords.get(1);
        float z = windowCoords.get(2);

        // Используем данные для получения пикселя
        int pixelColor = readPixelColor((int) winX[0], (int) winY[0]);

        // Восстанавливаем выбор по цвету
        selectedVertexId = pixelColor;
        System.out.println("Selected vertex ID: " + selectedVertexId);
    }



    // Функция для чтения цвета пикселя
    private int readPixelColor(int x, int y) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        int color = buffer.get(0) & 0xFF;    // Красный компонент (идентификатор вершины)
        color |= (buffer.get(1) << 8) & 0xFF00; // Зеленый компонент
        color |= (buffer.get(2) << 16) & 0xFF0000; // Синий компонент
        return color;
    }

    // Возвращает id выбранной вершины
    public int getSelectedVertexId() {
        return selectedVertexId;
    }
}

package org.mmc2;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import java.nio.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;
import java.util.ArrayList;
import java.util.List;

public class OpenGLRenderer {
    private long window;
    private int vao, vbo, shaderProgram;

    public void init(List<Triangle> triangles) {
        // Инициализация GLFW
        if (!glfwInit()) throw new IllegalStateException("Failed to init GLFW");

        // Создание окна
        window = glfwCreateWindow(800, 600, "Marching Cubes", NULL, NULL);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();

        // Загрузка меша в GPU
        loadMesh(triangles);

        // Компиляция шейдеров
        shaderProgram = compileShaders();
    }

    private void loadMesh(List<Triangle> triangles) {
        // Конвертация треугольников в FloatBuffer
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(triangles.size() * 9);
        for (Triangle tri : triangles) {
            vertexBuffer.put((float) tri.v1.x).put((float) tri.v1.y).put((float) tri.v1.z);
            vertexBuffer.put((float) tri.v2.x).put((float) tri.v2.y).put((float) tri.v2.z);
            vertexBuffer.put((float) tri.v3.x).put((float) tri.v3.y).put((float) tri.v3.z);
        }
        vertexBuffer.flip();

        // Создание VAO и VBO
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        // Указание атрибутов вершин
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private int compileShaders() {
        // Вершинный шейдер
        String vertexShaderSource = "#version 330 core\n" +
                "layout (location = 0) in vec3 aPos;\n" +
                "void main() {\n" +
                "   gl_Position = vec4(aPos, 1.0);\n" +
                "}";

        // Фрагментный шейдер
        String fragmentShaderSource = "#version 330 core\n" +
                "out vec4 FragColor;\n" +
                "void main() {\n" +
                "   FragColor = vec4(0.8, 0.3, 0.2, 1.0);\n" +
                "}";

        // Компиляция
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexShaderSource);
        glCompileShader(vertexShader);

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentShaderSource);
        glCompileShader(fragmentShader);

        // Линковка
        int shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);

        // Удаление шейдеров
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        return shaderProgram;
    }

    public void render(List<Triangle> triangles) {
        glClearColor(0.2f, 0.3f, 0.3f, 1.0f);
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glUseProgram(shaderProgram);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, triangles.size() * 3); // 3 вершины на треугольник

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    public void cleanup() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteProgram(shaderProgram);
        glfwTerminate();
    }
}
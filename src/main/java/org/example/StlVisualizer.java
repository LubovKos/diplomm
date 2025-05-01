package org.example;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL11;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;

public class StlVisualizer {

    private long window; // Идентификатор окна, созданного через GLFW
    private int width = 800; // Ширина окна по умолчанию
    private int height = 600; // Высота окна по умолчанию
    private float positionX = 0.0f; // Переменная для позиции по оси X (может использоваться для анимации)
    private float direction = 0.05f; // Шаг перемещения или скорость (для анимации)

    // Положение камеры в 3D-пространстве
    private float cameraX = 0.0f;
    private float cameraY = 0.0f;
    private float cameraZ = -15.0f; // Смещаем камеру назад, чтобы объект был виден

    // Параметры вращения объекта
    private float rotationX = 0.0f;
    private float rotationY = 0.0f;
    private boolean rotating = false; // Флаг, указывающий, происходит ли вращение (при зажатой средней кнопке мыши)
    private double lastX = 0.0, lastY = 0.0; // Последняя позиция мыши для вычисления смещения

    // Идентификаторы VBO (Vertex Buffer Object) для хранения данных вершин и нормалей
    private int vboVertexHandle;
    private int vboNormalHandle;

    public void init() {
        // Инициализация GLFW. Если не удалось, выбрасываем исключение.
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Создание окна с заданной шириной, высотой и заголовком "STL Viewer"
        window = GLFW.glfwCreateWindow(width, height, "STL Viewer", 0, 0);
        if (window == 0) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Устанавливаем созданное окно как текущий контекст OpenGL
        GLFW.glfwMakeContextCurrent(window);
        // Инициализируем возможности OpenGL для текущего контекста
        GL.createCapabilities();

        // Настройка проекции (матрицы проекции)
        GL11.glMatrixMode(GL11.GL_PROJECTION); // Переключаемся в режим матрицы проекции
        GL11.glLoadIdentity(); // Сбрасываем матрицу проекции к единичной матрице
        float aspect = (float) width / (float) height; // Вычисляем соотношение сторон окна
        // Задаем перспективную проекцию через усеченную пирамиду (frustum)
        GL11.glFrustum(-aspect, aspect, -1.0, 1.0, 1.0, 100.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); // Переключаемся в режим моделирования и просмотра

        GL11.glEnable(GL11.GL_DEPTH_TEST); // Включаем тест глубины для корректного отображения перекрывающихся объектов

        // Устанавливаем цвет, которым будет очищаться экран (белый фон)
        GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Колбэк для обработки изменения размера окна
        GLFW.glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
            this.width = width;   // Обновляем ширину
            this.height = height; // Обновляем высоту
            GL11.glViewport(0, 0, width, height); // Устанавливаем новую область просмотра
            GL11.glMatrixMode(GL11.GL_PROJECTION); // Переключаемся в режим матрицы проекции
            GL11.glLoadIdentity(); // Сбрасываем матрицу проекции
            float newAspect = (float) width / (float) height; // Пересчитываем соотношение сторон
            // Обновляем перспективную проекцию с новыми размерами
            GL11.glFrustum(-newAspect, newAspect, -1.0, 1.0, 1.0, 100.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); // Возвращаемся в режим моделирования и просмотра
        });

        // Колбэк для обработки нажатий клавиш
        GLFW.glfwSetKeyCallback(window, new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                // Обрабатываем только нажатие или повторное нажатие клавиши (игнорируем отпускание)
                if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                    switch (key) {
                        case GLFW.GLFW_KEY_W:
                            cameraY -= 0.1f; // При нажатии W смещаем камеру вперед (вниз по оси Y)
                            break;
                        case GLFW.GLFW_KEY_S:
                            cameraY += 0.1f; // При нажатии S смещаем камеру назад (вверх по оси Y)
                            break;
                        case GLFW.GLFW_KEY_A:
                            cameraX += 0.1f; // При нажатии A смещаем камеру влево (по оси X)
                            break;
                        case GLFW.GLFW_KEY_D:
                            cameraX -= 0.1f; // При нажатии D смещаем камеру вправо (по оси X)
                            break;
                    }
                }
            }
        });

        // Колбэк для обработки нажатия и отпускания средней кнопки мыши
        GLFW.glfwSetMouseButtonCallback(window, new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                // Проверяем, что событие связано со средней кнопкой мыши
                if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    if (action == GLFW.GLFW_PRESS) {
                        rotating = true; // Начинаем вращение при нажатии
                    } else if (action == GLFW.GLFW_RELEASE) {
                        rotating = false; // Прекращаем вращение при отпускании
                    }
                }
            }
        });

        // Колбэк для обработки перемещения мыши
        GLFW.glfwSetCursorPosCallback(window, new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                if (rotating) { // Если активировано вращение
                    double deltaX = xpos - lastX; // Вычисляем смещение по оси X
                    double deltaY = ypos - lastY; // Вычисляем смещение по оси Y

                    // Изменяем углы поворота объекта, умножая на коэффициент для регулировки скорости вращения
                    rotationX += (float) deltaY * 0.1f;
                    rotationY += (float) deltaX * 0.1f;
                }
                // Обновляем последнюю позицию курсора
                lastX = xpos;
                lastY = ypos;
            }
        });

        // Колбэк для обработки прокрутки колесика мыши (масштабирование)
        GLFW.glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            // Изменяем положение камеры по оси Z для приближения/отдаления
            cameraZ += yoffset * 0.2f;  // Коэффициент регулирует скорость масштабирования
        });
    }

    public void setupVBO(List<StlReader.Triangle> triangles) {
        // Создаем буферы для вершин и нормалей.
        // Для каждого треугольника выделяем 9 значений (3 вершины * 3 координаты)
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(triangles.size() * 9);
        // Для нормалей аналогично – 9 значений (3 нормали * 3 координаты) даже если нормаль одна для всего треугольника
        FloatBuffer normalBuffer = BufferUtils.createFloatBuffer(triangles.size() * 9);

        // Заполняем буферы данными из STL-файла
        for (StlReader.Triangle triangle : triangles) {
            for (int i = 0; i < 3; i++) { // Обрабатываем 3 вершины треугольника
                // Добавляем координаты вершины в буфер
                vertexBuffer.put(triangle.vertices[i][0]);
                vertexBuffer.put(triangle.vertices[i][1]);
                vertexBuffer.put(triangle.vertices[i][2]);

                // Добавляем координаты нормали (одинаковые для всех вершин треугольника)
                normalBuffer.put(triangle.normal[0]);
                normalBuffer.put(triangle.normal[1]);
                normalBuffer.put(triangle.normal[2]);
            }
        }

        // Переводим буферы в режим чтения
        vertexBuffer.flip();
        normalBuffer.flip();

        // Создаем VBO для вершин: генерируем идентификатор буфера
        vboVertexHandle = GL15.glGenBuffers();
        // Привязываем созданный буфер к цели GL_ARRAY_BUFFER
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboVertexHandle);
        // Передаем данные вершин в видеопамять с режимом STATIC_DRAW (данные не меняются часто)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STATIC_DRAW);

        // Создаем VBO для нормалей аналогичным образом
        vboNormalHandle = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboNormalHandle);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, normalBuffer, GL15.GL_STATIC_DRAW);

        // Отвязываем буфер, чтобы случайно не изменить его в будущем
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void render(List<StlReader.Triangle> triangles) {
        // Очищаем буферы цвета и глубины для подготовки нового кадра
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // Сбрасываем матрицу модельно-видовой (начинаем с единичной матрицы)
        GL11.glLoadIdentity();
        // Перемещаем сцену согласно положению камеры
        GL11.glTranslatef(cameraX, cameraY, cameraZ);

        // Применяем повороты к сцене, чтобы изменить угол обзора
        GL11.glRotatef(rotationX, 1.0f, 0.0f, 0.0f); // Вращение вокруг оси X
        GL11.glRotatef(rotationY, 0.0f, 1.0f, 0.0f); // Вращение вокруг оси Y

        // Включаем систему освещения OpenGL
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0); // Активируем первый источник света
        GL11.glEnable(GL11.GL_COLOR_MATERIAL); // Разрешаем использовать цвет как материал для освещения

        // Настраиваем параметры источника света
        FloatBuffer lightPosition = BufferUtils.createFloatBuffer(4); // Буфер для позиции света (4 значения: x, y, z, w)
        lightPosition.put(new float[]{5.0f, 5.0f, 5.0f, 1.0f}).flip(); // Устанавливаем позицию и переводим буфер в режим чтения
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_POSITION, lightPosition); // Применяем позицию для LIGHT0

        FloatBuffer lightColor = BufferUtils.createFloatBuffer(4); // Буфер для цвета света
        lightColor.put(new float[]{1.0f, 1.0f, 1.0f, 1.0f}).flip(); // Устанавливаем белый цвет света
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, lightColor); // Задаем диффузное (рассеянное) освещение

        // Устанавливаем цвет для отрисовки фигур (белый)
        GL11.glColor3f(1.0f, 1.0f, 1.0f);

        // Включаем автоматическую нормализацию нормалей для корректного освещения при масштабировании
        GL11.glEnable(GL11.GL_NORMALIZE);

        // Отрисовка треугольников (залитая поверхность)
        GL11.glBegin(GL11.GL_TRIANGLES); // Начинаем режим отрисовки треугольников
        for (StlReader.Triangle triangle : triangles) {
            // Устанавливаем нормаль для треугольника
            GL11.glNormal3f(triangle.normal[0], triangle.normal[1], triangle.normal[2]);
            for (int i = 0; i < 3; i++) { // Проходим по каждой вершине треугольника
                GL11.glVertex3f(triangle.vertices[i][0], triangle.vertices[i][1], triangle.vertices[i][2]);
            }
        }
        GL11.glEnd(); // Завершаем режим отрисовки треугольников

        // Отключаем освещение для отрисовки контуров без влияния света
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_LIGHT0);
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);

        // Задаем цвет линий для контуров (черный)
        GL11.glColor3f(0.0f, 0.0f, 0.0f);
        // Устанавливаем толщину линий
        GL11.glLineWidth(1.0f);

        // Отрисовка ребер треугольников (контур модели)
//        GL11.glBegin(GL11.GL_LINE_LOOP); // Начинаем режим рисования замкнутой линии
//        for (StlReader.Triangle triangle : triangles) {
//            for (int i = 0; i < 3; i++) { // Для каждой вершины треугольника
//                GL11.glVertex3f(triangle.vertices[i][0], triangle.vertices[i][1], triangle.vertices[i][2]);
//            }
//        }
        GL11.glEnd(); // Завершаем режим рисования линий

        // Рисуем жирные точки в вершинах для выделения ключевых точек модели
        GL11.glPointSize(5.0f); // Устанавливаем размер точек
        GL11.glBegin(GL11.GL_POINTS); // Начинаем режим рисования точек
        GL11.glColor3f(0.0f, 0.0f, 0.0f); // Устанавливаем цвет точек (черный)
        for (StlReader.Triangle triangle : triangles) {
            for (int i = 0; i < 3; i++) { // Проходим по всем вершинам
                GL11.glVertex3f(triangle.vertices[i][0], triangle.vertices[i][1], triangle.vertices[i][2]);
            }
        }

         GL11.glEnd(); // Завершаем режим рисования точек

        // Восстанавливаем настройки освещения для последующих операций
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
    }

    public void loop(String s) throws IOException {
        // Создаем объект для чтения STL-файла
        StlReader reader = new StlReader();
        List<StlReader.Triangle> triangles = null; // Список для хранения треугольников модели
        try {
            // Используем BufferedReader для чтения файла
            try (BufferedReader br = new BufferedReader(new FileReader(s))) {
                String firstLine = br.readLine().trim(); // Считываем первую строку файла
                // Если файл начинается со слова "solid", предполагается ASCII STL-файл
                if (firstLine.startsWith("solid")) {
                    triangles = reader.readStlFile(s);
                } else {
                    // Иначе предполагается бинарный STL-файл
                    triangles = reader.readBinaryStlFile(s);
                }
            }
            if (triangles != null) {
                setupVBO(triangles); // Если треугольники успешно загружены, настраиваем VBO
            }
        } catch (IOException e) {
            e.printStackTrace(); // Выводим стек вызовов при ошибке
            System.err.println("Ошибка при чтении STL-файла: " + e.getMessage());
            return; // Прерываем выполнение метода, если произошла ошибка при чтении файла
        }

        // Основной цикл программы, выполняется, пока окно не будет закрыто
        while (!GLFW.glfwWindowShouldClose(window)) {
            if (triangles != null) {
                render(triangles); // Отрисовываем сцену, если модель загружена
            }
            GLFW.glfwSwapBuffers(window); // Меняем буферы, чтобы отобразить новый кадр
            GLFW.glfwPollEvents(); // Обрабатываем события (клавиатура, мышь и т.д.)
        }

        // Освобождаем ресурсы: удаляем VBO и закрываем окно
        GL15.glDeleteBuffers(vboVertexHandle);
        GL15.glDeleteBuffers(vboNormalHandle);
        GLFW.glfwDestroyWindow(window); // Разрушаем окно
        GLFW.glfwTerminate(); // Завершаем работу GLFW, освобождая ресурсы
    }

    public static void main(String[] args) throws IOException {
        // Точка входа в приложение
        StlVisualizer visualizer = new StlVisualizer(); // Создаем экземпляр визуализатора
        visualizer.init(); // Инициализируем окно и OpenGL
        visualizer.loop("mesh_output.stl"); // Запускаем основной цикл, передавая имя STL-файла для визуализации
    }
}

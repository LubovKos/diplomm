package org.example;

import org.bytedeco.opencv.opencv_core.Point2d;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.opengl.*;
import org.joml.Vector3f;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.calib3d.*;
import org.opencv.imgcodecs.*;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import org.lwjgl.opengl.GL;
import java.util.ArrayList;


public class OpenGL3DReconstruction {
    static { System.load("C:\\opencv\\build\\java\\x64\\opencv_java4110.dll"); } // Загрузка OpenCV

    private long window;
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

    private void initOpenGL() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        window = glfwCreateWindow(800, 600, "3D Reconstruction", 0, 0);
        if (window == 0) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        // Настройки OpenGL
        glEnable(GL_DEPTH_TEST); // Включаем тест глубины
        glClearColor(0.2f, 0.2f, 0.2f, 1.0f); // Серый фон

        // Установка перспективы вручную
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        double aspect = 800.0 / 600.0;
        glFrustum(-aspect, aspect, -1, 1, 1.5, 1000); // Задаем перспективу вручную
        glMatrixMode(GL_MODELVIEW);

        // Колбэк для обработки изменения размера окна
        org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
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

        // Ограничения на положение камеры по оси Z
        final float MIN_ZOOM = -0.1f;  // Минимальное приближение
        final float MAX_ZOOM = -100.0f; // Максимальное отдаление

        GLFW.glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            // Используем логарифмическое масштабирование
            float scaleFactor = (yoffset > 0) ? 0.9f : 1.1f;
            cameraZ *= scaleFactor;

            // Ограничиваем диапазон значений Z
            if (cameraZ > MIN_ZOOM) cameraZ = MIN_ZOOM;
            if (cameraZ < MAX_ZOOM) cameraZ = MAX_ZOOM;
        });
    }

    // Функция для рисования координатных осей X, Y, Z
    private void drawAxes() {
        GL11.glLineWidth(2.0f); // Устанавливаем толщину линий

        GL11.glBegin(GL11.GL_LINES);

        // Ось X (красная)
        GL11.glColor3f(1.0f, 0.0f, 0.0f);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(1000, 0, 0);

        // Ось Y (зеленая)
        GL11.glColor3f(0.0f, 1.0f, 0.0f);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(0, 1000, 0);

        // Ось Z (синяя)
        GL11.glColor3f(0.0f, 0.0f, 1.0f);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(0, 0, 1000);

        GL11.glEnd();
    }

    // Функция для рисования координатной сетки
    private void drawGrid() {
        GL11.glLineWidth(1.0f);
        GL11.glColor3f(0.5f, 0.5f, 0.5f); // Серый цвет сетки
        GL11.glBegin(GL11.GL_LINES);

        int gridSize = 100; // Размер сетки
        float step = 1.0f;  // Шаг сетки

        // Линии параллельные оси X (XZ плоскость)
        for (int i = -gridSize; i <= gridSize; i++) {
            GL11.glVertex3f(i * step, 0, -gridSize * step);
            GL11.glVertex3f(i * step, 0, gridSize * step);
        }

        // Линии параллельные оси Z (XZ плоскость)
        for (int i = -gridSize; i <= gridSize; i++) {
            GL11.glVertex3f(-gridSize * step, 0, i * step);
            GL11.glVertex3f(gridSize * step, 0, i * step);
        }

        GL11.glEnd();
    }

    private void render(List<Vector3f> points) {
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


        // Включаем сглаживание точек
        GL11.glEnable(GL11.GL_POINT_SMOOTH);
        GL11.glHint(GL11.GL_POINT_SMOOTH_HINT, GL11.GL_NICEST);

        // Рисуем координатные оси
        drawAxes();

        // Рисуем координатную сетку
        drawGrid();

        // Рисуем точки (зеленые круги)
        GL11.glPointSize(5.0f); // Размер точек
        GL11.glBegin(GL11.GL_POINTS);
        GL11.glColor3f(0.0f, 1.0f, 0.0f); // Зеленый цвет
        for (Vector3f v : points) {
            GL11.glVertex3f(v.x, v.y, v.z);
        }

        GL11.glEnd(); // Завершаем режим рисования точек

        // Восстанавливаем настройки освещения для последующих операций
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
    }

    private void loop(List<Vector3f> points3D) {
        //List<int[]> triangles = Triangulation.delaunayTriangulation(points3D);
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            render(points3D);
            glfwSwapBuffers(window);
            glfwPollEvents();
        }

    }



//    private void render3DModel(List<Vector3f> points3D) {
//        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
//        glLoadIdentity();
//        glTranslatef(0.0f, 0.0f, -50.0f); // Отодвигаем камеру дальше
//
//        glPointSize(5.0f); // Увеличиваем размер точек
//        glBegin(GL_POINTS);
//        glColor3f(1.0f, 0.0f, 0.0f); // Красные точки
//
//        for (Vector3f point : points3D) {
//            glVertex3f(point.x, point.y, point.z);
//        }
//        glEnd();
//    }
//
//
//    private void loop(List<Vector3f> points3D) {
//        while (!glfwWindowShouldClose(window)) {
//            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
//            render3DModel(points3D);
//            glfwSwapBuffers(window);
//            glfwPollEvents();
//        }
//    }

    private void cleanUp() {
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private static List<DMatch> filterMatchesKRatio(List<DMatch> matches, List<List<DMatch>> knnMatches, double ratio) {
        List<DMatch> goodMatches = new ArrayList<>();
        for (List<DMatch> pair : knnMatches) {
            if (pair.size() < 2) continue;
            if (pair.get(0).distance < ratio * pair.get(1).distance) {
                goodMatches.add(pair.get(0));
            }
        }
        return goodMatches;
    }

    private static List<DMatch> filterMatchesLeftRight(List<DMatch> matches1, List<DMatch> matches2) {
        Map<Integer, Integer> map12 = new HashMap<>();
        Map<Integer, Integer> map21 = new HashMap<>();

        for (DMatch m : matches1) {
            map12.put(m.queryIdx, m.trainIdx);
        }
        for (DMatch m : matches2) {
            map21.put(m.trainIdx, m.queryIdx);
        }

        List<DMatch> goodMatches = new ArrayList<>();
        for (DMatch m : matches1) {
            if (map21.containsKey(m.trainIdx) && map21.get(m.trainIdx) == m.queryIdx) {
                goodMatches.add(m);
            }
        }
        return goodMatches;
    }

    private static Point pixelToCam(Point p, Mat K) {
        return new Point(
                (float) ((p.x - K.get(0, 2)[0]) / K.get(0, 0)[0]),
                (float) ((p.y - K.get(1, 2)[0]) / K.get(1, 1)[0])
        );
    }

    public static void main(String[] args) {
        // Загрузка изображений в градациях серого
        Mat img1 = Imgcodecs.imread("cub/cub6.jpg", Imgcodecs.IMREAD_GRAYSCALE);
        Mat img2 = Imgcodecs.imread("cub/cub7.jpg", Imgcodecs.IMREAD_GRAYSCALE);

        if (img1.empty() || img2.empty()) {
            System.out.println("Ошибка загрузки изображений!");
            return;
        }

        // Используем SIFT для обнаружения и описания ключевых точек
        //ORB sift = ORB.create();
        SIFT sift = SIFT.create(10000);
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
        Mat descriptors1 = new Mat();
        Mat descriptors2 = new Mat();

        sift.detectAndCompute(img1, new Mat(), keypoints1, descriptors1);
        sift.detectAndCompute(img2, new Mat(), keypoints2, descriptors2);

        // Отрисовка ключевых точек на изображениях
        Mat imgKeypoints1 = new Mat();
        Mat imgKeypoints2 = new Mat();
        Features2d.drawKeypoints(img1, keypoints1, imgKeypoints1);
        Features2d.drawKeypoints(img2, keypoints2, imgKeypoints2);
        Imgcodecs.imwrite("keypoints1.jpg", imgKeypoints1);
        Imgcodecs.imwrite("keypoints2.jpg", imgKeypoints2);

        // Поиск совпадений между точками с помощью Brute-Force
//        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
//        List<List<DMatch>> knnMatches1 = new ArrayList<>();
//        List<List<DMatch>> knnMatches2 = new ArrayList<>();
//        matcher.knnMatch(descriptors1, descriptors2, knnMatches1, 2);
//        matcher.knnMatch(descriptors2, descriptors1, knnMatches2, 2);
//
//        List<DMatch> goodMatches = filterMatchesKRatio(new ArrayList<>(), knnMatches1, 0.75);
//        goodMatches = filterMatchesLeftRight(goodMatches, filterMatchesKRatio(new ArrayList<>(), knnMatches2, 0.75));
//
//        MatOfDMatch goodMatchesMat = new MatOfDMatch();
//        goodMatchesMat.fromList(goodMatches);
//        Mat imgMatches = new Mat();
//        Features2d.drawMatches(img1, keypoints1, img2, keypoints2, goodMatchesMat, imgMatches);

        // ratio test
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
        List<MatOfDMatch> matches = new ArrayList<MatOfDMatch>();
        matcher.knnMatch(descriptors1, descriptors2, matches, 5);
        System.out.println(matches.size());

        // смотрим на все мэтчи


        // Преобразование списка в MatOfDMatch для отрисовки
        List<DMatch> allMatches = new ArrayList<>();
        for (MatOfDMatch matOfDMatch : matches) {
            allMatches.addAll(matOfDMatch.toList());
        }

        MatOfDMatch allMatchesMat = new MatOfDMatch();
        allMatchesMat.fromList(allMatches);

        // Отрисовка всех совпадений
        Mat imgMatches = new Mat();
        Features2d.drawMatches(img1, keypoints1, img2, keypoints2, allMatchesMat, imgMatches);

        // Сохранение результата
        Imgcodecs.imwrite("all_matches.jpg", imgMatches);





        // находим лучшие мэтчи

        LinkedList<DMatch> good_matches = new LinkedList<DMatch>();
        for (Iterator<MatOfDMatch> iterator = matches.iterator(); iterator.hasNext();) {
            MatOfDMatch matOfDMatch = (MatOfDMatch) iterator.next();
            if (matOfDMatch.toArray()[0].distance / matOfDMatch.toArray()[1].distance < 0.9) {
                good_matches.add(matOfDMatch.toArray()[0]);
            }
        }

        // координаты ключевых точек для точного соответствия, чтобы найти гомографию и удалить выбросы с помощью ransac
        List<Point> pts1 = new ArrayList<Point>();
        List<Point> pts2 = new ArrayList<Point>();
        for(int i = 0; i<good_matches.size(); i++){
            pts1.add(keypoints1.toList().get(good_matches.get(i).queryIdx).pt);
            pts2.add(keypoints2.toList().get(good_matches.get(i).trainIdx).pt);
        }

        // преобразование типов данных
        Mat outputMask = new Mat();
        MatOfPoint2f pts1Mat = new MatOfPoint2f();
        pts1Mat.fromList(pts1);
        MatOfPoint2f pts2Mat = new MatOfPoint2f();
        pts2Mat.fromList(pts2);

        // Находим гомографию — здесь она используется только для фильтрации совпадений с помощью RANSAC,
        // но может применяться, например, для совмещения изображений
        // чем меньше допустимая ошибка перепроектирования (здесь 15), тем больше совпадений отфильтровывается

        Mat Homog = Calib3d.findHomography(pts1Mat, pts2Mat, Calib3d.RANSAC, 50, outputMask, 2000, 0.995);

        // outputMask содержит нули и единицы, указывающие, какие совпадения будут отфильтрованы
        LinkedList<DMatch> better_matches = new LinkedList<DMatch>();
        for (int i = 0; i < good_matches.size(); i++) {
            if (outputMask.get(i, 0)[0] != 0.0) {
                better_matches.add(good_matches.get(i));
            }
        }

        // отрисовка мэтчей
        Mat outputImg = new Mat();
        MatOfDMatch better_matches_mat = new MatOfDMatch();
        better_matches_mat.fromList(better_matches);
        Features2d.drawMatches(img1, keypoints1, img2, keypoints2, better_matches_mat, outputImg);
        Imgcodecs.imwrite("good_matches.jpg", outputImg);

        // Матрица внутренних параметров камеры
        Mat K = Mat.eye(3, 3, CvType.CV_64F);
        double focal = 1170;
        double cx = 638.3097505828375;
        double cy = 468.5645410324926;
        K.put(0, 0, focal);  // fx
        K.put(1, 1, focal);  // fy
        K.put(0, 2, cx);   // cx
        K.put(1, 2, cy);   // cy

        System.out.println("\nK:\n" + K.dump());

        // Извлечение координат сопоставленных точек
        List<Point> points1 = new ArrayList<>();
        List<Point> points2 = new ArrayList<>();

        List<Point> point1 = new ArrayList<>();
        List<Point> point2 = new ArrayList<>();

        for (DMatch match : better_matches) {
            Point pt1 = keypoints1.toList().get(match.queryIdx).pt;
            points1.add(pt1);
            point1.add(pixelToCam(pt1, K));
            Point pt2 = keypoints2.toList().get(match.trainIdx).pt;
            points2.add(pt2);
            point2.add(pixelToCam(pt2, K));
        }

        System.out.println(better_matches.size());

        // Преобразуем списки точек в MatOfPoint2f
        MatOfPoint2f points1Mat = new MatOfPoint2f(points1.toArray(new Point[0]));
        MatOfPoint2f points2Mat = new MatOfPoint2f(points2.toArray(new Point[0]));

        Point principal_point = new Point(cx, cy);
        // Находим матрицу Essential Matrix (E)
        Mat E1 = Calib3d.findEssentialMat(
                points1Mat,
                points2Mat,
                K,                  // Матрица камеры
                Calib3d.LMEDS,     // Метод RANSAC
                0.999,              // Вероятность
                1.0                 // Порог ошибки RANSAC
        );

        Mat E = Calib3d.findEssentialMat(points1Mat, points2Mat, focal, principal_point);

        System.out.println("\nE:\n" + E.dump());
        System.out.println("\nE1:\n" + E1.dump());

        // Восстановление положения камеры
        // Создаем маску для хранения инлайеров (совпадающих точек, прошедших RANSAC)
        Mat mask = new Mat();

        // Матрицы для хранения результата (вращение и трансляция)
        Mat R = new Mat();
        Mat t = new Mat();

        // Восстанавливаем позу (вращение и перемещение камеры)
        int inliers = Calib3d.recoverPose(E, points1Mat, points2Mat, K, R, t, mask);

        // Выводим количество инлайеров (точек, прошедших фильтрацию)
        System.out.println("Number of inliers: " + inliers);

        // Формирование проекционных матриц
        Mat P0 = Mat.eye(3, 4, CvType.CV_64F);
        Mat P1 = new Mat(3, 4, CvType.CV_64F);
        R.copyTo(P1.colRange(0, 3));
        t.copyTo(P1.col(3));

        System.out.println("Size of points1Mat: " + points1Mat.rows() + "x" + points1Mat.cols());
        System.out.println("Size of points2Mat: " + points2Mat.rows() + "x" + points2Mat.cols());

        // Вычисление гомографии на основе восстановленного движения

//        Mat H = Calib3d.findHomography(points2Mat, points1Mat, Calib3d.RANSAC, 5.0);
//
//        if (H.empty()) {
//            System.out.println("Ошибка: не удалось вычислить гомографию.");
//            return;
//        }
//
//        Size resultSize = new Size(img1.cols() * 2, img1.rows() * 2);
//        Mat warpedImg2 = new Mat();
//        Imgproc.warpPerspective(img2, warpedImg2, H, resultSize);
//
//        Mat panorama = new Mat(resultSize, img1.type());
//        panorama.setTo(new Scalar(0, 0, 0));
//        img1.copyTo(panorama.rowRange(0, img1.rows()).colRange(0, img1.cols()));
//
//        Mat maskk = new Mat();
//        Imgproc.threshold(warpedImg2, maskk, 1, 255, Imgproc.THRESH_BINARY);
//        img1.copyTo(panorama, maskk);
//
//        Imgcodecs.imwrite("stitched.jpg", panorama);
//        System.out.println("Сохранено склеенное изображение: stitched.jpg");

        // Выровненное изображение (перспективное преобразование)
       // Mat rgbRWarped = new Mat();
//        Imgproc.warpPerspective(rgbR, rgbRWarped, H,
//                new Size(rgbL.cols() + rgbR.cols(), rgbL.rows()));
//
//        // Копируем левое изображение в соответствующую область результирующего изображения
//        rgbL.copyTo(rgbRWarped.submat(0, rgbL.rows(), 0, rgbL.cols()));

        // Сохранение или отображение результата
        //Imgcodecs.imwrite("stitched_image.jpg", rgbRWarped);

        System.out.println(better_matches.size());


        // Преобразуем списки точек в MatOfPoint2f
        MatOfPoint2f point1Mat = new MatOfPoint2f(point1.toArray(new Point[0]));
        MatOfPoint2f point2Mat = new MatOfPoint2f(point2.toArray(new Point[0]));

        Mat points4D = new Mat();
        Calib3d.triangulatePoints(P0, P1, point1Mat, point2Mat, points4D);
        System.out.println("Size of points4D: " + points4D.rows() + "x" + points4D.cols());

        // Конвертация гомогенных координат в 3D-точки
//        List<Vector3f> points3D = new ArrayList<>();
//        for (int i = 0; i < points4D.cols(); i++) {
//            double[] homPoint = points4D.get(0, i);
//            float x = (float) (homPoint[0] / homPoint[3]);
//            float y = (float) (homPoint[1] / homPoint[3]);
//            float z = (float) (homPoint[2] / homPoint[3]);
//            points3D.add(new Vector3f(x, y, z));
//        }

        // Конвертация гомогенных координат в 3D-точки
        List<Vector3f> points3D = new ArrayList<>();

        for (int i = 0; i < points4D.cols(); i++) {
            double x = points4D.get(0, i)[0];
            double y = points4D.get(1, i)[0];
            double z = points4D.get(2, i)[0];
            double w = points4D.get(3, i)[0]; // гомогенная координата

            if (w != 0) { // Проверяем, чтобы w ≠ 0
                points3D.add(new Vector3f((float) (x / w), (float) (y / w), (float) (z / w)));
            } else {
                System.out.println("Warning: Skipping point " + i + " due to w = 0");
            }
        }

        // Преобразование points4D в MatOfPoint3f
        List<Point3> point3DList = new ArrayList<>();
        for (int i = 0; i < points4D.cols(); i++) {
            double x = points4D.get(0, i)[0];
            double y = points4D.get(1, i)[0];
            double z = points4D.get(2, i)[0];
            double w_val = points4D.get(3, i)[0];
            if (w_val != 0) { // Избегаем деления на ноль
                point3DList.add(new Point3(x / w_val, y / w_val, z / w_val));
            }
        }

        System.out.println("Total valid 3D points: " + points3D.size());

        //проверки

        // Проверка матрицы E
        Mat E_check = K.t().inv().mul(E).mul(K.inv());
        System.out.println("\nE * K^-T * F * K^-1:\n" + E_check.dump());

        // Проверка сингулярных значений матрицы E
        Mat w = new Mat();
        Mat u = new Mat();
        Mat vt = new Mat();
        Core.SVDecomp(E, w, u, vt);
        System.out.println("\nSingular values of E: " + w.dump());

        // Проверка проекционных матриц
        System.out.println("\nProjection Matrix P0:\n" + P0.dump());
        System.out.println("\nProjection Matrix P1:\n" + P1.dump());
        System.out.println("\nR:\n" + R.dump());


//        // Проверка, восстанавливают ли P0 и P1 исходные 2D точки
//        MatOfPoint3f points3DMat = new MatOfPoint3f();
//        points3DMat.fromList(point3DList);
//
//        // Проверка, восстанавливают ли P0 и P1 исходные 2D точки
//        MatOfPoint2f projected1 = new MatOfPoint2f();
//        MatOfPoint2f projected2 = new MatOfPoint2f();
//
//        // Преобразование матрицы вращения R в вектор поворота rvec
//        Mat rvec = new Mat();
//        Calib3d.Rodrigues(R, rvec);
//
//        // Проверяем размерности rvec и tvec
//        System.out.println("rvec size: " + rvec.size()); // Должно быть 3x1
//        System.out.println("t size: " + t.size());       // Должно быть 3x1
//
//        t = t.t(); // Делаем вектор-столбец 3x1
//
//        System.out.println("Reshaped t size: " + t.size()); // Должно быть 3x1
//
//
//        // Проекция 3D-точек на первый и второй кадр
//        Calib3d.projectPoints(points3DMat, new Mat(), new Mat(), K, new MatOfDouble(), projected1);
//        Calib3d.projectPoints(points3DMat, rvec, t, K, new MatOfDouble(), projected2);
//
//
//        System.out.println("\nReprojected points1Mat:\n" + projected1.dump());
//        System.out.println("\nReprojected points2Mat:\n" + projected2.dump());
//
//        // Проверка, насколько reprojection error мала
//        Mat diff1 = new Mat();
//        Mat diff2 = new Mat();
//        Core.absdiff(points1Mat, projected1, diff1);
//        Core.absdiff(points2Mat, projected2, diff2);
//        System.out.println("\nReprojection error (image 1):\n" + Core.norm(diff1));
//        System.out.println("\nReprojection error (image 2):\n" + Core.norm(diff2));
//
//        // Проверка 3D-точек
//        System.out.println("\nTotal valid 3D points: " + points3D.size());
//        for (Vector3f point : points3D) {
//            System.out.println(String.format("vertex %.6f %.6f %.6f", point.x, point.y, point.z));
//        }


        //STLWriter.saveToSTL("points3D.stl", points3D);
//        for (Vector3f point : points3D) {
//            System.out.println(String.format("vertex %.6f %.6f %.6f\n", point.x, point.y, point.z));
//        }
//
//
//        System.out.println("end\n");

        // Запуск OpenGL визуализации
        OpenGL3DReconstruction renderer = new OpenGL3DReconstruction();
        renderer.initOpenGL();
        renderer.loop(points3D);
        renderer.cleanUp();
    }
}

package org.example;
import com.github.quickhull3d.QuickHull3D;
import com.github.quickhull3d.Point3d;


import com.github.quickhull3d.Vector3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.opengl.*;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.calib3d.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;
import org.opencv.imgcodecs.Imgcodecs;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import org.lwjgl.opengl.GL;
import java.util.ArrayList;


public class MultiReconstruction {
    static {
        System.load("C:\\opencv\\build\\java\\x64\\opencv_java4110.dll");
    } // Загрузка OpenCV

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
    // Параметры фильтрации
    private static final int K_NEIGHBORS = 10;
    private static final float STD_DEV_THRESHOLD = 1.0f;

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


    private void cleanUp() {
        glfwDestroyWindow(window);
        glfwTerminate();
    }


    // чтобы затем работать с точками в системе камеры, а не в пикселях.
    private static Point pixelToCam(Point p, Mat K) {
        return new Point(
                (float) ((p.x - K.get(0, 2)[0]) / K.get(0, 0)[0]),
                (float) ((p.y - K.get(1, 2)[0]) / K.get(1, 1)[0])
        );
    }

    public static class FeatureData {
        public MatOfKeyPoint keypoints;
        public Mat descriptors;
        public Mat image;

        public FeatureData(Mat image, MatOfKeyPoint keypoints, Mat descriptors) {
            this.image = image;
            this.keypoints = keypoints;
            this.descriptors = descriptors;
        }
    }

    public static class CameraPose {
        public Mat R;
        public Mat t;

        public CameraPose(Mat R, Mat t) {
            this.R = R;
            this.t = t;
        }
    }

    public static class MapPoint {
        public Vector3f point;
        public List<Integer> imageIndices;
        public List<Integer> keypointIndices;

        public MapPoint(Vector3f point) {
            this.point = point;
            this.imageIndices = new ArrayList<>();
            this.keypointIndices = new ArrayList<>();
        }
    }

    public static class Vector3f {
        public float x, y, z;

        public Vector3f(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static List<MapPoint> initializeReconstruction(
            FeatureData data1,
            FeatureData data2,
            Mat K,
            double focal,
            Point principalPoint,
            List<CameraPose> poses
    ) {
        List<MapPoint> mapPoints = new ArrayList<>();

        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        matcher.knnMatch(data1.descriptors, data2.descriptors, knnMatches, 5);

        LinkedList<DMatch> goodMatches = new LinkedList<>();
        for (MatOfDMatch matOfDMatch : knnMatches) {
            DMatch[] matches = matOfDMatch.toArray();
            if (matches[0].distance / matches[1].distance < 0.9) {
                goodMatches.add(matches[0]);
            }
        }

        List<Point> pts1 = new ArrayList<>();
        List<Point> pts2 = new ArrayList<>();
        for (DMatch match : goodMatches) {
            pts1.add(data1.keypoints.toList().get(match.queryIdx).pt);
            pts2.add(data2.keypoints.toList().get(match.trainIdx).pt);
        }

        Mat outputmask = new Mat();
        MatOfPoint2f pts1Mat = new MatOfPoint2f();
        pts1Mat.fromList(pts1);
        MatOfPoint2f pts2Mat = new MatOfPoint2f();
        pts2Mat.fromList(pts2);

        // Находим гомографию — здесь она используется только для фильтрации совпадений с помощью RANSAC
        Mat H = Calib3d.findHomography(pts1Mat, pts2Mat, Calib3d.RANSAC, 50, outputmask, 2000, 0.995);

        // outputMask содержит нули и единицы, указывающие, какие совпадения будут отфильтрованы
        LinkedList<DMatch> better_matches = new LinkedList<DMatch>();
        for (int i = 0; i < goodMatches.size(); i++) {
            if (outputmask.get(i, 0)[0] != 0.0) {
                better_matches.add(goodMatches.get(i));
            }
        }

        // Извлечение координат сопоставленных точек
        List<Point> points1 = new ArrayList<>();
        List<Point> points2 = new ArrayList<>();

        List<Point> normPts1 = new ArrayList<>();
        List<Point> normPts2 = new ArrayList<>();

        for (DMatch match : better_matches) {
            Point pt22 = data1.keypoints.toList().get(match.queryIdx).pt;
            points1.add(pt22);
            normPts1.add(pixelToCam(pt22, K));
            Point pt3 = data2.keypoints.toList().get(match.trainIdx).pt;
            points2.add(pt3);
            normPts2.add(pixelToCam(pt3, K));
        }

        MatOfPoint2f points1Mat = new MatOfPoint2f(points1.toArray(new Point[0]));
        MatOfPoint2f points2Mat = new MatOfPoint2f(points2.toArray(new Point[0]));


        Mat E = Calib3d.findEssentialMat(points1Mat, points2Mat, focal, principalPoint);
        Mat R = new Mat();
        Mat t = new Mat();
        Mat mask = new Mat();
        Calib3d.recoverPose(E, points1Mat, points2Mat, K, R, t, mask);

        poses.add(new CameraPose(Mat.eye(3, 3, CvType.CV_64F), Mat.zeros(3, 1, CvType.CV_64F)));
        poses.add(new CameraPose(R, t));

        Mat P1 = Mat.eye(3, 4, CvType.CV_64F);
        Mat P2 = new Mat(3, 4, CvType.CV_64F);
        R.copyTo(P2.colRange(0, 3));
        t.copyTo(P2.col(3));

        System.out.println(E.dump());


        MatOfPoint2f normPts1Mat = new MatOfPoint2f();
        normPts1Mat.fromList(normPts1);
        MatOfPoint2f normPts2Mat = new MatOfPoint2f();
        normPts2Mat.fromList(normPts2);

        Mat points4D = new Mat();
        Calib3d.triangulatePoints(P1, P2, normPts1Mat, normPts2Mat, points4D);

        for (int i = 0; i < points4D.cols(); i++) {
            double x = points4D.get(0, i)[0];
            double y = points4D.get(1, i)[0];
            double z = points4D.get(2, i)[0];
            double w = points4D.get(3, i)[0]; // гомогенная координата


            if (w != 0) {
                MapPoint mp = new MapPoint(new Vector3f((float) (x / w), (float) (y / w), (float) (z / w)));
                mp.imageIndices.add(0);
                mp.keypointIndices.add(goodMatches.get(i).queryIdx);
                mp.imageIndices.add(1);
                mp.keypointIndices.add(goodMatches.get(i).trainIdx);
                mapPoints.add(mp);
            }
        }

        return mapPoints;
    }

    public static CameraPose addViewWithPnP(
            FeatureData newData,
            List<MapPoint> mapPoints,
            List<FeatureData> allFeatures,
            List<CameraPose> poses,
            Mat K,
            int newImageIndex
    ) {
        List<Point3> objectPoints = new ArrayList<>();
        List<Point> imagePoints = new ArrayList<>();

        for (MapPoint mp : mapPoints) {
            for (int i = 0; i < mp.imageIndices.size(); i++) {
                int imgIdx = mp.imageIndices.get(i);
                int kpIdx = mp.keypointIndices.get(i);

                if (imgIdx >= allFeatures.size() || kpIdx >= allFeatures.get(imgIdx).descriptors.rows()) continue;

                DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
                Mat queryDesc = allFeatures.get(imgIdx).descriptors.row(kpIdx);
                List<MatOfDMatch> matches = new ArrayList<>();
                matcher.knnMatch(queryDesc, newData.descriptors, matches, 5);

                if (!matches.isEmpty() && matches.get(0).rows() >= 2) {
                    DMatch[] m = matches.get(0).toArray();
                    if (m[0].distance / m[1].distance < 0.9) {
                        int matchIdx = m[0].trainIdx;
                        objectPoints.add(new Point3(mp.point.x, mp.point.y, mp.point.z));
                        imagePoints.add(newData.keypoints.toList().get(matchIdx).pt);

                        mp.imageIndices.add(newImageIndex);
                        mp.keypointIndices.add(matchIdx);
                    }
                }
            }
        }

        if (objectPoints.size() < 6) {
            System.out.println("Недостаточно точек для PnP: " + objectPoints.size());
            return null;
        }

        Mat rvec = new Mat();
        Mat tvec = new Mat();

        MatOfPoint3f objMat = new MatOfPoint3f();
        List<org.opencv.core.Point3> convertedPoints = new ArrayList<>();
        for (Point3 pt : objectPoints) {
            convertedPoints.add(new org.opencv.core.Point3(pt.x, pt.y, pt.z));
        }
        objMat.fromList(convertedPoints);

        MatOfPoint2f imgMat = new MatOfPoint2f();
        imgMat.fromList(imagePoints);

        boolean ok = Calib3d.solvePnP(objMat, imgMat, K, new MatOfDouble(), rvec, tvec);

        if (!ok) {
            System.out.println("solvePnP не сработал");
            return null;
        }

        Mat R = new Mat();
        Calib3d.Rodrigues(rvec, R);

        CameraPose newPose = new CameraPose(R, tvec);

        // Добавим новые 3D-точки между новым изображением и предыдущим
        FeatureData prevData = allFeatures.get(newImageIndex - 1);
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);
        List<MatOfDMatch> matches = new ArrayList<>();
        matcher.knnMatch(prevData.descriptors, newData.descriptors, matches, 5);

        List<Point> pts1 = new ArrayList<>();
        List<Point> pts2 = new ArrayList<>();
        List<Integer> idx1 = new ArrayList<>();
        List<Integer> idx2 = new ArrayList<>();

        LinkedList<DMatch> goodMatches = new LinkedList<>();

        for (int i = 0; i < matches.size(); i++) {
            DMatch[] m = matches.get(i).toArray();
            if (m[0].distance / m[1].distance < 0.9) {
                goodMatches.add(m[0]);
                pts1.add(prevData.keypoints.toList().get(m[0].queryIdx).pt);
                pts2.add(newData.keypoints.toList().get(m[0].trainIdx).pt);
                idx1.add(m[0].queryIdx);
                idx2.add(m[0].trainIdx);
            }
        }

        Mat outputmask = new Mat();
        MatOfPoint2f pts1Mat = new MatOfPoint2f();
        pts1Mat.fromList(pts1);
        MatOfPoint2f pts2Mat = new MatOfPoint2f();
        pts2Mat.fromList(pts2);

        // Находим гомографию — здесь она используется только для фильтрации совпадений с помощью RANSAC
        Mat H = Calib3d.findHomography(pts1Mat, pts2Mat, Calib3d.RANSAC, 50, outputmask, 2000, 0.995);

        // outputMask содержит нули и единицы, указывающие, какие совпадения будут отфильтрованы
        LinkedList<DMatch> better_matches = new LinkedList<DMatch>();
        for (int i = 0; i < goodMatches.size(); i++) {
            if (outputmask.get(i, 0)[0] != 0.0) {
                better_matches.add(goodMatches.get(i));
            }
        }

        List<Point> normPts1 = new ArrayList<>();
        List<Point> normPts2 = new ArrayList<>();

        for (DMatch match : better_matches) {
            Point pt22 = prevData.keypoints.toList().get(match.queryIdx).pt;
            normPts1.add(pixelToCam(pt22, K));
            Point pt3 = newData.keypoints.toList().get(match.trainIdx).pt;
            normPts2.add(pixelToCam(pt3, K));
        }

        Mat P1 = new Mat(3, 4, CvType.CV_64F);
        Mat P2 = new Mat(3, 4, CvType.CV_64F);

        CameraPose prevPose = poses.get(newImageIndex - 1);
        Mat Rt1 = new Mat(3, 4, CvType.CV_64F);
        Mat Rt2 = new Mat(3, 4, CvType.CV_64F);
        prevPose.R.copyTo(Rt1.colRange(0, 3));
        prevPose.t.copyTo(Rt1.col(3));
        newPose.R.copyTo(Rt2.colRange(0, 3));
        newPose.t.copyTo(Rt2.col(3));

        Core.gemm(K, Rt1, 1.0, new Mat(), 0.0, P1);
        Core.gemm(K, Rt2, 1.0, new Mat(), 0.0, P2);


        MatOfPoint2f norm1 = new MatOfPoint2f();
        norm1.fromList(normPts1);
        MatOfPoint2f norm2 = new MatOfPoint2f();
        norm2.fromList(normPts2);

        Mat points4D = new Mat();
        Calib3d.triangulatePoints(P1, P2, norm1, norm2, points4D);

        for (int i = 0; i < points4D.cols(); i++) {
            double x = points4D.get(0, i)[0];
            double y = points4D.get(1, i)[0];
            double z = points4D.get(2, i)[0];
            double w = points4D.get(3, i)[0]; // гомогенная координата
            if (w != 0) {
                MapPoint mp = new MapPoint(new Vector3f((float) (x / w), (float) (y / w), (float) (z / w)));
                mp.imageIndices.add(newImageIndex - 1);
                mp.keypointIndices.add(idx1.get(i));
                mp.imageIndices.add(newImageIndex);
                mp.keypointIndices.add(idx2.get(i));
                mapPoints.add(mp);
            }
        }

        return newPose;
    }

    public static void main(String[] args) {

        List<Mat> images = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            images.add(Imgcodecs.imread("cub2/cube" + i + ".jpg", Imgcodecs.IMREAD_GRAYSCALE));
        }

        double focal = 1170;
        Point principalPoint = new Point(638.3097505828375, 468.564541032492);
        Mat K = Mat.eye(3, 3, CvType.CV_64F);
        K.put(0, 0, focal);
        K.put(1, 1, focal);
        K.put(0, 2, principalPoint.x);
        K.put(1, 2, principalPoint.y);

        SIFT sift = SIFT.create(10000);
        List<FeatureData> features = new ArrayList<>();

        for (Mat img : images) {
            MatOfKeyPoint keypoints = new MatOfKeyPoint();
            Mat descriptors = new Mat();
            sift.detectAndCompute(img, new Mat(), keypoints, descriptors);
            features.add(new FeatureData(img, keypoints, descriptors));
        }

        List<CameraPose> poses = new ArrayList<>();
        List<MapPoint> mapPoints = initializeReconstruction(features.get(0), features.get(1), K, focal, principalPoint, poses);

        for (int i = 2; i < features.size(); i++) {
            CameraPose pose = addViewWithPnP(features.get(i), mapPoints, features.subList(0, i), poses, K, i);
            if (pose != null) {
                poses.add(pose);
                System.out.println("Добавлена камера " + i);
            }
        }

        System.out.println("Всего камер: " + poses.size());
        System.out.println("Всего 3D точек: " + mapPoints.size());

        // Запуск OpenGL визуализации
        MultiReconstruction renderer = new MultiReconstruction();
        renderer.initOpenGL();
        List<Vector3f> points = new ArrayList<>();
        for (MapPoint m : mapPoints) {
            points.add(m.point);
        }
        List<Vector3f>cleanedPoints = removeNoise(points, 0.01f);
        renderer.loop(cleanedPoints);
        renderer.cleanUp();

        // Сохраняем облако точек в STL через Convex Hull
        Point3d[] pointsArray = new Point3d[mapPoints.size()];
        for (int i = 0; i < mapPoints.size(); i++) {
            Vector3f p = mapPoints.get(i).point;
            pointsArray[i] = new Point3d(p.x, p.y, p.z);
        }

        QuickHull3D hull = new QuickHull3D();
        hull.build(pointsArray);
        int[][] faceIndices = hull.getFaces();

        // Генерация STL
        StringBuilder stl = new StringBuilder();
        stl.append("solid pointcloud\n");

        for (int[] face : faceIndices) {
            Point3d p1 = pointsArray[face[0]];
            Point3d p2 = pointsArray[face[1]];
            Point3d p3 = pointsArray[face[2]];

            // Вычисляем нормаль
            Vector3d v1 = new Vector3d(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z);
            Vector3d v2 = new Vector3d(p3.x - p1.x, p3.y - p1.y, p3.z - p1.z);
            Vector3d normal = cross(v1, v2);
            normalize(normal);

            stl.append("facet normal ").append(normal.x).append(" ").append(normal.y).append(" ").append(normal.z).append("\n");
            stl.append("  outer loop\n");
            stl.append("    vertex ").append(p1.x).append(" ").append(p1.y).append(" ").append(p1.z).append("\n");
            stl.append("    vertex ").append(p2.x).append(" ").append(p2.y).append(" ").append(p2.z).append("\n");
            stl.append("    vertex ").append(p3.x).append(" ").append(p3.y).append(" ").append(p3.z).append("\n");
            stl.append("  endloop\n");
            stl.append("endfacet\n");
        }
        stl.append("endsolid pointcloud");

        try (PrintWriter out = new PrintWriter("mesh_output.stl")) {
            out.print(stl.toString());
            System.out.println("STL mesh сохранён в mesh_output.stl");
        } catch (
                IOException e) {
            System.err.println("Ошибка записи STL: " + e.getMessage());
        }
    }
    public static Vector3d cross(Vector3d a, Vector3d b) {
        return new Vector3d(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
        );
    }

    public static void normalize(Vector3d v) {
        double len = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        if (len > 1e-8) {
            v.x /= len;
            v.y /= len;
            v.z /= len;
        }
    }
    public static List<Vector3f> removeNoise(List<Vector3f> pointCloud, float threshold) {
        List<Vector3f> cleaned = new ArrayList<>();
        float sqThreshold = threshold * threshold; // Квадрат порога

        // 1. Пространственное разбиение на ячейки для оптимизации
        Map<String, List<Vector3f>> grid = new HashMap<>();
        for (Vector3f p : pointCloud) {
            String key = (int)(p.x / threshold) + "_" +
                    (int)(p.y / threshold) + "_" +
                    (int)(p.z / threshold);
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        // 2. Проверка соседних ячеек для каждой точки
        for (Vector3f p : pointCloud) {
            boolean hasNeighbor = false;

            // Проверяем соседние ячейки (включая текущую)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        // Формируем ключ соседней ячейки
                        String key = (int)(p.x / threshold + dx) + "_" +
                                (int)(p.y / threshold + dy) + "_" +
                                (int)(p.z / threshold + dz);

                        if (grid.containsKey(key)) {
                            for (Vector3f neighbor : grid.get(key)) {
                                // Пропускаем сравнение точки с самой собой
                                if (p == neighbor) continue;

                                // Ручной расчёт квадрата расстояния
                                float dx2 = p.x - neighbor.x;
                                float dy2 = p.y - neighbor.y;
                                float dz2 = p.z - neighbor.z;
                                float distSq = dx2*dx2 + dy2*dy2 + dz2*dz2;

                                if (distSq < sqThreshold) {
                                    hasNeighbor = true;
                                    break;
                                }
                            }
                        }
                        if (hasNeighbor) break; // Выходим раньше
                    }
                    if (hasNeighbor) break;
                }
                if (hasNeighbor) break;
            }

            // Если соседей нет - сохраняем точку
            if (!hasNeighbor) {
                cleaned.add(p);
            }
        }

        return cleaned;
    }
}

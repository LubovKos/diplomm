package org.example;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

public class ChessboardCalibration {
    // Размеры шахматной доски: число внутренних углов (число квадратов - 1)
    private static final int CHECKERBOARD_COLS = 9;  // 10 квадратов = 9 углов
    private static final int CHECKERBOARD_ROWS = 7;  // 7 квадратов = 6 углов

    public static void main(String[] args) {
        // Загрузка нативной библиотеки OpenCV
        System.load("C:\\opencv\\build\\java\\x64\\opencv_java4110.dll");

        List<Mat> objPoints = new ArrayList<>();
        List<Mat> imgPoints = new ArrayList<>();

        // Подготовка объектных точек (0,0,0), (1,0,0), ..., (8,5,0)
        MatOfPoint3f objp = new MatOfPoint3f();
        List<Point3> objpList = new ArrayList<>();
        for (int i = 0; i < CHECKERBOARD_ROWS; i++) {
            for (int j = 0; j < CHECKERBOARD_COLS; j++) {
                objpList.add(new Point3(j, i, 0));
            }
        }
        objp.fromList(objpList);
        System.out.println(objpList);

        // Загрузка изображений
        String folder = "./chess/";
        File dir = new File(folder);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg"));

        if (files == null || files.length == 0) {
            System.out.println("Изображения не найдены в папке: " + dir.getAbsolutePath());
            return;
        }
        System.out.println("Найдено изображений: " + files.length);

        // Основные параметры
        Size boardSize = new Size(CHECKERBOARD_COLS, CHECKERBOARD_ROWS);
        int successCount = 0;
        Size imageSize = null;

        for (File file : files) {
            Mat frame = Imgcodecs.imread(file.getAbsolutePath());
            if (frame.empty()) {
                System.out.println("Ошибка загрузки: " + file.getName());
                continue;
            }

            Mat gray = new Mat();
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);

            // Поиск углов с улучшенными параметрами
            MatOfPoint2f corners = new MatOfPoint2f();
            int flags = Calib3d.CALIB_CB_ADAPTIVE_THRESH
                    | Calib3d.CALIB_CB_NORMALIZE_IMAGE;

            boolean found = Calib3d.findChessboardCorners(gray, boardSize, corners, flags);

            if (found) {
                // Уточнение координат углов
                TermCriteria criteria = new TermCriteria(
                        TermCriteria.EPS + TermCriteria.MAX_ITER,
                        30,
                        0.001
                );
                Imgproc.cornerSubPix(gray, corners, new Size(11, 11), new Size(-1, -1), criteria);

                // Сохранение точек
                objPoints.add(objp);
                imgPoints.add(corners);
                successCount++;

                // Визуализация
                Calib3d.drawChessboardCorners(frame, boardSize, corners, found);
                System.out.println("Успешно: " + file.getName());
            } else {
                System.out.println("Не найдены углы на: " + file.getName());
            }

            // Сохранение результата
            String outputPath = folder + "drawn_" + file.getName();
            Imgcodecs.imwrite(outputPath, frame);

            // Сохранение размера первого изображения
            if (imageSize == null) {
                imageSize = frame.size();
            }
        }

        // Проверка количества успешных изображений
        System.out.println("\nУспешно обработано изображений: " + successCount);
        if (successCount < 10) {
            System.out.println("Рекомендуется использовать минимум 10 изображений");
        }

        if (successCount > 0) {
            // Калибровка камеры
            Mat cameraMatrix = Mat.eye(3, 3, CvType.CV_64F);
            Mat distCoeffs = Mat.zeros(8, 1, CvType.CV_64F);
            List<Mat> rvecs = new ArrayList<>();
            List<Mat> tvecs = new ArrayList<>();

            double rms = Calib3d.calibrateCamera(
                    objPoints,
                    imgPoints,
                    imageSize,
                    cameraMatrix,
                    distCoeffs,
                    rvecs,
                    tvecs
            );

            System.out.println("\nКалибровка успешна!");
            System.out.println("RMS ошибка: " + rms);
            System.out.println("Матрица камеры:\n" + cameraMatrix.dump());
            System.out.println("Коэффициенты искажения:\n" + distCoeffs.dump());
        } else {
            System.out.println("Не найдено подходящих изображений для калибровки");
        }
    }
}
package org.example;

import org.opencv.core.*;
import org.opencv.features2d.SIFT;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.highgui.HighGui;
import java.util.Random;
import java.awt.event.*;
import javax.swing.*;

public class Sift {

    static { System.load("C:\\opencv\\build\\java\\x64\\opencv_java4110.dll"); }

    public static void main(String[] args) {
        // Загрузка изображения (в цвете)
        String imagePath = "image1.jpg";
        Mat imageColor = Imgcodecs.imread(imagePath, Imgcodecs.IMREAD_COLOR);

        if (imageColor.empty()) {
            System.out.println("Не удалось загрузить изображение!");
            return;
        }

        // Конвертация в оттенки серого
        Mat imageGray = new Mat();
        Imgproc.cvtColor(imageColor, imageGray, Imgproc.COLOR_BGR2GRAY);

        // Повышение контрастности (CLAHE)
        Mat imageEnhanced = new Mat();
        Imgproc.createCLAHE(3.0, new Size(8, 8)).apply(imageGray, imageEnhanced);

        // Повышение резкости
        Mat imageSharpened = new Mat();
        Imgproc.GaussianBlur(imageEnhanced, imageSharpened, new Size(0, 0), 3);
        Core.addWeighted(imageEnhanced, 1.5, imageSharpened, -0.5, 0, imageSharpened);

        // Создание объекта SIFT (увеличиваем число ключевых точек)
        SIFT sift = SIFT.create(100);

        // Обнаружение ключевых точек и вычисление дескрипторов
        MatOfKeyPoint keyPoints = new MatOfKeyPoint();
        Mat descriptors = new Mat();
        sift.detectAndCompute(imageSharpened, new Mat(), keyPoints, descriptors);

        // Создание выходного изображения
        Mat outputImage = imageColor.clone();
        Random rand = new Random();

        for (KeyPoint kp : keyPoints.toArray()) {
            Scalar color = new Scalar(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256)); // Случайный цвет
            Point center = new Point(kp.pt.x, kp.pt.y);
            int radius = (int) (kp.size / 2);
            double angle = Math.toRadians(kp.angle);
            Point direction = new Point(
                    center.x + radius * Math.cos(angle),
                    center.y + radius * Math.sin(angle)
            );

            // Рисуем окружность и направление дескриптора с антиалиасингом и меньшей толщиной

            // Размер ключевой точки (kp.size) определяется как масштаб, на котором эта точка была обнаружена.
            // Он влияет на то, насколько большой считается эта особенность
            Imgproc.circle(outputImage, center, radius, color, 1, Imgproc.LINE_AA);

            // стрелки, указывающие направление градиента вокруг ключевой точки
            Imgproc.line(outputImage, center, direction, color, 1, Imgproc.LINE_AA);
        }

        // Сохранение результата
        Imgcodecs.imwrite("sift_output.jpg", outputImage);

        // Создание окна с возможностью масштабирования
        JFrame frame = new JFrame("SIFT Keypoints with Descriptors");
        JLabel label = new JLabel(new ImageIcon(HighGui.toBufferedImage(outputImage)));
        JScrollPane scrollPane = new JScrollPane(label);
        frame.add(scrollPane);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
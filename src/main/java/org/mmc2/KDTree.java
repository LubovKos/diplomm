package org.mmc2;

import java.util.List;
public class KDTree {
    private Node root;
    private List<Point3D> points;

    private static class Node {
        Point3D point;
        Node left, right;
        int axis; // 0 (x), 1 (y), 2 (z)

        Node(Point3D p, int axis) {
            this.point = p;
            this.axis = axis;
        }
    }

    public KDTree(List<Point3D> points) {
        this.points = points;
        root = buildTree(points, 0);
    }

    private Node buildTree(List<Point3D> points, int depth) {
        if (points.isEmpty()) return null;
        int axis = depth % 3;
        points.sort((a, b) -> Double.compare(getCoord(a, axis), getCoord(b, axis)));
        int median = points.size() / 2;
        Node node = new Node(points.get(median), axis);
        node.left = buildTree(points.subList(0, median), depth + 1);
        node.right = buildTree(points.subList(median + 1, points.size()), depth + 1);
        return node;
    }

    private double getCoord(Point3D p, int axis) {
        return (axis == 0) ? p.x : (axis == 1) ? p.y : p.z;
    }

    // Поиск ближайшей точки (упрощённый, без оптимизаций)
    public Point3D findNearest(Point3D target) {
        return findNearest(root, target, null);
    }

    private Point3D findNearest(Node node, Point3D target, Point3D best) {
        if (node == null) return best;
        double d = node.point.distanceTo(target);
        if (best == null || d < best.distanceTo(target)) best = node.point;
        int axis = node.axis;
        double diff = getCoord(target, axis) - getCoord(node.point, axis);
        Node first = diff < 0 ? node.left : node.right;
        Node second = diff < 0 ? node.right : node.left;
        best = findNearest(first, target, best);
        if (Math.abs(diff) < best.distanceTo(target)) {
            best = findNearest(second, target, best);
        }
        return best;
    }
}

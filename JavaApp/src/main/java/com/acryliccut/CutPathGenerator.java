package com.acryliccut;

import javafx.scene.shape.*;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * PNG → SVG カットパス生成コア。
 * Python 版 (generate_cutpath.py) に準拠したアルゴリズム:
 *   1. アルファチャンネル (または輝度) を二値化
 *   2. 楕円カーネルで膨張 (オフセット量)
 *   3. 外周輪郭を検出 (cv2.findContours)
 *   4. SVG を生成 (PNG base64 埋め込み + 赤カットパス)
 *
 * ネイティブ OpenCV は org.openpnp:opencv の nu.pattern.OpenCV.loadLocally() で自動ロード。
 */
public class CutPathGenerator {

    static {
        OpenCV.loadLocally();
    }

    // ──────────────────────────────────────────────
    //  メイン生成メソッド
    // ──────────────────────────────────────────────

    public static GeneratedResult generate(String inputPng, int offset, boolean smooth)
            throws IOException {

        Mat image = Imgcodecs.imread(inputPng, Imgcodecs.IMREAD_UNCHANGED);
        if (image.empty()) {
            throw new IOException("画像を読み込めませんでした: " + inputPng);
        }

        int width  = image.cols();
        int height = image.rows();

        // ── 1. 二値マスク生成 ──
        Mat binary = new Mat();
        if (image.channels() == 4) {
            // BGRA: アルファチャンネルを使用
            List<Mat> channels = new ArrayList<>();
            Core.split(image, channels);
            Imgproc.threshold(channels.get(3), binary, 1, 255, Imgproc.THRESH_BINARY);
            channels.forEach(Mat::release);
        } else {
            Mat gray = new Mat();
            if (image.channels() == 1) {
                image.copyTo(gray);
            } else {
                Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            }
            Imgproc.threshold(gray, binary, 1, 255, Imgproc.THRESH_BINARY);
            gray.release();
        }
        image.release();

        // ── 2. オフセット分パディング → 楕円カーネルで膨張 ──
        // パディングにより境界近くの輪郭がクリップされず画像サイズを超えて拡張される
        Mat padded = new Mat();
        Core.copyMakeBorder(binary, padded, offset, offset, offset, offset,
                Core.BORDER_CONSTANT, new Scalar(0));
        binary.release();
        int  kernelSize = offset * 2 + 1;
        Mat  kernel     = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(kernelSize, kernelSize));
        Mat  dilated    = new Mat();
        Imgproc.dilate(padded, dilated, kernel);
        padded.release();
        kernel.release();

        // ── 3. 輪郭検出 ──
        // smooth=true  → CHAIN_APPROX_NONE (密な点群でベジェがなめらか)
        // smooth=false → CHAIN_APPROX_SIMPLE (簡略化ポリライン)
        List<MatOfPoint> rawContours = new ArrayList<>();
        Mat hierarchy = new Mat();
        int approxMode = smooth
                ? Imgproc.CHAIN_APPROX_NONE
                : Imgproc.CHAIN_APPROX_SIMPLE;
        Imgproc.findContours(dilated, rawContours, hierarchy,
                Imgproc.RETR_EXTERNAL, approxMode);
        dilated.release();
        hierarchy.release();

        // MatOfPoint → double[][][] に変換
        double[][][] contours = rawContours.stream()
                .filter(c -> c.total() >= 3)
                .map(c -> {
                    Point[] pts = c.toArray();
                    double[][] arr = new double[pts.length][2];
                    for (int i = 0; i < pts.length; i++) {
                        arr[i][0] = pts[i].x;
                        arr[i][1] = pts[i].y;
                    }
                    return arr;
                })
                .toArray(double[][][]::new);
        rawContours.forEach(Mat::release);

        // ── 4. SVG 生成 ──
        int svgWidth  = width  + 2 * offset;
        int svgHeight = height + 2 * offset;
        String svg = buildSvg(inputPng, svgWidth, svgHeight, offset, contours, smooth);

        return new GeneratedResult(svgWidth, svgHeight, offset, offset, contours, svg, inputPng);
    }

    // ──────────────────────────────────────────────
    //  SVG ビルダー
    // ──────────────────────────────────────────────

    private static String buildSvg(String inputPng, int width, int height, int imageX,
                                   double[][][] contours, boolean smooth)
            throws IOException {
        byte[] pngBytes = Files.readAllBytes(new File(inputPng).toPath());
        String base64   = Base64.getEncoder().encodeToString(pngBytes);
        int imgW = width  - 2 * imageX;
        int imgH = height - 2 * imageX;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                "width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">\n",
                width, height, width, height));
        sb.append(String.format(
                "  <image href=\"data:image/png;base64,%s\" " +
                "x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\"/>\n",
                base64, imageX, imageX, imgW, imgH));

        for (double[][] cnt : contours) {
            String d = smooth ? bezierPathData(cnt) : linePathData(cnt);
            sb.append(String.format(
                    "  <path d=\"%s\" fill=\"none\" stroke=\"red\" stroke-width=\"1\"/>\n", d));
        }

        sb.append("</svg>\n");
        return sb.toString();
    }

    /** 直線ポリライン SVG パスデータ */
    private static String linePathData(double[][] pts) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("M %d,%d", (int) pts[0][0], (int) pts[0][1]));
        for (int i = 1; i < pts.length; i++) {
            sb.append(String.format(" L %d,%d", (int) pts[i][0], (int) pts[i][1]));
        }
        sb.append(" Z");
        return sb.toString();
    }

    /**
     * 3 点おきに三次ベジェ曲線を生成。
     * Python 版 contour_to_bezier_path と同等:
     * 連続する 4 点を P0(アンカー), P1(制御), P2(制御), P3(アンカー) として使用。
     */
    private static String bezierPathData(double[][] pts) {
        int n = pts.length;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("M %d,%d", (int) pts[0][0], (int) pts[0][1]));
        for (int i = 0; i < n; i += 3) {
            double[] p1 = pts[(i + 1) % n];
            double[] p2 = pts[(i + 2) % n];
            double[] p3 = pts[(i + 3) % n];
            sb.append(String.format(" C %d,%d %d,%d %d,%d",
                    (int) p1[0], (int) p1[1],
                    (int) p2[0], (int) p2[1],
                    (int) p3[0], (int) p3[1]));
        }
        sb.append(" Z");
        return sb.toString();
    }

    // ──────────────────────────────────────────────
    //  JavaFX プレビュー用 Path 生成
    // ──────────────────────────────────────────────

    /**
     * プレビュー用の JavaFX {@link Path} を生成する。
     * 輪郭点群をベジェ曲線またはポリラインとして描画する。
     */
    public static Path createJavaFXPath(double[][][] contours, boolean smooth) {
        Path path = new Path();
        path.setFill(null);
        path.setStroke(javafx.scene.paint.Color.RED);
        path.setStrokeWidth(1.5);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);

        for (double[][] pts : contours) {
            if (pts.length < 3) continue;

            path.getElements().add(new MoveTo(pts[0][0], pts[0][1]));

            if (smooth) {
                int n = pts.length;
                for (int i = 0; i < n; i += 3) {
                    double[] p1 = pts[(i + 1) % n];
                    double[] p2 = pts[(i + 2) % n];
                    double[] p3 = pts[(i + 3) % n];
                    path.getElements().add(
                            new CubicCurveTo(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1]));
                }
            } else {
                for (int i = 1; i < pts.length; i++) {
                    path.getElements().add(new LineTo(pts[i][0], pts[i][1]));
                }
            }

            path.getElements().add(new ClosePath());
        }

        return path;
    }
}

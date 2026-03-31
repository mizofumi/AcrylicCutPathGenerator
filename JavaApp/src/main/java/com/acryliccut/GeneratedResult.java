package com.acryliccut;

/**
 * カットパス生成結果を保持するデータクラス。
 * contours[i][j][0] = X 座標, contours[i][j][1] = Y 座標 (ピクセル)
 */
public class GeneratedResult {

    public final int width;
    public final int height;
    /** SVGキャンバス内でのPNG画像のX/Y開始位置（= offset量） */
    public final int imageX;
    public final int imageY;
    /** 輪郭点群: contours[輪郭index][点index][0=X/1=Y] */
    public final double[][][] contours;
    public final String svgContent;
    public final String inputPngPath;

    public GeneratedResult(int width, int height, int imageX, int imageY,
                           double[][][] contours,
                           String svgContent,
                           String inputPngPath) {
        this.width        = width;
        this.height       = height;
        this.imageX       = imageX;
        this.imageY       = imageY;
        this.contours     = contours;
        this.svgContent   = svgContent;
        this.inputPngPath = inputPngPath;
    }
}

package com.acryliccut;

/**
 * fat JAR 実行用のエントリポイント。
 *
 * Main-Class が JavaFX Application 継承クラスだと、
 * `java -jar` 実行時に JVM 側の JavaFX ランタイム検査で
 * 先に終了してしまうことがあるため、薄いランチャーを挟む。
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}

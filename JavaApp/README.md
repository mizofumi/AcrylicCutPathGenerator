# AcrylicCutPathGenerator — Java (JavaFX) クロスプラットフォーム版

Python 版 (`generate_cutpath.py`) を **JavaFX + OpenCV Java** で再実装。  
Windows / macOS / Linux で動作します。

## 機能

| 機能 | 内容 |
|------|------|
| PNG 入力 | ファイルダイアログで選択 |
| SVG 出力 | PNG を base64 埋め込み ＋ 赤カットパス |
| オフセット調整 | 1〜100 px スライダー（デフォルト 10 px） |
| スムージング | ベジェ曲線 / 直線ポリライン 切り替え |
| ライブプレビュー | JavaFX ネイティブ PathGeometry レンダリング |
| ズーム | マウスホイール（マウス位置を中心に拡大縮小） |
| パン | 左ボタンドラッグで移動 |
| 全体表示 | ボタン一発でウィンドウにフィット |

## アルゴリズム（Python 版と同等）

1. PNG のアルファチャンネル（または輝度）を二値化
2. 楕円カーネルでオフセット量だけ膨張（`Imgproc.dilate`）
3. 外周輪郭を検出（`Imgproc.findContours`）
4. スムーズモード: 3 点おきに三次ベジェ曲線へ変換
5. SVG を生成（PNG base64 埋め込み ＋ `<path stroke="red">`）

## 前提条件

- **JDK 21** 以上  
  → https://adoptium.net/ (Eclipse Temurin 推奨)
- **Gradle 8.5+** （または Gradle Wrapper を使用すれば不要）  
  → https://gradle.org/install/

## ビルド・実行

```bash
cd JavaApp

# 依存関係を解決して起動
./gradlew run          # macOS / Linux
gradlew.bat run        # Windows
```

初回実行時に Maven セントラルから JavaFX・OpenCV のバイナリを自動ダウンロードします。

## fat JAR の作成

Windows / macOS / Linux 向けの JavaFX ネイティブライブラリと OpenCV をまとめた
単一 JAR を生成できます。

```bash
cd JavaApp

./gradlew fatJar       # macOS / Linux
gradlew.bat fatJar     # Windows
```

生成物:

```text
build/libs/acrylic-cut-path-generator-1.0.0-all.jar
```

実行例:

```bash
java -jar build/libs/acrylic-cut-path-generator-1.0.0-all.jar
```

`fatJar` は `shadowJar` を使って依存ライブラリを 1 つにまとめており、
JavaFX は Windows / Linux / macOS(Intel / Apple Silicon) 向けネイティブを同梱します。
そのため、実行環境には Java 21 以上だけあれば動作します。
JAR の起動エントリポイントは専用 `Launcher` にしてあり、
`java -jar` でも JavaFX のランタイム不足エラーを回避できるようにしています。

> Gradle Wrapper (gradlew) を使う場合は事前に `gradle wrapper` を実行して
> `gradlew` / `gradlew.bat` を生成してください（Gradle 本体のインストール不要になります）。

```bash
# Gradle Wrapper の生成（一度だけ実行）
gradle wrapper
```

## 配布（実行可能パッケージ）

`./gradlew fatJar` で単体配布向け JAR を作れます。  
さらに OS ごとのインストーラーまで作りたい場合は、JDK 同梱の `jpackage` も使えます。

```bash
# 1. ビルド
./gradlew build

# 2. jpackage でインストーラーを生成 (Windows の場合 .exe / .msi)
jpackage \
  --input build/libs \
  --main-jar acrylic-cut-path-generator-1.0.0.jar \
  --main-class com.acryliccut.App \
  --name "AcrylicCutPathGenerator" \
  --type app-image
```

## プロジェクト構成

```
JavaApp/
├── build.gradle
├── settings.gradle
└── src/main/java/com/acryliccut/
    ├── App.java                  JavaFX Application + UI 全体
    ├── CutPathGenerator.java     コアアルゴリズム（画像処理 + SVG 生成）
    └── GeneratedResult.java      生成結果データクラス
```

## 主な依存ライブラリ

| ライブラリ | 用途 |
|-----------|------|
| `org.openjfx:javafx-controls` | GUI フレームワーク |
| `org.openpnp:opencv` | OpenCV Java バインディング（全プラットフォームのネイティブ DLL/dylib/so を同梱） |

### OpenCV ネイティブライブラリのロード

`org.openpnp:opencv` は Windows・macOS・Linux 向けのネイティブバイナリを JAR に同梱しており、
`nu.pattern.OpenCV.loadLocally()` を呼ぶことで実行時に自動抽出・ロードします。
追加インストール不要でクロスプラットフォーム対応します。

## 操作方法

1. **「参照...」** ボタンで PNG ファイルを選択
2. **オフセットスライダー** で余白量を調整
3. **スムージングチェックボックス** でベジェ曲線 / ポリラインを選択
4. **「プレビューを生成」** をクリック
5. プレビューを確認（ホイールズーム・ドラッグパン対応）
6. **「SVG ファイルを保存...」** で出力

## 注意事項

- 非常に大きい画像（4000px 以上）でスムーズモードを使用すると、
  輪郭点数が多くなり処理に時間がかかる場合があります。
  その場合はスムージングをオフにするか、オフセット値を大きくすると
  輪郭が単純化されて高速になります。

package com.acryliccut;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * アクリルキーホルダー カットパス生成ツール — JavaFX GUI
 *
 * レイアウト:
 *   左パネル : 入力/設定コントロール
 *   右パネル : プレビュー (ズーム・パン対応)
 *   下部     : ステータスバー
 *
 * ズーム・パンの仕組み:
 *   contentGroup に [Scale(pivot=0,0), Translate] の 2 つの Transform を適用。
 *   JavaFX は getTransforms() のインデックス 0 から順に適用するため、
 *   screen = Translate( Scale(local) ) となる。
 *   ホイール時の平行移動量補正式:
 *     tx_new = mouseX - ratio * (mouseX - tx_old)  [ratio = scale_new / scale_old]
 */
public class App extends Application {

    // ── 定数 ──────────────────────────────────────
    private static final double ZOOM_STEP = 1.15;
    private static final double MIN_ZOOM  = 0.04;
    private static final double MAX_ZOOM  = 30.0;

    // ── Stage ──────────────────────────────────────
    private Stage primaryStage;

    // ── 左パネル コントロール ────────────────────
    private TextField inputPathField;
    private Slider    offsetSlider;
    private Label     offsetValueLabel;
    private CheckBox  smoothCheckBox;
    private Button    generateButton;
    private Button    saveButton;

    // ── ステータスバー ───────────────────────────
    private Label statusLabel;
    private Label zoomLabel;

    // ── プレビュー ───────────────────────────────
    private Pane       previewPane;
    private VBox       placeholder;
    private StackPane  processingOverlay;
    private Group      contentGroup;
    private ImageView  previewImageView;
    private Path       cutPathShape;

    // ── ズーム・パン 状態 ────────────────────────
    private final Scale     scaleXform     = new Scale(1.0, 1.0, 0, 0);
    private final Translate translateXform = new Translate(0, 0);
    private double  currentScale = 1.0;
    private double  txVal = 0, tyVal = 0;
    private boolean isPanning  = false;
    private double  dragStartX, dragStartY;
    private double  startTx,   startTy;

    // ── 生成結果 ─────────────────────────────────
    private GeneratedResult currentResult;

    // ──────────────────────────────────────────────
    //  Application lifecycle
    // ──────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("アクリルキーホルダー カットパス生成ツール");
        stage.setMinWidth(860);
        stage.setMinHeight(540);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F3F3F3;");
        root.setCenter(buildSplitPane());
        root.setBottom(buildStatusBar());

        stage.setScene(new Scene(root, 1140, 720));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ──────────────────────────────────────────────
    //  レイアウト構築
    // ──────────────────────────────────────────────

    private Node buildSplitPane() {
        SplitPane sp = new SplitPane();
        sp.getItems().addAll(buildControlPanel(), buildPreviewPanel());
        sp.setDividerPositions(0.265);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return sp;
    }

    // ────── 左パネル ──────────────────────────────

    private Node buildControlPanel() {
        VBox outer = new VBox();
        outer.setStyle("-fx-background-color: white; " +
                       "-fx-border-color: #E1E1E1; -fx-border-width: 0 1 0 0;");
        outer.setMinWidth(220);
        outer.setMaxWidth(420);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox content = new VBox();
        content.setPadding(new Insets(16));

        // ── タイトル ──
        content.getChildren().addAll(
            styledLabel("アクリルキーホルダー",
                        "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0078D4;"),
            withMargin(styledLabel("カットパス生成ツール",
                                   "-fx-font-size: 12px; -fx-text-fill: #666666;"),
                       2, 0, 18, 0)
        );

        // ── 入力ファイル ──
        content.getChildren().add(sectionLabel("入力 PNG ファイル"));

        inputPathField = new TextField("(選択されていません)");
        inputPathField.setEditable(false);
        inputPathField.setStyle("-fx-background-color: #F8F8F8; " +
                                "-fx-border-color: #CCCCCC; -fx-text-fill: #888888;");
        inputPathField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(inputPathField, Priority.ALWAYS);

        Button browseBtn = secondaryButton("参照...");
        browseBtn.setOnAction(e -> onBrowse());

        HBox fileRow = new HBox(6, inputPathField, browseBtn);
        content.getChildren().add(fileRow);
        content.getChildren().add(hSeparator(18, 18));

        // ── カットパス設定 ──
        content.getChildren().add(sectionLabel("カットパス設定"));

        offsetSlider     = new Slider(1, 100, 10);
        offsetValueLabel = new Label("10 px");
        offsetValueLabel.setStyle("-fx-text-fill: #555555; -fx-font-weight: bold;");
        offsetValueLabel.setPrefWidth(46);
        offsetValueLabel.setAlignment(Pos.CENTER_RIGHT);
        offsetSlider.setBlockIncrement(1);
        offsetSlider.setSnapToTicks(true);
        offsetSlider.setMajorTickUnit(1);
        HBox.setHgrow(offsetSlider, Priority.ALWAYS);
        offsetSlider.valueProperty().addListener(
            (obs, o, n) -> offsetValueLabel.setText((int) n.doubleValue() + " px"));

        Label offsetLabelText = new Label("オフセット:");
        offsetLabelText.setMinWidth(80);
        HBox offsetRow = new HBox(6, offsetLabelText, offsetSlider, offsetValueLabel);
        offsetRow.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(withMargin(offsetRow, 0, 0, 4, 0));
        content.getChildren().add(withMargin(
            styledLabel("輪郭から外側に広げる量（ピクセル）",
                        "-fx-text-fill: #888888; -fx-font-size: 11px;"),
            0, 0, 10, 0));

        smoothCheckBox = new CheckBox("スムージング（ベジェ曲線）");
        smoothCheckBox.setSelected(true);
        content.getChildren().add(withMargin(smoothCheckBox, 0, 0, 4, 0));
        content.getChildren().add(withMargin(
            styledLabel("チェックOFF: 直線ポリライン",
                        "-fx-text-fill: #888888; -fx-font-size: 11px; -fx-padding: 0 0 0 20;"),
            0, 0, 0, 0));
        content.getChildren().add(hSeparator(18, 18));

        // ── 生成ボタン ──
        generateButton = primaryButton("プレビューを生成");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setStyle(generateButton.getStyle() + " -fx-font-size: 14px;");
        generateButton.setDisable(true);
        generateButton.setOnAction(e -> onGenerate());
        content.getChildren().add(generateButton);
        content.getChildren().add(hSeparator(18, 18));

        // ── SVG 保存 ──
        content.getChildren().add(sectionLabel("エクスポート"));
        saveButton = secondaryButton("SVG ファイルを保存...");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setDisable(true);
        saveButton.setOnAction(e -> onSave());
        content.getChildren().add(saveButton);
        content.getChildren().add(hSeparator(18, 18));

        // ── 操作ガイド ──
        content.getChildren().add(sectionLabel("プレビュー操作"));
        Label guide = new Label(
            "• マウスホイール : ズームイン／アウト\n" +
            "• 左ボタンドラッグ : パン（移動）");
        guide.setStyle("-fx-text-fill: #666666; -fx-font-size: 12px; -fx-line-spacing: 3;");
        content.getChildren().add(withMargin(guide, 0, 0, 10, 0));

        Button resetZoomBtn = secondaryButton("ズームリセット");
        resetZoomBtn.setMaxWidth(Double.MAX_VALUE);
        resetZoomBtn.setOnAction(e -> onResetZoom());

        Button fitBtn = secondaryButton("全体表示");
        fitBtn.setMaxWidth(Double.MAX_VALUE);
        fitBtn.setOnAction(e -> onFitToWindow());

        HBox zoomBtns = new HBox(8, resetZoomBtn, fitBtn);
        HBox.setHgrow(resetZoomBtn, Priority.ALWAYS);
        HBox.setHgrow(fitBtn, Priority.ALWAYS);
        content.getChildren().add(zoomBtns);

        scroll.setContent(content);
        outer.getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return outer;
    }

    // ────── 右パネル (プレビュー) ─────────────────

    private Node buildPreviewPanel() {
        // 描画コンテンツ (PNG + カットパス)
        previewImageView = new ImageView();
        previewImageView.setPreserveRatio(false);
        previewImageView.setSmooth(true);

        cutPathShape = new Path();
        cutPathShape.setFill(null);
        cutPathShape.setStroke(Color.RED);
        cutPathShape.setStrokeWidth(1.5);

        contentGroup = new Group(previewImageView, cutPathShape);
        // transforms[0]: Scale (inner, pivot 0,0)
        // transforms[1]: Translate (outer)
        // → screen = Translate * Scale * local
        contentGroup.getTransforms().setAll(scaleXform, translateXform);

        previewPane = new Pane(contentGroup);
        previewPane.setStyle("-fx-background-color: #252526;");

        // プレースホルダー
        Label iconLabel = new Label("✂");
        iconLabel.setStyle("-fx-font-size: 32px; -fx-text-fill: #666666;");
        Label hint1 = new Label("PNG ファイルを選択して");
        Label hint2 = new Label("「プレビューを生成」をクリックしてください");
        hint1.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        hint2.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        placeholder = new VBox(12, iconLabel, hint1, hint2);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setMouseTransparent(true); // マウスイベントを後ろに通す

        // 処理中オーバーレイ
        Label procLabel = new Label("処理中...");
        procLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: white;");
        processingOverlay = new StackPane(procLabel);
        processingOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.65);");
        processingOverlay.setVisible(false);
        processingOverlay.setMouseTransparent(false);

        StackPane container = new StackPane(previewPane, placeholder, processingOverlay);
        container.setStyle("-fx-background-color: #252526;");

        // previewPane のサイズをコンテナに追従
        previewPane.prefWidthProperty().bind(container.widthProperty());
        previewPane.prefHeightProperty().bind(container.heightProperty());
        processingOverlay.prefWidthProperty().bind(container.widthProperty());
        processingOverlay.prefHeightProperty().bind(container.heightProperty());

        // ── ズーム (マウスホイール) ──
        previewPane.setOnScroll(e -> {
            if (currentResult == null) return;
            double factor   = e.getDeltaY() > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP;
            double oldScale = currentScale;
            currentScale    = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, currentScale * factor));
            double ratio    = currentScale / oldScale;
            double mx       = e.getX();
            double my       = e.getY();
            txVal = mx - ratio * (mx - txVal);
            tyVal = my - ratio * (my - tyVal);
            applyTransforms();
            e.consume();
        });

        // ── パン (左ボタンドラッグ) ──
        previewPane.setOnMousePressed(e -> {
            if (currentResult == null || !e.isPrimaryButtonDown()) return;
            isPanning  = true;
            dragStartX = e.getX();
            dragStartY = e.getY();
            startTx    = txVal;
            startTy    = tyVal;
            previewPane.getScene().setCursor(javafx.scene.Cursor.CLOSED_HAND);
        });
        previewPane.setOnMouseDragged(e -> {
            if (!isPanning) return;
            txVal = startTx + (e.getX() - dragStartX);
            tyVal = startTy + (e.getY() - dragStartY);
            applyTransforms();
        });
        previewPane.setOnMouseReleased(e -> {
            isPanning = false;
            if (previewPane.getScene() != null)
                previewPane.getScene().setCursor(
                    currentResult != null ? javafx.scene.Cursor.OPEN_HAND
                                         : javafx.scene.Cursor.DEFAULT);
        });
        previewPane.setOnMouseEntered(e -> {
            if (currentResult != null && previewPane.getScene() != null)
                previewPane.getScene().setCursor(javafx.scene.Cursor.OPEN_HAND);
        });
        previewPane.setOnMouseExited(e -> {
            if (!isPanning && previewPane.getScene() != null)
                previewPane.getScene().setCursor(javafx.scene.Cursor.DEFAULT);
        });

        return container;
    }

    // ────── ステータスバー ────────────────────────

    private Node buildStatusBar() {
        statusLabel = new Label("準備完了");
        statusLabel.setStyle("-fx-text-fill: #444444; -fx-padding: 3 8;");
        zoomLabel = new Label("--");
        zoomLabel.setStyle("-fx-text-fill: #444444; -fx-padding: 3 8;");

        HBox bar = new HBox();
        bar.setStyle("-fx-background-color: #F0F0F0; " +
                     "-fx-border-color: #D1D1D1; -fx-border-width: 1 0 0 0;");
        bar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        bar.getChildren().addAll(statusLabel, zoomLabel);
        return bar;
    }

    // ──────────────────────────────────────────────
    //  イベントハンドラ
    // ──────────────────────────────────────────────

    private void onBrowse() {
        FileChooser fc = new FileChooser();
        fc.setTitle("PNG ファイルを選択");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("PNG ファイル", "*.png"),
            new FileChooser.ExtensionFilter("すべてのファイル", "*.*")
        );
        File file = fc.showOpenDialog(primaryStage);
        if (file == null) return;

        inputPathField.setText(file.getAbsolutePath());
        inputPathField.setStyle("-fx-background-color: #F8F8F8; " +
                                "-fx-border-color: #CCCCCC; -fx-text-fill: black;");
        generateButton.setDisable(false);
        setStatus("ファイル選択: " + file.getName());
    }

    private void onGenerate() {
        String inputPath = inputPathField.getText();
        if (!new File(inputPath).exists()) {
            showError("PNG ファイルが見つかりません。");
            return;
        }

        int     offset = (int) offsetSlider.getValue();
        boolean smooth = smoothCheckBox.isSelected();

        generateButton.setDisable(true);
        saveButton.setDisable(true);
        processingOverlay.setVisible(true);
        setStatus("生成中...");

        Task<GeneratedResult> task = new Task<>() {
            @Override
            protected GeneratedResult call() throws Exception {
                return CutPathGenerator.generate(inputPath, offset, smooth);
            }
        };

        task.setOnSucceeded(e -> {
            currentResult = task.getValue();
            applyPreview(currentResult, smooth);
            saveButton.setDisable(false);
            setStatus(String.format(
                "生成完了 — %d × %d px  |  輪郭数: %d  |  オフセット: %d px",
                currentResult.width, currentResult.height,
                currentResult.contours.length, offset));
            generateButton.setDisable(false);
            processingOverlay.setVisible(false);
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            showError("エラーが発生しました:\n" + (ex != null ? ex.getMessage() : "不明なエラー"));
            setStatus("エラーが発生しました");
            generateButton.setDisable(false);
            processingOverlay.setVisible(false);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void onSave() {
        if (currentResult == null) return;

        String baseName = new File(inputPathField.getText())
                .getName().replaceFirst("(?i)\\.png$", "");
        FileChooser fc = new FileChooser();
        fc.setTitle("SVG ファイルを保存");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("SVG ファイル", "*.svg"));
        fc.setInitialFileName(baseName + "_cutpath.svg");

        File file = fc.showSaveDialog(primaryStage);
        if (file == null) return;

        try {
            Files.writeString(file.toPath(), currentResult.svgContent);
            setStatus("保存完了: " + file.getAbsolutePath());
            Alert dlg = new Alert(Alert.AlertType.INFORMATION,
                    "SVG ファイルを保存しました:\n" + file.getAbsolutePath(),
                    ButtonType.OK);
            dlg.setTitle("保存完了");
            dlg.setHeaderText(null);
            dlg.showAndWait();
        } catch (IOException ex) {
            showError("保存エラー:\n" + ex.getMessage());
        }
    }

    private void onResetZoom() {
        currentScale = 1.0;
        txVal = 0;
        tyVal = 0;
        applyTransforms();
    }

    private void onFitToWindow() {
        if (currentResult == null) return;
        // レイアウト確定後に実行
        Platform.runLater(() -> {
            double pW = previewPane.getWidth();
            double pH = previewPane.getHeight();
            if (pW <= 0 || pH <= 0) return;

            double s = Math.min(pW / currentResult.width, pH / currentResult.height) * 0.95;
            s = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, s));

            currentScale = s;
            txVal = (pW - currentResult.width  * s) / 2.0;
            tyVal = (pH - currentResult.height * s) / 2.0;
            applyTransforms();
        });
    }

    // ──────────────────────────────────────────────
    //  プレビュー更新
    // ──────────────────────────────────────────────

    private void applyPreview(GeneratedResult result, boolean smooth) {
        // PNG 画像をロード
        Image img = new Image(new File(result.inputPngPath).toURI().toString());
        previewImageView.setImage(img);
        previewImageView.setFitWidth(result.width   - 2 * result.imageX);
        previewImageView.setFitHeight(result.height - 2 * result.imageY);
        previewImageView.setTranslateX(result.imageX);
        previewImageView.setTranslateY(result.imageY);

        // カットパスを更新
        Path generated = CutPathGenerator.createJavaFXPath(result.contours, smooth);
        cutPathShape.getElements().setAll(generated.getElements());

        // プレースホルダーを非表示
        placeholder.setVisible(false);

        // 全体表示にフィット
        onFitToWindow();
    }

    private void applyTransforms() {
        scaleXform.setX(currentScale);
        scaleXform.setY(currentScale);
        translateXform.setX(txVal);
        translateXform.setY(tyVal);
        updateZoomLabel();
    }

    // ──────────────────────────────────────────────
    //  UI ユーティリティ
    // ──────────────────────────────────────────────

    private void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private void updateZoomLabel() {
        zoomLabel.setText(String.format("%.0f%%", currentScale * 100));
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.setTitle("エラー");
            a.setHeaderText(null);
            a.showAndWait();
        });
    }

    private static Label styledLabel(String text, String style) {
        Label l = new Label(text);
        if (style != null) l.setStyle(style);
        return l;
    }

    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-text-fill: #323130;");
        l.setPadding(new Insets(0, 0, 6, 0));
        return l;
    }

    /** VBox.setMargin を設定してそのままノードを返す */
    private static Node withMargin(Node n, double top, double right, double bottom, double left) {
        VBox.setMargin(n, new Insets(top, right, bottom, left));
        return n;
    }

    private static Node hSeparator(double topMargin, double bottomMargin) {
        Separator s = new Separator();
        VBox.setMargin(s, new Insets(topMargin, 0, bottomMargin, 0));
        return s;
    }

    private static Button primaryButton(String text) {
        Button b = new Button(text);
        b.setStyle("""
            -fx-background-color: #0078D4;
            -fx-text-fill: white;
            -fx-background-radius: 4;
            -fx-padding: 9 0;
            -fx-cursor: hand;
            -fx-font-weight: bold;
        """);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private static Button secondaryButton(String text) {
        Button b = new Button(text);
        b.setStyle("""
            -fx-background-color: white;
            -fx-text-fill: #323130;
            -fx-border-color: #CCCCCC;
            -fx-border-radius: 4;
            -fx-background-radius: 4;
            -fx-padding: 7 12;
            -fx-cursor: hand;
        """);
        return b;
    }
}

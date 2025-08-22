package org.nexus.ftc.replay;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.animation.AnimationTimer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Gson imports for JSON parsing
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;

public class ReplayViewer extends Application {

    private Canvas fieldCanvas;
    private GraphicsContext gc;
    private ReplayData replayData;
    private int currentFrameIndex = 0;
    private boolean isPlaying = false;
    private double playbackSpeed = 1;
    private Slider timelineSlider;
    private Label timeLabel;
    private AnimationTimer animationTimer;
    private VBox customDataPanel;
    private Image fieldBackgroundImage;

    // Add timing variables as instance fields
    private long lastUpdateTime = 0;
    private double accumulatedTime = 0;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #222222;");

        fieldBackgroundImage = new Image(getClass().getResource("/field_background.png").toExternalForm());
        // Field canvas (top-down view)
        fieldCanvas = new Canvas(800, 800);
        gc = fieldCanvas.getGraphicsContext2D();
        drawEmptyField();
        root.setCenter(fieldCanvas);

        // Control panel
        VBox controlPanel = createControlPanel();
        root.setRight(controlPanel);

        // Playback controls
        HBox playbackControls = createPlaybackControls();
        root.setBottom(playbackControls);

        // Menu bar for loading files
        HBox menuBar = createMenuBar(primaryStage);
        root.setTop(menuBar);

        controlPanel.setStyle("-fx-padding: 10; -fx-background-color: #222; -fx-text-fill: #eee;");
        playbackControls.setStyle("-fx-padding: 10; -fx-background-color: #333; -fx-text-fill: #eee;");
        menuBar.setStyle("-fx-padding: 10; -fx-background-color: #222; -fx-text-fill: #eee;");

        // Create scene
        Scene scene = new Scene(root, 1200, 900);
        scene.setFill(Color.web("#222222"));
        controlPanel.setStyle("-fx-padding: 10; -fx-background-color: #222; -fx-text-fill: #eee;");
        playbackControls.setStyle("-fx-padding: 10; -fx-background-color: #333; -fx-text-fill: #eee;");
        menuBar.setStyle("-fx-padding: 10; -fx-background-color: #222; -fx-text-fill: #eee;");

        customDataPanel.setStyle("-fx-padding: 5; -fx-background-color: #222; -fx-text-fill: #eee;");

        primaryStage.setTitle("FTC Match Replay Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();

        for (javafx.scene.Node node : customDataPanel.getChildren()) {
            if (node instanceof Label) {
                ((Label) node).setStyle("-fx-text-fill: #eee;");
            }
        }

        // Animation timer for playback
        // Animation timer for playback
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isPlaying || replayData == null) return;

                if (lastUpdateTime == 0) {
                    lastUpdateTime = now;
                    return;
                }

                // Convert nanoseconds to milliseconds
                long elapsedMs = (now - lastUpdateTime) / 1_000_000;
                long adjustedElapsedMs = (long)(elapsedMs * playbackSpeed);

                // Limit large jumps
                adjustedElapsedMs = Math.min(adjustedElapsedMs, 100);

                // Add elapsed time to accumulated time
                accumulatedTime += adjustedElapsedMs;

                // Find current frame
                FrameData currentFrame = replayData.frames.get(currentFrameIndex);

                // Calculate target time
                long targetTime = currentFrame.timeMs + (long)accumulatedTime;

                // Advance to the correct frame based on target time
                while (currentFrameIndex < replayData.frames.size() - 1 &&
                        replayData.frames.get(currentFrameIndex + 1).timeMs <= targetTime) {
                    FrameData nextFrame = replayData.frames.get(currentFrameIndex + 1);
                    // Subtract the time difference from accumulated time
                    accumulatedTime -= (nextFrame.timeMs - currentFrame.timeMs);
                    currentFrameIndex++;
                    currentFrame = nextFrame;
                }

                // Update display with interpolation
                if (currentFrameIndex < replayData.frames.size() - 1) {
                    FrameData nextFrame = replayData.frames.get(currentFrameIndex + 1);
                    double timeDiff = nextFrame.timeMs - currentFrame.timeMs;
                    double factor = timeDiff > 0 ? accumulatedTime / timeDiff : 0;
                    factor = Math.max(0, Math.min(1, factor));
                    updateDisplayWithInterpolation(currentFrame, nextFrame, factor);
                } else {
                    // At the last frame
                    updateDisplay();
                }

                // Update slider position
                updateTimelineSlider();

                // Loop playback when reaching the end
                if (currentFrameIndex >= replayData.frames.size() - 1 &&
                        accumulatedTime >= (replayData.frames.get(replayData.frames.size() - 1).timeMs -
                                replayData.frames.get(Math.max(0, replayData.frames.size() - 2)).timeMs)) {
                    currentFrameIndex = 0;
                    accumulatedTime = 0;
                    lastUpdateTime = 0; // Reset timer
                }

                lastUpdateTime = now;
            }
        };
    }

    private void updateTimelineSlider() {
        if (replayData == null) return;

        double percentage = 100.0 * currentFrameIndex / (replayData.frames.size() - 1);
        timelineSlider.setValue(percentage);
    }

    private void updateDisplayWithInterpolation(FrameData frame1, FrameData frame2, double factor) {
        // Clamp factor to [0,1]
        factor = Math.max(0, Math.min(1, factor));

        // Interpolate position and heading
        double x = frame1.x + (frame2.x - frame1.x) * factor;
        double y = frame1.y + (frame2.y - frame1.y) * factor;
        double heading = interpolateAngle(frame1.heading, frame2.heading, factor);

        // Draw the interpolated position
        drawEmptyField();
        drawRobot(x, y, heading);

        // Use closest frame's data for display
        updateCustomDataDisplay(factor < 0.5 ? frame1 : frame2);

        // Update time display
        long interpolatedTimeMs = frame1.timeMs + (long)((frame2.timeMs - frame1.timeMs) * factor);
        updateTimeDisplay(interpolatedTimeMs);
    }

    // Helper method for angle interpolation (handles wraparound correctly)
    private double interpolateAngle(double a, double b, double factor) {
        // Normalize angles to [0, 360)
        a = ((a % 360) + 360) % 360;
        b = ((b % 360) + 360) % 360;

        // Find the shortest path
        double diff = b - a;
        if (diff > 180) {
            diff -= 360;
        } else if (diff < -180) {
            diff += 360;
        }

        // Interpolate
        double result = a + diff * factor;

        // Normalize result
        return ((result % 360) + 360) % 360;
    }

    private void updateTimeDisplay(long timeMs) {
        long seconds = timeMs / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        long millis = timeMs % 1000;
        timeLabel.setText(String.format("Time: %d:%02d.%03d", minutes, seconds, millis));
    }

    private void drawEmptyField() {
        // Draw the FTC field background image inside the border
        double borderX = 50;
        double borderY = 50;
        double borderWidth = fieldCanvas.getWidth() - 100;
        double borderHeight = fieldCanvas.getHeight() - 100;

        // Fill background with dark color
        gc.setFill(Color.web("#222222"));
        gc.fillRect(0, 0, fieldCanvas.getWidth(), fieldCanvas.getHeight());

        // Draw image inside border
        if (fieldBackgroundImage != null) {
            gc.drawImage(fieldBackgroundImage, borderX, borderY, borderWidth, borderHeight);
        }

        // Draw field elements (perimeter, grid, etc.) with dark gray
        gc.setStroke(Color.web("#444444"));
        gc.setLineWidth(3);
        gc.strokeRect(borderX, borderY, borderWidth, borderHeight);

        gc.setLineWidth(0.5);
        for (int i = 0; i <= 6; i++) {
            double x = borderX + i * borderWidth / 6;
            double y = borderY + i * borderHeight / 6;
            gc.strokeLine(borderX, y, borderX + borderWidth, y);
            gc.strokeLine(x, borderY, x, borderY + borderHeight);
        }
    }

    private void drawRobot(double x, double y, double heading) {
        // Convert field coordinates to canvas coordinates
        double fieldSize = 12 * 12; // 12 feet in inches
        double scale = (fieldCanvas.getWidth() - 100) / fieldSize;

        double canvasX = 50 + (x * scale);
        double canvasY = fieldCanvas.getHeight() - 50 - (y * scale);

        // Draw robot (18" x 18" square)
        double robotSize = 18 * scale;

        gc.save();
        gc.translate(canvasX, canvasY);
        gc.rotate(-heading); // Negative because canvas Y is inverted

        // Robot body
        gc.setFill(Color.BLUE);
        gc.fillRect(-robotSize/2, -robotSize/2, robotSize, robotSize);

        // Direction indicator
        gc.setFill(Color.RED);
        gc.fillPolygon(
                new double[]{robotSize/2, robotSize/2 - 10, robotSize/2 - 10},
                new double[]{0, -10, 10},
                3
        );

        gc.restore();
    }

    private VBox createControlPanel() {
        VBox panel = new VBox(10);
        panel.setStyle("-fx-padding: 10; -fx-background-color: #222; -fx-text-fill: #eee;");
        panel.setPrefWidth(300);

        Label title = new Label("Match Data");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16; -fx-text-fill: #eee; ");

        // Create panel for custom data that will be updated when replay is loaded
        customDataPanel = new VBox(5);
        customDataPanel.setStyle("-fx-padding: 5;");

        panel.getChildren().addAll(title, customDataPanel);
        return panel;
    }

    private HBox createPlaybackControls() {
        HBox controls = new HBox(10);
        controls.setStyle("-fx-padding: 10; -fx-background-color: #333; -fx-text-fill: #eee;");
        Button playButton = new Button("Play");
        playButton.setOnAction(e -> {
            isPlaying = !isPlaying;
            playButton.setText(isPlaying ? "Pause" : "Play");

            if (isPlaying) {
                lastUpdateTime = 0; // Reset timer to prevent jumps
                animationTimer.start();
            } else {
                animationTimer.stop();
            }
        });

        Button resetButton = new Button("Reset");
        resetButton.setOnAction(e -> {
            isPlaying = false;
            animationTimer.stop();
            currentFrameIndex = 0;
            accumulatedTime = 0;
            lastUpdateTime = 0;
            updateDisplay();
            updateTimelineSlider();
            playButton.setText("Play");
        });

        timelineSlider = new Slider();
        timelineSlider.setPrefWidth(500);
        timelineSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (replayData != null) {
                // Stop playback when moving slider
                isPlaying = false;
                animationTimer.stop();

                currentFrameIndex = (int)Math.floor(newVal.doubleValue() * (replayData.frames.size() - 1) / 100);
                accumulatedTime = 0;
                lastUpdateTime = 0;
                updateDisplay();
                playButton.setText("Play");
            }
        });

        // Ultra-slow speed slider
        Slider speedSlider = new Slider(0.01, 1.0, 0.05);
        speedSlider.setPrefWidth(150);
        speedSlider.setShowTickMarks(true);
        speedSlider.setShowTickLabels(true);
        speedSlider.setMajorTickUnit(0.1);
        speedSlider.setValue(1);

        Label speedLabel = new Label("Speed: 1.00x"); // Initial label matches slider

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            playbackSpeed = newVal.doubleValue();

            // Format the speed label with a fraction for very slow speeds
            if (playbackSpeed < 0.1) {
                int denominator = (int)(1.0 / playbackSpeed);
                speedLabel.setText(String.format("Speed: %.2fx (1/%d speed)", playbackSpeed, denominator));
            } else {
                speedLabel.setText(String.format("Speed: %.2fx", playbackSpeed));
            }
        });

        timeLabel = new Label("Time: 0:00.000");

        VBox speedControls = new VBox(5);

        timeLabel.setStyle("-fx-text-fill: #eee;");
        speedLabel.setStyle("-fx-text-fill: #eee;");
        speedControls.getChildren().addAll(speedLabel, speedSlider);
        controls.getChildren().addAll(playButton, resetButton, timelineSlider, timeLabel, speedControls);
        return controls;
    }

    private HBox createMenuBar(Stage primaryStage) {
        HBox menuBar = new HBox(10);
        menuBar.setStyle("-fx-padding: 10; -fx-background-color: #222; -fx-text-fill: #eee;");
        menuBar.setAlignment(Pos.TOP_RIGHT); // Align to top right

        Button loadButton = new Button("Load Replay");
        Button creditsButton = new Button("Credits");

        creditsButton.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Credits");
            alert.setHeaderText("FTC Match Replay Viewer");

            // Apply dark mode stylesheet
            alert.getDialogPane().getStylesheets().add(getClass().getResource("/dark-alert.css").toExternalForm());

            VBox content = new VBox(5);
            content.getChildren().add(new Label("Developed by Team 3796 Talons"));
            Hyperlink githubLink = new Hyperlink("Available on GitHub: https://github.com/Blaziumm/ftc-replay-viewer");
            githubLink.setOnAction(ev -> getHostServices().showDocument("https://github.com/Blaziumm/ftc-replay-viewer"));
            content.getChildren().add(githubLink);
            alert.getDialogPane().setContent(content);

            alert.showAndWait();
        });
        loadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Replay File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Replay Files", "*.replay")
            );
            File selectedFile = fileChooser.showOpenDialog(primaryStage);

            if (selectedFile != null) {
                loadReplayFile(selectedFile);
                isPlaying = false;
                animationTimer.stop();
                currentFrameIndex = 0;
                updateDisplay();
            }
        });

        menuBar.getChildren().addAll(loadButton, creditsButton);
        return menuBar;
    }

    private void loadReplayFile(File file) {
        try {
            // Create Gson instance for JSON parsing
            Gson gson = new GsonBuilder().create();

            // Read the file content
            try (FileReader reader = new FileReader(file)) {
                // Parse JSON into ReplayData object
                replayData = gson.fromJson(reader, ReplayData.class);

                if (replayData == null || replayData.frames == null || replayData.frames.isEmpty()) {
                    throw new JsonSyntaxException("Invalid replay file format or empty data");
                }

                // Display match info
                System.out.println("Loaded match: " + replayData.match);
                System.out.println("Team: " + replayData.team);
                System.out.println("Date: " + new java.util.Date(replayData.date));
                System.out.println("Frame count: " + replayData.frames.size());

                // Update UI with loaded data
                timelineSlider.setValue(0);
                updateMatchInfoDisplay();
            }
        } catch (JsonSyntaxException e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
            showError("Invalid JSON format: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            showError("Could not read file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error loading replay file: " + e.getMessage());
            showError("Error: " + e.getMessage());
        }
    }

    private void showError(String message) {
        // In a real application, show this in a dialog
        System.err.println(message);
    }

    private void updateMatchInfoDisplay() {
        if (replayData == null) return;

        // Update match info in the control panel
        customDataPanel.getChildren().clear();

        Label teamLabel = new Label("Team: " + replayData.team);
        Label matchLabel = new Label("Match: " + replayData.match);
        Label dateLabel = new Label("Date: " + new java.util.Date(replayData.date));
        Label framesLabel = new Label("Total Frames: " + replayData.frames.size());
        Label durationLabel = new Label(String.format("Match Duration: %.1f seconds",
                replayData.frames.get(replayData.frames.size()-1).timeMs / 1000.0));

        teamLabel.setStyle("-fx-text-fill: #eee;");
        matchLabel.setStyle("-fx-text-fill: #eee;");
        dateLabel.setStyle("-fx-text-fill: #eee;");
        framesLabel.setStyle("-fx-text-fill: #eee;");
        durationLabel.setStyle("-fx-text-fill: #eee;");

        customDataPanel.getChildren().addAll(teamLabel, matchLabel, dateLabel, framesLabel, durationLabel);
    }

    private void updateDisplay() {
        if (replayData == null || replayData.frames.isEmpty()) return;

        // Get current frame
        FrameData frame = replayData.frames.get(currentFrameIndex);

        // Update time label
        updateTimeDisplay(frame.timeMs);

        // Redraw field
        drawEmptyField();
        drawRobot(frame.x, frame.y, frame.heading);

        // Update custom data display
        updateCustomDataDisplay(frame);
    }

    private void updateCustomDataDisplay(FrameData frame) {
        if (frame == null || frame.customData == null) return;

        // Update the custom data section of the control panel
        VBox dataBox = new VBox(5);
        dataBox.setStyle("-fx-padding: 5; -fx-border-color: #cccccc; -fx-border-radius: 5;");

        Label frameDataTitle = new Label("Frame Data");
        frameDataTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #eee;");
        dataBox.getChildren().add(frameDataTitle);

        // Add position data
        dataBox.getChildren().add(new Label(String.format("X: %.2f", frame.x)));
        dataBox.getChildren().add(new Label(String.format("Y: %.2f", frame.y)));
        dataBox.getChildren().add(new Label(String.format("Heading: %.2f°", frame.heading)));

        // Add separator
        Label customDataTitle = new Label("Custom Data");
        customDataTitle.setStyle("-fx-font-weight: bold; -fx-padding: 5 0 0 0;-fx-text-fill: #eee;");
        dataBox.getChildren().add(customDataTitle);

        // Add all custom data entries
        for (Map.Entry<String, Object> entry : frame.customData.entrySet()) {
            dataBox.getChildren().add(new Label(entry.getKey() + ": " + entry.getValue()));
        }

        // Replace previous custom data display
        if (customDataPanel.getChildren().size() > 5) {
            customDataPanel.getChildren().set(customDataPanel.getChildren().size() - 1, dataBox);
        } else {
            customDataPanel.getChildren().add(dataBox);
        }

        for (javafx.scene.Node node : dataBox.getChildren()) {
            if (node instanceof Label) {
                ((Label) node).setStyle("-fx-text-fill: #eee;");
            }
        }
    }

    // Data classes to hold replay information
    private static class ReplayData {
        String team;
        String match;
        long date;
        List<FrameData> frames = new ArrayList<>();
    }

    private static class FrameData {
        long timeMs;
        double x;
        double y;
        double heading;
        Map<String, Object> customData = new HashMap<>();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
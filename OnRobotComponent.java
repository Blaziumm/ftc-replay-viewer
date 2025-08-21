package org.firstinspires.ftc.teamcode.replay;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.navigation.Position;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchRecorder {
    private final OpMode opMode;
    private final ElapsedTime timer = new ElapsedTime();
    private final List<MatchFrame> frames = new ArrayList<>();
    private final int recordingFrequencyHz;
    private long lastRecordTimeMs = 0;
    private boolean isRecording = false;
    
    public MatchRecorder(OpMode opMode, int recordingFrequencyHz) {
        this.opMode = opMode;
        this.recordingFrequencyHz = recordingFrequencyHz;
    }
    
    public void startRecording() {
        frames.clear();
        timer.reset();
        isRecording = true;
        lastRecordTimeMs = 0;
    }
    
    public void update(double x, double y, double heading) {
        if (!isRecording) return;
        
        long currentTimeMs = (long)(timer.milliseconds());
        long recordInterval = 1000 / recordingFrequencyHz;
        
        if (currentTimeMs - lastRecordTimeMs >= recordInterval) {
            Map<String, Object> customData = new HashMap<>();
            
            // Add example custom data - replace with your own sensors/motors
            customData.put("leftDriveEncoder", opMode.hardwareMap.dcMotor.get("leftDrive").getCurrentPosition());
            customData.put("rightDriveEncoder", opMode.hardwareMap.dcMotor.get("rightDrive").getCurrentPosition());
            
            // Create and add frame
            MatchFrame frame = new MatchFrame(
                currentTimeMs,
                x,
                y,
                heading,
                customData
            );
            frames.add(frame);
            lastRecordTimeMs = currentTimeMs;
        }
    }
    
    public void stopRecording() {
        isRecording = false;
    }
    
    public void saveReplay(String fileName) {
        if (frames.isEmpty()) {
            opMode.telemetry.addData("Error", "No replay data to save");
            opMode.telemetry.update();
            return;
        }
        
        try {
            // Create match data object
            Map<String, Object> matchData = new HashMap<>();
            matchData.put("team", "YourTeamNumber");
            matchData.put("match", fileName);
            matchData.put("date", System.currentTimeMillis());
            matchData.put("frames", frames);
            
            // Convert to JSON (using a simple JSON conversion implementation)
            String json = convertToJson(matchData);
            
            // Save to file
            File replayDir = new File("/sdcard/FIRST/replays");
            if (!replayDir.exists()) {
                replayDir.mkdirs();
            }
            
            File replayFile = new File(replayDir, fileName + ".replay");
            FileWriter writer = new FileWriter(replayFile);
            writer.write(json);
            writer.close();
            
            opMode.telemetry.addData("Replay Saved", replayFile.getAbsolutePath());
            opMode.telemetry.update();
        } catch (IOException e) {
            opMode.telemetry.addData("Error", "Failed to save replay: " + e.getMessage());
            opMode.telemetry.update();
        }
    }
    
    // Simple class to represent a frame of data
    private static class MatchFrame {
        long timeMs;
        double x;
        double y;
        double heading;
        Map<String, Object> customData;
        
        public MatchFrame(long timeMs, double x, double y, double heading, Map<String, Object> customData) {
            this.timeMs = timeMs;
            this.x = x;
            this.y = y;
            this.heading = heading;
            this.customData = customData;
        }
    }
    
    // Simple JSON conversion (in practice, use a proper JSON library like Gson)
    private String convertToJson(Map<String, Object> data) {
        // Implementation omitted for brevity
        // In your actual code, use Gson or another JSON library
        return "{}"; // Placeholder
    }
}

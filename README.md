# FTC Match Replay Viewer

A JavaFX application for visualizing FTC match replays. Load `.replay` files to view robot movement, match data, and custom telemetry.

- Top-down FTC field visualization
- Robot position and heading display
- Timeline slider and playback controls
- Custom data panel for frame telemetry

## Setup

1. **Requirements:**
   - Java 11+

2. **Download Latest Release:**
   --Coming Soon--

4. **Put the Robot Component Into Your Teamcode:** Can be found [here](https://gist.github.com/Blaziumm/d439542c0a41481c1b11d9c34db8675b.js).


# Usage of MatchRecorder:
[Example OpMode](https://gist.github.com/Blaziumm/90784dc3d2b72986ca1a0c94e7d22b97)


**MatchRecorder.getNextAvailableMatchNumber()** - Gets the next match number not currently used (To reset delete the used_match_numbers.txt file on RC)

**startMatch()** - Must Call startMatch before recording any frames

**recordFrame()** - records a "waypoint" of data 

**saveToFile()** - saves the data to a file, must be called at the end of opmode

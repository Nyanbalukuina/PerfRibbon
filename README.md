# PerfRibbon

[English](README.md) | [日本語](README.ja.md)

A PC monitoring app for Windows. It displays game FPS, displayed FPS, GPU usage and temperature, and CPU and RAM usage in a single line, updated approximately once per second. Clicks pass through the ribbon to the app underneath.

## Screenshots

The ribbon (FPS is unavailable in this screenshot):

![PerfRibbon monitoring ribbon](ribbon.png)

The system tray menu for selecting a position or exiting:

![PerfRibbon system tray menu](menu.png)

## Requirements

- 64-bit Windows
- The distribution ZIP includes Java, so no separate Java installation is required.
- GPU metrics support NVIDIA (`nvidia-smi`) and AMD (ADLX included with the driver). Unavailable metrics appear as `--`.

## Getting started

1. Extract the distribution ZIP.
2. Launch `PerfRibbon.exe` inside the extracted folder. Keep the `app` and `runtime` folders alongside it; do not move the exe on its own.
3. **To measure FPS, right-click the exe and select "Run as administrator".** CPU, RAM, and GPU metrics continue to work when FPS measurement is unavailable due to insufficient permissions.

Right-click the "P" icon in the system tray to change the ribbon position or exit.

- `Left-Top` (default), `Left-Bottom`, `Right-Top`, `Right-Bottom`: move the ribbon to a corner of the primary monitor.
- `Exit`: close the app.

The selected position is saved automatically and restored on the next launch.

## Limitations

- FPS measurement follows the foreground game or app. Switching apps changes the measurement target.
- Game FPS is calculated from Present call intervals; displayed FPS is calculated from display change intervals. Detection of generated frames depends on the game and driver.
- Ribbon visibility in exclusive fullscreen mode has not been verified. At the bottom of the screen, the ribbon may be hidden behind the taskbar.
- If both NVIDIA and AMD metrics providers are available, GPU metrics appear as `--` because GPU selection is not yet implemented.

## Settings and uninstalling

Settings are stored in `%LOCALAPPDATA%\PerfRibbon\settings.json`.

To uninstall, select `Exit` and delete the extracted folder. To also remove saved settings, delete `%LOCALAPPDATA%\PerfRibbon`.

## Development and packaging

Built with Kotlin, JVM 21, and Gradle. Creating a distribution ZIP requires 64-bit Windows and JDK 21's `jpackage` tool.

```powershell
# Run from source (use an administrator PowerShell for FPS measurement)
.\gradlew.bat --no-daemon run

# Run automated tests
.\gradlew.bat --no-daemon test

# Create a ZIP containing the exe and Java in the release folder
.\package.ps1
```

The first build requires an internet connection. It downloads PresentMon 2.6.0 from the official release, verifies its SHA-256 hash, and bundles it with the app. The distributed app does not download it at launch.

## License

PerfRibbon's original code is released under the [MIT License](LICENSE). External software and libraries retain their respective licenses.

The PresentMon license and third-party notices are included in `src/main/resources/presentmon/` in the source and `licenses/PresentMon/` in the distribution.

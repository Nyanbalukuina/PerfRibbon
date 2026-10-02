# PerfRibbon

Windows向けのPC状態表示アプリ。メイン画面の左上にゲームFPS・表示FPS、CPU・RAM使用率とGPU使用率・温度を表示します。

## 起動

```powershell
.\gradlew.bat --no-daemon run
```

通知領域の「P」アイコンを右クリックして「終了」を選ぶと終了します。

## FPS対応

PresentMon 2.6.0で最前面のゲーム・アプリのプロセスを計測します。ゲーム専用の判別は行わないため、別のアプリに切り替えると対象も変わります。複数の描画ウィンドウ（swap chain）がある場合は、直近1秒で最もPresent数が多いものを表示し、合算はしません。

- ゲームFPS: `MsBetweenPresents`（Present呼び出し間隔）の平均から算出します。
- 表示FPS: `MsBetweenDisplayChange`（表示切り替え間隔）の平均から算出します。表示されなかったフレームは含めません。
- PresentMonが識別できた生成フレームは表示FPSに含め、ゲームFPSには含めません。フレーム生成の観測可否はゲーム・ドライバーに依存します。
- 直近約1秒の値を整数で表示します。切り替え直後やフレームが途切れた場合、取得できない項目は`--`になります。

**このPCでFPSを取得するには、PowerShellを「管理者として実行」してから起動してください。**
起動済みのPerfRibbonは通知領域の「終了」で閉じてください。管理者PowerShellでも通常権限の既存Gradleデーモンを再利用しないよう、起動コマンドには`--no-daemon`を付けます。
PresentMonのETW計測には管理者権限、またはWindowsのPerformance Log Usersグループでの実行権限が必要です。アプリは権限設定を自動変更しません。通常権限で拒否された場合もCPU・RAM・GPU表示は継続し、通知領域の「FPSの計測状態」で理由を確認できます。

ウィンドウなしで15秒間確認するには、次を実行し、ゲームを最前面に切り替えます。

```powershell
.\gradlew.bat --no-daemon run --args=--check-fps
```

特定のプロセスに固定する場合（1234をタスクマネージャーで確認したゲームのPIDへ置き換え）:

```powershell
.\gradlew.bat --no-daemon run --args="--fps-pid=1234"
```

初回ビルド時に[公式リリース](https://github.com/GameTechDev/PresentMon/releases/tag/v2.6.0)から実行ファイルを取得し、SHA-256を検証してアプリに同梱します。実行時のダウンロードやPresentMonの個別インストールは不要です。ライセンスは`src/main/resources/presentmon/LICENSE.txt`にあります。

PresentMon公式の記録済みETLの再生と、CSV解析・FPS計算の自動テストで確認しています。このPCでのライブ計測は通常権限では拒否されたため、管理者起動での実ゲーム確認は未実施です。排他的フルスクリーンでの最前面表示も未検証です。

## GPU対応

- NVIDIA: `nvidia-smi`から取得します。
- AMD: 64ビットWindowsで、AMDドライバーに同梱された`System32/amdadlx64.dll`（ADLX）から取得します。追加のSDKインストールは不要です。
- AMDの内蔵GPUと単体GPUがある場合、試作では単体GPUを優先します。同種が複数ある場合はADLXの列挙順で最初のGPUを使用します。
- NVIDIAとAMDの両方が取得可能な場合の選択UIは未実装です。その場合はGPUを自動選択せず`--`を表示します。
- 非対応・取得失敗の項目は`--`になります。AMDの温度は通常のGPU温度で、ホットスポット温度ではありません。

AMDの取得確認（ウィンドウを表示せず、選択GPU名と5回の測定結果を出力）:

```powershell
.\gradlew.bat run --args=--check-amd
```

Radeon RX 9070 XTで使用率・温度の連続取得を確認済みです。

ADLXの呼び出し定義は[AMD公式SDK](https://github.com/GPUOpen-LibrariesAndSDKs/ADLX/tree/main/SDK/Include)を参照しています。DLLはリポジトリには同梱しません。

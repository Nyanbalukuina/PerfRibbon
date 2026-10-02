# エラーが発生したら、その時点で処理を停止する
$ErrorActionPreference = "Stop"

# 配布バージョンと、現在のGradle設定に対応するJAR名を指定する
$appVersion = "1.0.1"
$mainJar = "PerfRibbon-1.0-SNAPSHOT.jar"

# スクリプトのあるフォルダーを基準に、出力先を決める
$projectDir = [System.IO.Path]::GetFullPath($PSScriptRoot)
$packageDir = Join-Path $projectDir "build\package"
$appDir = Join-Path $packageDir "PerfRibbon"
# 完成したZIPをプロジェクト直下のreleaseフォルダーに保存する
$zipDir = Join-Path $projectDir "release"
$zipPath = Join-Path $zipDir "PerfRibbon-$appVersion-windows-x64.zip"

# ビルド前に、必要なコマンドが利用できるか確認する
$jpackageCommand = Get-Command jpackage -ErrorAction Stop

# 作業フォルダーを一時的にプロジェクトへ変更する
Push-Location $projectDir

try {
    # テストを実行し、アプリと依存ライブラリをまとめる
    & .\gradlew.bat --no-daemon test installDist
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed."
    }

    # 削除対象が、プロジェクト内の配布フォルダーであることを確認する
    $expectedAppDir = [System.IO.Path]::GetFullPath(
        (Join-Path $projectDir "build\package\PerfRibbon")
    )
    if ([System.IO.Path]::GetFullPath($appDir) -ne $expectedAppDir) {
        throw "Unexpected output directory."
    }

    # 以前生成した配布フォルダーを削除する
    if (Test-Path -LiteralPath $appDir) {
        Remove-Item -LiteralPath $appDir -Recurse -Force
    }

    # 配布先とZIP保存先のフォルダーを用意する
    New-Item -ItemType Directory -Path $packageDir -Force | Out-Null
    New-Item -ItemType Directory -Path $zipDir -Force | Out-Null

    # exe・依存ライブラリ・Javaを含む配布フォルダーを作る
    $packageArguments = @(
        "--type", "app-image",
        "--name", "PerfRibbon",
        "--app-version", $appVersion,
        "--input", "build\install\PerfRibbon\lib",
        "--main-jar", $mainJar,
        "--main-class", "org.perfribbon.MainKt",
        "--dest", $packageDir
    )
    & $jpackageCommand.Source @packageArguments
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed."
    }

    # 説明書とPresentMonのライセンス通知を配布物に添える
    Copy-Item -LiteralPath "README.md" -Destination $appDir
    # 日本語版の説明書も配布フォルダーへコピーする
    Copy-Item -LiteralPath "README.ja.md" -Destination $appDir
    # 説明書に掲載するスクリーンショットを同梱する
    Copy-Item -LiteralPath "ribbon.png", "menu.png" -Destination $appDir
    # PerfRibbon自身のライセンスを配布フォルダーへコピーする
    Copy-Item -LiteralPath "LICENSE" -Destination $appDir
    $licenseDir = Join-Path $appDir "licenses\PresentMon"
    New-Item -ItemType Directory -Path $licenseDir -Force | Out-Null
    Copy-Item -LiteralPath "src\main\resources\presentmon\LICENSE.txt" `
        -Destination $licenseDir
    Copy-Item -LiteralPath "src\main\resources\presentmon\THIRD_PARTY.txt" `
        -Destination $licenseDir

    # フォルダー全体をZIPにし、同名のZIPがあれば上書きする
    Compress-Archive -LiteralPath $appDir `
        -DestinationPath $zipPath -Force

    # 完成したZIPの保存先を表示する
    Write-Host "Created: $zipPath"
}
finally {
    # 成功・失敗にかかわらず、元の作業フォルダーへ戻る
    Pop-Location
}

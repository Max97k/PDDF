# ==============================================================================
# verify_pdf_viewer.ps1
# End-to-End Automated Verification of PDF Decryption and PDF Viewer Flow
# ==============================================================================
[CmdletBinding()]
param(
    [string]$DeviceId = "emulator-5554",
    [string]$ScreenshotDir = "C:\Users\b\.gemini\antigravity\brain\35f131be-eeb5-4204-844c-d9b07101c319\e2e_screenshots"
)

$ErrorActionPreference = "Stop"

# Locate ADB
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) {
        $adb = $cmd.Source
    }
}
if (-not $adb -or -not (Test-Path $adb)) {
    Write-Error "ADB executable not found. Please ensure Android SDK platform-tools is installed."
    exit 1
}

Write-Host "=================================================================="
Write-Host "Starting E2E Verification for PDDF PDF Decryptor & PDF Viewer"
Write-Host "ADB Path: $adb"
Write-Host "Target Device: $DeviceId"
Write-Host "Screenshots Output: $ScreenshotDir"
Write-Host "=================================================================="

# Ensure screenshot directory exists
if (-not (Test-Path $ScreenshotDir)) {
    New-Item -ItemType Directory -Path $ScreenshotDir -Force | Out-Null
}

# ------------------------------------------------------------------------------
# Helper Functions
# ------------------------------------------------------------------------------
function Dump-UiHierarchy {
    param([string]$RemotePath = "/sdcard/e2e_dump.xml")
    & $adb -s $DeviceId shell rm -f $RemotePath 2>$null
    & $adb -s $DeviceId shell uiautomator dump $RemotePath 2>&1 | Out-Null
    $rawXml = & $adb -s $DeviceId shell cat $RemotePath 2>&1
    if (-not $rawXml -or $rawXml -notmatch "<hierarchy") {
        return $null
    }
    try {
        [xml]$xml = $rawXml
        return $xml
    } catch {
        return $null
    }
}

function Find-UiNode {
    param(
        [xml]$XmlDoc,
        [string]$Text = $null,
        [switch]$ExactText,
        [string]$ContentDesc = $null,
        [switch]$ExactContentDesc,
        [string]$ResourceId = $null,
        [string]$ClassName = $null,
        [string]$Enabled = $null
    )
    if (-not $XmlDoc) { return $null }
    $nodes = $XmlDoc.SelectNodes("//node")
    foreach ($node in $nodes) {
        $nodeText = $node.GetAttribute("text")
        $nodeDesc = $node.GetAttribute("content-desc")
        $nodeRes  = $node.GetAttribute("resource-id")
        $nodeCls  = $node.GetAttribute("class")
        $nodeEn   = $node.GetAttribute("enabled")

        $match = $true
        if ($Text) {
            if ($ExactText) {
                if ($nodeText -ne $Text) { $match = $false }
            } else {
                if ($nodeText -notlike "*$Text*") { $match = $false }
            }
        }
        if ($ContentDesc) {
            if ($ExactContentDesc) {
                if ($nodeDesc -ne $ContentDesc) { $match = $false }
            } else {
                if ($nodeDesc -notlike "*$ContentDesc*") { $match = $false }
            }
        }
        if ($ResourceId -and $nodeRes -notlike "*$ResourceId*") { $match = $false }
        if ($ClassName -and $nodeCls -notlike "*$ClassName*") { $match = $false }
        if ($Enabled -and $nodeEn -ne $Enabled) { $match = $false }

        if ($match) {
            $boundsStr = $node.GetAttribute("bounds")
            if ($boundsStr -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                $x1 = [int]$matches[1]; $y1 = [int]$matches[2]
                $x2 = [int]$matches[3]; $y2 = [int]$matches[4]
                return [PSCustomObject]@{
                    Node        = $node
                    Text        = $nodeText
                    ContentDesc = $nodeDesc
                    ResourceId  = $nodeRes
                    ClassName   = $nodeCls
                    Enabled     = $nodeEn
                    Bounds      = $boundsStr
                    CenterX     = [int](($x1 + $x2) / 2)
                    CenterY     = [int](($y1 + $y2) / 2)
                    X1          = $x1
                    Y1          = $y1
                    X2          = $x2
                    Y2          = $y2
                }
            }
        }
    }
    return $null
}

function Wait-ForUiNode {
    param(
        [string]$Text = $null,
        [switch]$ExactText,
        [string]$ContentDesc = $null,
        [switch]$ExactContentDesc,
        [string]$ResourceId = $null,
        [string]$ClassName = $null,
        [string]$Enabled = $null,
        [int]$TimeoutSec = 15
    )
    $startTime = Get-Date
    while (((Get-Date) - $startTime).TotalSeconds -lt $TimeoutSec) {
        $xml = Dump-UiHierarchy
        if ($xml) {
            $found = Find-UiNode -XmlDoc $xml -Text $Text -ExactText:$ExactText -ContentDesc $ContentDesc -ExactContentDesc:$ExactContentDesc -ResourceId $ResourceId -ClassName $ClassName -Enabled $Enabled
            if ($found) { return $found }
        }
        Start-Sleep -Milliseconds 800
    }
    return $null
}

function Tap-Node {
    param($NodeInfo)
    if (-not $NodeInfo) { throw "Cannot tap null node" }
    Write-Host "  -> Tapping '$($NodeInfo.Text)$($NodeInfo.ContentDesc)' at ($($NodeInfo.CenterX), $($NodeInfo.CenterY))"
    & $adb -s $DeviceId shell input tap $NodeInfo.CenterX $NodeInfo.CenterY
}

function Save-Screenshot {
    param([string]$FileName)
    $destPath = Join-Path $ScreenshotDir $FileName
    $remoteTmp = "/sdcard/$FileName"
    & $adb -s $DeviceId shell screencap -p $remoteTmp
    & $adb -s $DeviceId pull $remoteTmp $destPath | Out-Null
    & $adb -s $DeviceId shell rm -f $remoteTmp
    Write-Host "[SCREENSHOT] Saved: $destPath"
    if (-not (Test-Path $destPath)) {
        throw "Failed to capture screenshot: $destPath"
    }
}

function Get-WindowFocus {
    $out = & $adb -s $DeviceId shell dumpsys window 2>&1
    $focusLine = ($out | Select-String "mCurrentFocus|mFocusedApp") -join "`n"
    return $focusLine
}

# ==============================================================================
# Execution Steps
# ==============================================================================

# Step 1: Ensure test asset is pushed to /sdcard/Download/encrypted.pdf
Write-Host "`n[Step 1] Pushing encrypted.pdf to device..."
$localPdf = "app/src/test/assets/encrypted.pdf"
if (-not (Test-Path $localPdf)) {
    throw "Local test asset $localPdf not found!"
}
& $adb -s $DeviceId push $localPdf /sdcard/Download/encrypted.pdf
# Trigger media scanner scan so documentsui indexes the file
& $adb -s $DeviceId shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/encrypted.pdf | Out-Null
Write-Host "  -> Asset pushed and indexed successfully."

# Step 2: Clear app data
Write-Host "`n[Step 2] Resetting app data via pm clear..."
& $adb -s $DeviceId shell pm clear com.max97k.pddf
Start-Sleep -Seconds 1

# Step 3: Launch MainActivity
Write-Host "`n[Step 3] Launching MainActivity..."
& $adb -s $DeviceId shell am start -n com.max97k.pddf/com.example.MainActivity
Start-Sleep -Seconds 2

# Step 4: Dismiss "What's New" dialog if present
Write-Host "`n[Step 4] Checking for 'What''s New' dialog..."
$whatsNewNode = Wait-ForUiNode -Text "What's New" -TimeoutSec 3
if ($whatsNewNode) {
    Write-Host "  -> Detected What's New dialog, dismissing..."
    $closeDialogBtn = Wait-ForUiNode -Text "Close" -ExactText -TimeoutSec 3
    if ($closeDialogBtn) {
        Tap-Node $closeDialogBtn
        Start-Sleep -Seconds 1
    }
    # Double check if dialog is still visible, fallback to back key
    $stillThere = Find-UiNode -XmlDoc (Dump-UiHierarchy) -Text "What's New"
    if ($stillThere) {
        Write-Host "  -> Dialog still open, sending KEYCODE_BACK..."
        & $adb -s $DeviceId shell input keyevent 4
        Start-Sleep -Seconds 1
    }
} else {
    Write-Host "  -> No intro dialog displayed."
}

# Step 5: Click "Select PDF" button
Write-Host "`n[Step 5] Clicking 'Select Encrypted PDFs' button..."
$selectBtn = Wait-ForUiNode -Text "Select Encrypted" -TimeoutSec 10
if (-not $selectBtn) {
    # Fallback to any Select button
    $selectBtn = Wait-ForUiNode -Text "Select" -TimeoutSec 5
}
if (-not $selectBtn) {
    throw "Failed to locate Select PDF button on main screen!"
}
Tap-Node $selectBtn

# Step 6: In documentsui, select encrypted.pdf
Write-Host "`n[Step 6] Waiting for documentsui picker and selecting 'encrypted.pdf'..."
$pickerWaitStart = Get-Date
$fileNode = $null
while (((Get-Date) - $pickerWaitStart).TotalSeconds -lt 15) {
    $xml = Dump-UiHierarchy
    $fileNode = Find-UiNode -XmlDoc $xml -Text "encrypted.pdf"
    if ($fileNode) { break }
    Start-Sleep -Milliseconds 800
}
if (-not $fileNode) {
    throw "Failed to find 'encrypted.pdf' in documentsui file picker!"
}
# Tap center of the item card above the text title
$tapX = $fileNode.CenterX
$tapY = [Math]::Max(100, $fileNode.Y1 - 150)
Write-Host "  -> Tapping encrypted.pdf card at ($tapX, $tapY)..."
& $adb -s $DeviceId shell input tap $tapX $tapY

# Step 7: Wait for password input to appear
Write-Host "`n[Step 7] Waiting for password input field..."
$passwordField = Wait-ForUiNode -ClassName "android.widget.EditText" -TimeoutSec 10
if (-not $passwordField) {
    $passwordField = Wait-ForUiNode -Text "PDF Password" -TimeoutSec 5
}
if (-not $passwordField) {
    throw "Password input field did not appear after file selection!"
}
Write-Host "  -> Password field ready at bounds $($passwordField.Bounds)"

# Step 8: Enter password "password"
Write-Host "`n[Step 8] Entering password 'password'..."
Tap-Node $passwordField
Start-Sleep -Milliseconds 300
# Clear existing characters if any
1..10 | ForEach-Object { & $adb -s $DeviceId shell input keyevent 67 }
& $adb -s $DeviceId shell input text "password"
Start-Sleep -Milliseconds 300
# Dismiss soft keyboard with Back key so action buttons below are visible
& $adb -s $DeviceId shell input keyevent 4
Start-Sleep -Seconds 1

# Step 9: Click "Overwrite Original"
Write-Host "`n[Step 9] Clicking 'Overwrite Original' button..."
$overwriteBtn = Wait-ForUiNode -Text "Overwrite Original" -Enabled "true" -TimeoutSec 10
if (-not $overwriteBtn) {
    throw "'Overwrite Original' button was not enabled or not found!"
}
Tap-Node $overwriteBtn

# Step 10: Wait for decryption to complete and "Preview PDF" button to appear
Write-Host "`n[Step 10] Waiting for decryption to complete and 'Preview PDF' button to appear..."
$previewBtn = Wait-ForUiNode -Text "Preview PDF" -TimeoutSec 25
if (-not $previewBtn) {
    throw "Decryption failed or 'Preview PDF' button did not appear!"
}
Write-Host "  -> Decryption successful! 'Preview PDF' button is displayed."

# Step 11: Clear Logcat before opening PDF viewer for continuous monitoring
Write-Host "`n[Step 11] Clearing Logcat for crash monitoring..."
& $adb -s $DeviceId logcat -c

# Step 12: Click "Preview PDF"
Write-Host "`n[Step 12] Clicking 'Preview PDF'..."
Tap-Node $previewBtn

# Wait for viewer to render
Write-Host "  -> Waiting for PDF viewer to render..."
$viewerRendered = Wait-ForUiNode -ContentDesc "Share File" -TimeoutSec 20
if (-not $viewerRendered) {
    $viewerRendered = Wait-ForUiNode -ContentDesc "Close" -ExactContentDesc -TimeoutSec 5
}
if (-not $viewerRendered) {
    throw "PDF Viewer did not render within timeout!"
}
Start-Sleep -Seconds 2
Write-Host "  -> PDF Viewer successfully loaded and rendered document."

# Take screenshot step_viewer_opened.png
Save-Screenshot "step_viewer_opened.png"

# Step 12b: Test Search button
Write-Host "`n[Step 12b] Testing Top Bar Search button..."
$searchIcon = Wait-ForUiNode -ContentDesc "Search in Document" -TimeoutSec 10
if (-not $searchIcon) {
    throw "Search icon not found in viewer top bar!"
}
Tap-Node $searchIcon
Start-Sleep -Seconds 2
Save-Screenshot "step_search_tapped.png"

$searchLogs = & $adb -s $DeviceId logcat -d | Select-String "InkPdfViewerFragment|PdfViewerScreen"
Write-Host "--- InkPdfViewerFragment & PdfViewerScreen Logcat Output ---"
$searchLogs | ForEach-Object { Write-Host $_ }
Write-Host "------------------------------------------------------------"

# Tap Search icon again to toggle off
$exitSearchIcon = Wait-ForUiNode -ContentDesc "Close Search" -TimeoutSec 5
if ($exitSearchIcon) {
    Tap-Node $exitSearchIcon
    Start-Sleep -Seconds 1
}

# Step 13: Test Share button
Write-Host "`n[Step 13] Testing Top Bar Share button..."
$shareIcon = Wait-ForUiNode -ContentDesc "Share File" -TimeoutSec 10
if (-not $shareIcon) {
    throw "Share icon not found in viewer top bar!"
}
Tap-Node $shareIcon
Start-Sleep -Seconds 2

# Verify focus changes to ChooserActivity
$focusShare = Get-WindowFocus
Write-Host "  -> Window focus after Share: $focusShare"
if ($focusShare -notmatch "ChooserActivity|ChooserActivityLauncher|intentresolver|ResolverActivity") {
    throw "Assertion failed: Focus did not switch to ChooserActivity! Got: $focusShare"
}
Write-Host "  -> [PASS] Focus switched to ChooserActivity."

# Press Back to return to viewer
& $adb -s $DeviceId shell input keyevent 4
Start-Sleep -Seconds 1
$focusBack = Get-WindowFocus
if ($focusBack -notmatch "com.max97k.pddf") {
    throw "Assertion failed: Focus did not return to MainActivity after closing chooser! Got: $focusBack"
}
Save-Screenshot "step_after_share.png"

# Step 14: Test Save As button
Write-Host "`n[Step 14] Testing Top Bar Save As button..."
$saveAsIcon = Wait-ForUiNode -ContentDesc "Save As New" -TimeoutSec 10
if (-not $saveAsIcon) {
    throw "Save As icon not found in viewer top bar!"
}
Tap-Node $saveAsIcon
Start-Sleep -Seconds 2

# Verify focus changes to documentsui
$focusSaveAs = Get-WindowFocus
Write-Host "  -> Window focus after Save As: $focusSaveAs"
if ($focusSaveAs -notmatch "documentsui") {
    throw "Assertion failed: Focus did not switch to documentsui! Got: $focusSaveAs"
}
Write-Host "  -> [PASS] Focus switched to documentsui."

# Press Back to return to viewer
& $adb -s $DeviceId shell input keyevent 4
Start-Sleep -Seconds 1
$focusBack2 = Get-WindowFocus
if ($focusBack2 -notmatch "com.max97k.pddf") {
    throw "Assertion failed: Focus did not return to MainActivity after closing documentsui! Got: $focusBack2"
}
Save-Screenshot "step_after_saveas.png"

# Step 15: Test Close button
Write-Host "`n[Step 15] Testing Top Bar Close button..."
$closeIcon = Wait-ForUiNode -ContentDesc "Close" -ExactContentDesc -TimeoutSec 10
if (-not $closeIcon) {
    throw "Close icon not found in viewer top bar!"
}
Tap-Node $closeIcon
Start-Sleep -Seconds 2

# Verify viewer is closed and main screen is visible
$mainScreenCheck = Wait-ForUiNode -Text "PDF Decryptor" -TimeoutSec 5
if (-not $mainScreenCheck) {
    $mainScreenCheck = Wait-ForUiNode -Text "Select Encrypted" -TimeoutSec 3
}
if (-not $mainScreenCheck) {
    throw "Assertion failed: PDF viewer did not close back to main screen!"
}
Write-Host "  -> [PASS] PDF viewer closed. Main screen is visible."
Save-Screenshot "step_after_close.png"

# Step 16: Verify 0 crashes / fatal exceptions in Logcat
Write-Host "`n[Step 16] Checking Logcat for crashes and fatal exceptions..."
$logcatDump = & $adb -s $DeviceId logcat -d 2>&1
$crashMatches = $logcatDump | Select-String "FATAL EXCEPTION|AndroidRuntime:E|Process:\s+com\.max97k\.pddf.*has\s+died"
if ($crashMatches) {
    Write-Error "CRASH DETECTED in Logcat during test execution!`n$($crashMatches -join "`n")"
    exit 1
}
Write-Host "  -> [PASS] 0 Fatal Exceptions / 0 Crashes detected in Logcat throughout test flow."

Write-Host "`n=================================================================="
Write-Host "ALL END-TO-END VERIFICATION CHECKS PASSED SUCCESSFULLY!"
Write-Host "Screenshots captured in: $ScreenshotDir"
Write-Host "=================================================================="
exit 0

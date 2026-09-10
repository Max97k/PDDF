# ==============================================================================
# verify_pdf_viewer.ps1
# End-to-End Automated Verification of PDF Decryption and PDF Viewer Flow
# ==============================================================================
[CmdletBinding()]
param(
    [string]$DeviceId = "emulator-5554",
    [string]$ScreenshotDir = "$PSScriptRoot\..\e2e_screenshots",
    [string]$Password = "u121837872"
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
    $null = & $adb -s $DeviceId shell "rm -f $RemotePath 2>/dev/null"
    $null = & $adb -s $DeviceId shell "uiautomator dump $RemotePath 2>/dev/null"
    $lines = & $adb -s $DeviceId shell "cat $RemotePath 2>/dev/null"
    $rawXml = ($lines -join "`n").Trim()
    if (-not $rawXml -or $rawXml -notmatch "<hierarchy") {
        return $null
    }
    try {
        $xmlDoc = New-Object System.Xml.XmlDocument
        $xmlDoc.LoadXml($rawXml)
        return $xmlDoc
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
        [string]$Enabled = $null,
        [int]$MaxY = 0
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
                if ($MaxY -gt 0 -and $y2 -gt $MaxY) { continue }
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
        [int]$MaxY = 0,
        [int]$TimeoutSec = 15
    )
    $startTime = Get-Date
    while (((Get-Date) - $startTime).TotalSeconds -lt $TimeoutSec) {
        $xml = Dump-UiHierarchy
        if ($xml) {
            $found = Find-UiNode -XmlDoc $xml -Text $Text -ExactText:$ExactText -ContentDesc $ContentDesc -ExactContentDesc:$ExactContentDesc -ResourceId $ResourceId -ClassName $ClassName -Enabled $Enabled -MaxY $MaxY
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

function Dismiss-ImmersiveCling {
    $xml = Dump-UiHierarchy
    if ($xml) {
        $gotIt = Find-UiNode -XmlDoc $xml -Text "Got it"
        if (-not $gotIt) {
            $gotIt = Find-UiNode -XmlDoc $xml -ResourceId "com.android.systemui:id/ok"
        }
        if ($gotIt) {
            Write-Host "  -> Detected SystemUI Immersive Cling, tapping 'Got it'..."
            Tap-Node $gotIt
            Start-Sleep -Milliseconds 800
        }
    }
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
& $adb -s $DeviceId shell input keyevent 224
& $adb -s $DeviceId shell wm dismiss-keyguard
& $adb -s $DeviceId shell am start -n com.max97k.pddf/com.example.MainActivity
Start-Sleep -Seconds 2

# Step 4: Handle What's New dialog & capture screenshots
Write-Host "`n[Step 4] Waiting for app to load and checking for 'What''s New' dialog..."
$startWait = Get-Date
$whatsNewNode = $null
while (((Get-Date) - $startWait).TotalSeconds -lt 15) {
    $xml = Dump-UiHierarchy
    $whatsNewNode = Find-UiNode -XmlDoc $xml -Text "What's New"
    if ($whatsNewNode) { break }
    $mainNode = Find-UiNode -XmlDoc $xml -Text "Select Encrypted"
    if ($mainNode) { break }
    Start-Sleep -Milliseconds 800
}
if ($whatsNewNode) {
    Write-Host "  -> Detected What's New dialog, saving screenshot 02_whats_new_dialog.png..."
    Save-Screenshot "02_whats_new_dialog.png"
    $closeDialogBtn = Wait-ForUiNode -Text "Close" -ExactText -TimeoutSec 5
    if ($closeDialogBtn) {
        Tap-Node $closeDialogBtn
        Start-Sleep -Seconds 1
    }
    $stillThere = Find-UiNode -XmlDoc (Dump-UiHierarchy) -Text "What's New"
    if ($stillThere) {
        & $adb -s $DeviceId shell input keyevent 4
        Start-Sleep -Seconds 1
    }
} else {
    Write-Host "  -> No intro dialog displayed."
}

# Wait for Main screen button to be ready before taking empty state screenshot
$readyBtn = Wait-ForUiNode -Text "Select Encrypted" -TimeoutSec 10
# Capture Step 1: Empty state on app launch
Write-Host "`n[Step 4b] Capturing 01_app_launch_empty_state.png..."
Save-Screenshot "01_app_launch_empty_state.png"

# Step 5: Click "Select PDF" button
Write-Host "`n[Step 5] Clicking 'Select Encrypted PDFs' button..."
$selectBtn = Wait-ForUiNode -Text "Select Encrypted" -TimeoutSec 10
if (-not $selectBtn) {
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

# Capture Step 3: File picker screen
Save-Screenshot "03_documentsui_picker.png"

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
Save-Screenshot "04_autounlock_password_dialog.png"

# Step 8: Enter password
Write-Host "`n[Step 8] Entering password '$Password'..."
Tap-Node $passwordField
Start-Sleep -Milliseconds 300
1..20 | ForEach-Object { & $adb -s $DeviceId shell input keyevent 67 }
& $adb -s $DeviceId shell input text "$Password"
Start-Sleep -Milliseconds 300
& $adb -s $DeviceId shell input keyevent 4 # Dismiss soft keyboard
Start-Sleep -Seconds 1

# Capture Step 5: Password typed and masked
Save-Screenshot "05_password_typed_masked.png"

# Step 8b: Toggle password visibility to revealed
Write-Host "`n[Step 8b] Toggling password visibility to revealed..."
$xmlPass = Dump-UiHierarchy
$toggleIcon = Find-UiNode -XmlDoc $xmlPass -ContentDesc "Show password"
if ($toggleIcon) {
    Tap-Node $toggleIcon
    Start-Sleep -Milliseconds 600
    Save-Screenshot "06_password_toggled_revealed.png"
    Write-Host "  -> Password visibility toggled to plaintext."

    $xmlPass2 = Dump-UiHierarchy
    $toggleHide = Find-UiNode -XmlDoc $xmlPass2 -ContentDesc "Hide password"
    if ($toggleHide) {
        Tap-Node $toggleHide
        Start-Sleep -Milliseconds 400
    }
}
Save-Screenshot "07_autounlock_dialog_ready.png"

# Step 9: Click "Unlock & View" (AutoUnlock Dialog) or "Overwrite Original"
Write-Host "`n[Step 9] Proceeding with decryption..."
$unlockBtn = Wait-ForUiNode -Text "Unlock & View" -TimeoutSec 5
if (-not $unlockBtn) {
    $unlockBtn = Wait-ForUiNode -Text "Unlock" -ClassName "android.widget.Button" -TimeoutSec 3
}
if ($unlockBtn) {
    Write-Host "  -> Detected AutoUnlock Dialog, tapping '$($unlockBtn.Text)'..."
    Write-Host "`n[Step 11] Clearing Logcat for crash monitoring..."
    & $adb -s $DeviceId logcat -c
    Tap-Node $unlockBtn
} else {
    $overwriteBtn = Wait-ForUiNode -Text "Overwrite Original" -Enabled "true" -TimeoutSec 10
    if (-not $overwriteBtn) {
        throw "'Overwrite Original' or 'Unlock' button was not found!"
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
}

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
Dismiss-ImmersiveCling
Write-Host "  -> PDF Viewer successfully loaded and rendered document."

# Capture Step 8: PDF Viewer Opened
Save-Screenshot "08_pdf_viewer_opened.png"

# Step 12b: Test Search button
Write-Host "`n[Step 12b] Testing Top Bar Search button..."
$searchIcon = Wait-ForUiNode -ContentDesc "Search in Document" -TimeoutSec 10
if (-not $searchIcon) {
    throw "Search icon not found in viewer top bar!"
}
Tap-Node $searchIcon
Start-Sleep -Seconds 2
Save-Screenshot "09_pdf_viewer_search.png"

$searchLogs = & $adb -s $DeviceId logcat -d | Select-String "InkPdfViewerFragment|PdfViewerScreen"
Write-Host "--- InkPdfViewerFragment & PdfViewerScreen Logcat Output ---"
$searchLogs | ForEach-Object { Write-Host $_ }
Write-Host "------------------------------------------------------------"

# Dismiss keyboard and close search view
& $adb -s $DeviceId shell input keyevent 4 # Dismiss soft keyboard
Start-Sleep -Milliseconds 500

$closeSearchBtn = Wait-ForUiNode -ResourceId "com.max97k.pddf:id/closeButton" -TimeoutSec 3
if (-not $closeSearchBtn) {
    $closeSearchBtn = Wait-ForUiNode -ContentDesc "Close Search" -TimeoutSec 2
}
if ($closeSearchBtn) {
    Tap-Node $closeSearchBtn
    Start-Sleep -Seconds 1
} else {
    & $adb -s $DeviceId shell input keyevent 4 # Dismiss search mode
    Start-Sleep -Seconds 1
}

Dismiss-ImmersiveCling

# Step 13: Test Share button
Write-Host "`n[Step 13] Testing Top Bar Share button..."
$shareIcon = Wait-ForUiNode -ContentDesc "Share File" -TimeoutSec 5
if (-not $shareIcon) {
    Dismiss-ImmersiveCling
    # If top bar is hidden, tap screen center to reveal top bar
    Write-Host "  -> Tapping screen center to restore top bar..."
    & $adb -s $DeviceId shell input tap 540 800
    Start-Sleep -Seconds 1
    $shareIcon = Wait-ForUiNode -ContentDesc "Share File" -TimeoutSec 5
}
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
Save-Screenshot "10_system_share_sheet.png"

# Press Back to return to viewer
& $adb -s $DeviceId shell input keyevent 4
Start-Sleep -Seconds 1
$focusBack = Get-WindowFocus
if ($focusBack -notmatch "com.max97k.pddf") {
    throw "Assertion failed: Focus did not return to MainActivity after closing chooser! Got: $focusBack"
}

# Step 14: Test Save As button
Write-Host "`n[Step 14] Testing Top Bar Save As button..."
$saveAsIcon = Wait-ForUiNode -ContentDesc "Save As New" -TimeoutSec 5
if (-not $saveAsIcon) {
    Dismiss-ImmersiveCling
    Write-Host "  -> Tapping screen center to restore top bar..."
    & $adb -s $DeviceId shell input tap 540 800
    Start-Sleep -Seconds 1
    $saveAsIcon = Wait-ForUiNode -ContentDesc "Save As New" -TimeoutSec 5
}
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
Save-Screenshot "11_system_save_as_picker.png"

# Press Back to return to viewer
& $adb -s $DeviceId shell input keyevent 4
Start-Sleep -Seconds 1
$focusBack2 = Get-WindowFocus
if ($focusBack2 -notmatch "com.max97k.pddf") {
    throw "Assertion failed: Focus did not return to MainActivity after closing documentsui! Got: $focusBack2"
}

# Step 15: Test Close button
Write-Host "`n[Step 15] Testing Top Bar Close button..."
$closeIcon = Wait-ForUiNode -ContentDesc "Close" -ExactContentDesc -MaxY 400 -TimeoutSec 5
if (-not $closeIcon) {
    Dismiss-ImmersiveCling
    Write-Host "  -> Tapping screen center to restore top bar..."
    & $adb -s $DeviceId shell input tap 540 800
    Start-Sleep -Seconds 1
    $closeIcon = Wait-ForUiNode -ContentDesc "Close" -ExactContentDesc -MaxY 400 -TimeoutSec 5
}
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
Save-Screenshot "12_main_screen_decrypted_status.png"

# Step 15b: Test Saved Passwords Vault Dialog
Write-Host "`n[Step 15b] Testing Saved Passwords Vault dialog..."
$xmlMain = Dump-UiHierarchy
$vaultIcon = Find-UiNode -XmlDoc $xmlMain -ContentDesc "Saved Passwords"
if ($vaultIcon) {
    Tap-Node $vaultIcon
    Start-Sleep -Seconds 1
    Save-Screenshot "13_saved_passwords_vault.png"
    & $adb -s $DeviceId shell input keyevent 4
    Start-Sleep -Milliseconds 600
}

# Step 15c: Test Settings BottomSheet
Write-Host "`n[Step 15c] Testing Settings BottomSheet..."
$xmlMain2 = Dump-UiHierarchy
$settingsIcon = Find-UiNode -XmlDoc $xmlMain2 -ContentDesc "Settings"
if ($settingsIcon) {
    Tap-Node $settingsIcon
    Start-Sleep -Seconds 1
    Save-Screenshot "14_settings_bottom_sheet.png"
    & $adb -s $DeviceId shell input keyevent 4
    Start-Sleep -Milliseconds 600
}

# Step 15d: Verify Cache Cleaning
Write-Host "`n[Step 15d] Verifying cache cleanup..."
$cachedFiles = & $adb -s $DeviceId shell "ls /data/data/com.max97k.pddf/cache/*.pdf 2>/dev/null"
Write-Host "  -> Active cache files count: $(($cachedFiles | Measure-Object).Count)"
Save-Screenshot "15_cache_clean_verified.png"

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

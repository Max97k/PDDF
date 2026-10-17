# PDDF 依賴項升級與 API 36/37 現代化交接文件 (Handover Document)

**目前分支 (Current Branch)**: `audit/api37-dependency-optimization`  
**目標系統等級 (Target Platform)**: Min SDK 36 (Android 16), Target SDK 37, Compile SDK 37  
**開發環境規範 (Environment)**: Windows / PowerShell / Gradle 9.8.1 / AGP 9.4.1 / Java 24+ (JBR)  
**更新時間戳記 (Timestamp)**: 2026-10-13 21:15

---

## 一、 目前已完成事項 (Completed Progress)

本階段已完成建置工具鏈升級、API 36/37 現代化重構、排版與選單背景色差清查修復，以及測試驗證。

### 1. 選單背景色差與 UI 元件全面修復
- ✅ **`SettingsBottomSheet` 選單 ListItem 背景色差消除**：
  - **原因分析**：Material 3 `ModalBottomSheet` 容器背景色為 `surfaceContainerLow`，而內部的 `ListItem` 預設容器顏色為 `surface`。這導致選單內的每列選項（外觀主題、截圖保護）文字背後會繪製一整塊不透明的 `surface` 矩形色塊，產生明顯的色差與方塊痕跡。
  - **修復**：顯式傳入 `colors = ListItemDefaults.colors(containerColor = Color.Transparent)`，使選單選項無縫承襲 `ModalBottomSheet` 的容器背景色。
- ✅ **`PdfViewerScreen` 頂部重複 Padding 消除**：調整 `PddfNavHost` 為 Edge-to-Edge 滿版，完全消除標題列上方原本多餘的 48dp 黑色空欄。
- ✅ **`PrivacyShieldOverlay` 主題配色語意化**：將原本硬編碼的 Hex 色彩替換為 `MaterialTheme.colorScheme`，確保在日間與夜間模式下均能正常響應。

### 2. 建置工具鏈與 Version Catalog 現代化升級
- **Gradle Wrapper 升級**：`gradle-wrapper.properties` 升級為 **Gradle 9.8.1** (SHA256 驗證無誤)。
- **Android Gradle Plugin 升級**：`agp` 版本由 `9.1.1` 升級至 **`9.4.1`**。
- **Kotlin 序列化插件引入**：配置 `kotlin-serialization` (`2.0.21`) 與 `kotlinx-serialization-json` (`1.7.3`)，達成 Compose Navigation 2.10.2 強型別路由。
- **徹底移除 `androidx.documentfile`**：重構為原生 `DocumentsContract` 直連後，完全移除 `documentfile` 依賴。

### 3. 已升級並驗證通過的穩定版依賴項清單
- **Gradle**: `9.3.1` ➔ **`9.8.1`**
- **AGP**: `9.1.1` ➔ **`9.4.1`**
- **composeBom**: `2024.09.00` ➔ **`2026.09.00`**
- **datastore**: `1.1.1` ➔ **`1.2.1`**
- **kotlinxCollectionsImmutable**: `0.3.7` ➔ **`0.4.0`**
- **coreKtx**: `1.15.0` ➔ **`1.19.1`**
- **fragmentKtx**: `1.8.8` ➔ **`1.9.1`**
- **workRuntimeKtx**: `2.9.1` ➔ **`2.12.0`**
- **room**: `2.7.0` ➔ **`2.8.5`**
- **lifecycle**: `2.8.7` ➔ **`2.11.0`**
- **activityCompose**: `1.10.1` ➔ **`1.13.0`**
- **navigationCompose**: `2.8.7` ➔ **`2.10.2`**

### 4. v0.9.5 深度審查與升級完成事項 (Version 0.9.5 Release)
- ✅ **全域 `FLAG_SECURE` 防截圖架構統一**：修復 `ReEncryptPasswordDialog` 關閉時誤清除主視窗截圖保護問題，強化無障礙與密碼可見性切換。
- ✅ **DoD 5220.22-M 遞迴快取粉碎**：升級 `FileUtils.cleanUpDecryptedCache` 為遞迴清理，徹底粉碎 `cacheDir/decrypted/` 下所有解密暫存檔。
- ✅ **執行期異常修復**：修正 `MainViewModel.saveAllAttachments` 強制轉型崩潰（`ClassCastException`），以及頁面擷取分享時之 `FileUriExposedException`（改採安全 `FileProvider` URI）。
- ✅ **記憶體 OOM 串流最佳化**：PDF 元數據解析改用串流載入與動態記憶體分流，杜絕大檔記憶體溢出。
- ✅ **生命週期與資源洩漏修復**：快取 `InkPdfViewerFragment` 反射欄位並於銷毀時註銷 Handler；`PdfViewerViewModel` 串接 Job 取消；`PdfPageOverviewDialog` 安全釋放底層渲染器。
- ✅ **業務功能與在地化完善**：實作 `SuggestDocumentTitleUseCase`、恢復 Settings 7 國語言切換選單、密碼庫命中次數自動累加，並補齊繁體中文與簡體中文在地化資源。

### 5. 目前測試驗證狀態
- **JVM 單元測試**：`BUILD SUCCESSFUL`，全數測試套件 100% 通過（0 Failures / 0 Errors / 0 Skipped）。
- **Android Lint 靜態檢查**：`.\gradlew.bat lint` 0 Errors 通過。
- **覆蓋率建置**：`.\gradlew.bat :app:jacocoTestReport` 建置正常。
- **發布建置**：`.\gradlew.bat :app:assembleDebug` 建置正常。

---

## 二、 常用驗證指令備忘錄 (Command Reference)

```powershell
# 設定 JDK 並執行單元與回歸測試 (必須 100% 通過)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest

# 執行模擬器 E2E 自動化驗證
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify_pdf_viewer.ps1 -DeviceId emulator-5554 -ScreenshotDir "e2e_screenshots"

# 查看 Git 修改狀態
git status
git diff
```

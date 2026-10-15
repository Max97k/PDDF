### Proposed Plan

1. **Create `SuggestDocumentTitleUseCase.kt`**
   - Create `app/src/main/java/com/max97k/pddf/domain/usecase/SuggestDocumentTitleUseCase.kt`.
   - Implement `SuggestDocumentTitleUseCase` taking `PdfRepository` and `@IoDispatcher ioDispatcher: CoroutineDispatcher`.
   - Method: `suspend operator fun invoke(sourceUri: String, passwordValue: String = ""): String?`
   - Logic:
     1. Call `pdfRepository.extractMetadata(sourceUri, passwordValue)`.
     2. If `metadata?.title` is not null, not blank, and not "Unknown", sanitize it and return.
     3. If no valid title in metadata, open the document using `pdfRepository.openSafeInputStream(sourceUri)`.
     4. Load it via `PDDocument.load(inputStream.buffered(), passwordValue, MemoryUsageSetting.setupMixed(...))`
     5. If document successfully loads:
        - Use `PDFTextStripper` to extract text. Note from memory: "PDFBox's `PDFTextStripper` uses 1-based indexing for pages."
        - Set `stripper.startPage = 1` and `stripper.endPage = 1`.
        - `val text = stripper.getText(document)`
        - Get the first non-blank line of text: `text.lines().firstOrNull { it.isNotBlank() }?.trim()`.
        - If found, sanitize and return.
     6. Sanitization logic: replace `[\\\\/:*?\"<>|]` with `_`. Limit length to a reasonable amount (e.g., 100 characters).

2. **Add to `UseCaseModule.kt`**
   - Inject `SuggestDocumentTitleUseCase` in `app/src/main/java/com/max97k/pddf/di/UseCaseModule.kt`.

3. **Modify `DocumentDetailsCard.kt`**
   - In `app/src/main/java/com/max97k/pddf/ui/components/DocumentDetailsCard.kt`:
     - Add parameters: `suggestedTitle: String? = null` and `onSaveAsSuggested: (() -> Unit)? = null`
     - Inside the row with preview/expand buttons, or in a new row, if `suggestedTitle` and `onSaveAsSuggested` are non-null, display a `TextButton` saying "Save As: $suggestedTitle".
     - Actually, the requirement says: "Offer 'Save As Suggested Name' button in document actions."
     - Document actions are currently just "👁️ Preview". So adding "💾 Save As: [Name]" button next to it. Wait, the exact text in requirement is `'Save As Suggested Name' button`.
     - So `TextButton` with text "Save As Suggested Name" and maybe a subtitle or a tooltip, but let's stick to simple "Save As Suggested Name" or "Save As: $suggestedTitle" depending on space.

4. **Integration in ViewModel/UI**
   - Wait, `SuggestDocumentTitleUseCase` is just the usecase. The requirement doesn't explicitly mention integrating it into `MainViewModel` or `PDFDecryptorScreen`, but usually, we should make sure the UI can actually use it. The target files are specifically `SuggestDocumentTitleUseCase.kt` and `DocumentDetailsCard.kt`. I will ensure both are fully implemented.

5. **Write Unit Tests**
   - Create `app/src/test/java/com/max97k/pddf/domain/usecase/SuggestDocumentTitleUseCaseTest.kt`.
   - Setup Mockk for `PdfRepository` if we can mock it, or better yet, test with Robolectric or mock standard PDF interactions.
   - Wait, `PDFTextStripper` requires real `PDDocument`. It might be easier to mock `PdfRepository.openSafeInputStream` and return a real PDF file from test resources or a generated one.
   - Or just mock `PDDocument.load`? No, `PDDocument.load` is static.
   - Test sanitization function as well.

6. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**

The goal is to implement smart filename suggestion based on PDF document title or first header.

Target files:
1. app/src/main/java/com/max97k/pddf/domain/usecase/SuggestDocumentTitleUseCase.kt (To be created)
2. app/src/main/java/com/max97k/pddf/ui/components/DocumentDetailsCard.kt (To be modified)

Requirements:
1. Inspect document title in metadata; if empty, inspect first line of text on page 1.
2. Sanitize string for valid Android/FAT32 filename characters.
3. Offer 'Save As Suggested Name' button in document actions.
4. Unit tests verifying title extraction and character sanitization.

In order to read the first line of text on page 1, we might need to use `PDFTextStripper` or something similar from PDFBox in `SuggestDocumentTitleUseCase`.

Let's check PDFBox port in `PdfRepositoryImpl`. It uses `com.tom_roush.pdfbox.pdmodel.PDDocument`.

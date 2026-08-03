# Privacy Policy for PDF Decryptor (PDDF)

**Last updated: August 3, 2026**

## 1. Introduction
PDF Decryptor ("we," "our," or "us") respects your privacy and is committed to protecting your personal information. This Privacy Policy explains how our Android application (`com.max97k.pddf`) handles your data.

## 2. Zero Data Collection & Local Processing
**We do not collect, upload, transmit, or share any of your personal data, PDF files, or passwords to external servers.**

* **Local Decryption**: All PDF password decryption operations are processed **100% locally** on your device using Apache PDFBox Android. Your PDF files never leave your device.
* **Local Password Storage**: Saved password profiles are encrypted and stored locally in an isolated Android Room database on your device.
* **No Analytics or Tracking**: We do not use any third-party tracking, telemetry, or analytics SDKs (e.g., Firebase Analytics, Google Analytics).
* **No Cloud Backups of Passwords**: Database and sensitive preference files are explicitly excluded from Android cloud backups and device transfers via `data_extraction_rules.xml` and `backup_rules.xml`.

## 3. Permissions Requested
PDF Decryptor requests only minimal, necessary system permissions:
* `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (or Storage Access Framework / DocumentFile): Used solely to read user-selected PDF files and write decrypted copies to your designated storage location.

## 4. Third-Party Services & Ads
* **No Ads**: PDF Decryptor is completely ad-free.
* **No Third-Party SDK Data Sharing**: No user data is shared with any advertising or marketing networks.

## 5. Security
Your password profiles are stored locally within the application's private storage area, protected by Android's sandbox security model.

## 6. Children's Privacy
Our application does not collect any personal data from anyone, including children under the age of 13.

## 7. Changes to This Privacy Policy
We may update our Privacy Policy from time to time. Any updates will be posted on this page with a revised "Last updated" date.

## 8. Contact Us
If you have any questions or feedback regarding this Privacy Policy, please reach out via GitHub Issues:
https://github.com/Max97k/PDDF/issues

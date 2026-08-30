# Privacy Policy for PDF Decryptor (PDDF)

**Last updated: August 30, 2026**

## 1. Introduction
PDF Decryptor ("PDDF", "we," "our," or "us") respects your privacy and is committed to protecting your personal information. This Privacy Policy explains how our Android application (`com.max97k.pddf`) handles your data.

## 2. Zero Data Collection & Local Processing
**We do not collect, upload, transmit, or share any of your personal data, PDF files, or passwords to external servers.**

* **No Internet Access**: The application does not declare or request the Android `INTERNET` permission. It is completely incapable of transmitting data over the network.
* **100% Local Decryption**: All PDF password decryption and file processing operations run **strictly locally** on your device using Apache PDFBox Android. Your PDF files never leave your device.
* **Local Password Storage**: Saved password profiles are encrypted and stored locally in an isolated Android Room SQLite database on your device.
* **No Analytics or Tracking**: We do not use any third-party tracking, telemetry, crash reporting, or analytics SDKs (e.g., Firebase Analytics, Google Analytics).
* **No Cloud Backups of Passwords**: Database and sensitive preference files are explicitly excluded from Android cloud backups (Google Drive) and device migration transfers via `data_extraction_rules.xml` and `backup_rules.xml`.

## 3. Permissions Requested
PDF Decryptor requests only minimal, necessary system permissions:
* Storage Access Framework (SAF) / System Document Picker: Used solely to read user-selected PDF files and save decrypted copies to your designated storage location.

## 4. Third-Party Services & Ads
* **No Ads**: PDF Decryptor is completely ad-free.
* **No Third-Party SDK Data Sharing**: No user data is shared with any advertising or marketing networks.

## 5. Security
Your password profiles are stored locally within the application's private sandbox storage area and can optionally be protected by Android Biometric Authentication (Fingerprint / Face Unlock).

## 6. Children's Privacy
Our application does not collect or request any personal data from anyone, including children under the age of 13.

## 7. Changes to This Privacy Policy
We may update our Privacy Policy from time to time. Any updates will be posted on this page with a revised "Last updated" date.

## 8. Contact Us
If you have any questions or feedback regarding this Privacy Policy, please reach out via GitHub Issues:
https://github.com/Max97k/PDDF/issues

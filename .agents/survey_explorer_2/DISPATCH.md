# DISPATCH — Survey Explorer 2

## Identity
- Role: Security & Android 15 Surveyor
- Working Directory: c:\Users\b\PDDF\.agents\survey_explorer_2
- Parent Conversation ID: 408f3427-07df-48e6-a3ce-0638f3e78ce2

## Task
Read c:\Users\b\PDDF\.agents\ORIGINAL_REQUEST.md and c:\Users\b\PDDF\AGENTS.md.
Investigate the codebase at c:\Users\b\PDDF to map:
1. Current security & crypto implementations vs R2 requirements:
   - Hardware-backed BiometricPrompt (CryptoObject)
   - StrongBox Keymaster detection
   - DoD temporary file shredding
   - Sensitive password memory zeroization (CharArray / byte array wiping)
   - AndroidX WorkManager background batch decryption workers with ongoing progress notifications
2. Android 15 & Form Factor status vs R3 requirements:
   - Target SDK 35, predictive back gesture transitions
   - Edge-to-edge window insets with IME keyboard padding
   - 16KB ELF page size compliance (native libs / PDFBox / dependencies)
   - Tablet dual-pane layout & foldable tabletop mode awareness
3. Threading / Main Thread I/O audit (check Dispatchers.IO isolation).

Write your findings and evidence chain to c:\Users\b\PDDF\.agents\survey_explorer_2\handoff.md and notify parent when done.

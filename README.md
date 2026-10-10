# Card Scanner (Android)

A business card scanner in the style of Samsung's old SnapBizz. Scan the front and back of a card. The phone reads the text offline and fills in a contact, which you check and save to the phone's Contacts.

## Features
- **Front and back scanning** with Google's document scanner, which auto-detects the card edges, crops and straightens it
- **Offline text reading** with Google ML Kit. It's free, with no per-scan cost, and no card data leaves the phone
- **Smart field detection tuned for Indian cards:** name, designation, company, mobile (+91), STD landlines (0161-…, +91 80 …), fax, email, website, address with PIN code, **GSTIN** (with OCR error correction), plus foreign numbers
- **Edit screen:** tap any line of card text to put it in the right field
- **Save to Contacts** through the phone's own contact screen, so it goes to your Google account and syncs. The card image becomes the contact photo, and GSTIN and notes go into the contact's notes
- **History:** every scanned card stays in the app, with search by name, company, city or part of a number
- **Duplicate warning** if you scan someone whose mobile or email you already have
- **Quick actions:** Call, WhatsApp, Email, Map
- **Export:** all cards to Excel (CSV) or vCard (.vcf), saved anywhere, including OneDrive
- **No permissions needed**

## Project layout
```
app/src/main/java/com/bothrametals/cardscanner/
  parser/CardParser.kt    ← the logic that turns card text into fields (pure Kotlin, unit tested)
  parser/CardFields.kt    ← the contact data model
  util/OcrEngine.kt       ← ML Kit text recognition
  util/ContactSaver.kt    ← pre-filled "New contact" screen
  util/Exporter.kt        ← CSV / vCard export
  data/                   ← stores cards as JSON + images in the app's private storage
  ui/                     ← Jetpack Compose screens (Home, Review)
  MainActivity.kt         ← scanner / gallery / contacts launchers
app/src/test/…/CardParserTest.kt ← 10 sample cards the reader must parse correctly
build-apk-workflow.yml           ← GitHub Actions recipe that builds the APK for free
                                   (on GitHub it goes in .github/workflows/)
```

## Tech
Kotlin 2.0, Jetpack Compose (Material 3), AGP 8.7, compileSdk 35, minSdk 26 (Android 8.0+).
ML Kit Text Recognition 16.0.1 (bundled model) and ML Kit Document Scanner 16.0.0-beta1.

## Build
- Android Studio: File → Open → this folder → Run ▶ (or Build → Build APK(s))
- Command line: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Tests: `./gradlew testDebugUnitTest`

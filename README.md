# Card Scanner (Android)

A business card scanner in the style of Samsung's old SnapBizz. Scan the front and back of a card. The phone reads the text offline and fills in a contact, which you check and save to the phone's Contacts.

## Features
- **Front and back scanning** with Google's document scanner, which auto-detects the card edges, crops and straightens it
- **Free AI reading** with Google Gemini on its free tier (no billing, so no charges). It handles stylish fonts, logos and unusual layouts, and rotates across several free models for more daily capacity
- **Offline backup reader** with Google ML Kit. It's used automatically when there's no internet or the free AI limit is reached. Those cards can be re-read with AI later in one tap
- **Free cross-checks:** email typo fixes, name vs email, website vs email domain, GSTIN check digit. Doubtful fields are outlined in orange
- **Team setup:** copy the AI key to a colleague's phone with a QR code. "Scanned by" is recorded on every card
- **Smart field detection tuned for Indian cards:** name, designation, company, mobile (+91), STD landlines (0161-…, +91 80 …), fax, email, website, address with PIN code, **GSTIN** (with OCR error correction), plus foreign numbers
- **Edit screen:** tap any line of card text to put it in the right field
- **Save to Contacts** through the phone's own contact screen, so it goes to your Google account and syncs. The card image becomes the contact photo, and GSTIN and notes go into the contact's notes
- **History:** every scanned card stays in the app, with search by name, company, city or part of a number
- **Duplicate warning** if you scan someone whose mobile or email you already have
- **Quick actions:** Call, WhatsApp, Email, Map
- **Export:** all cards to Excel (CSV) or vCard (.vcf), saved anywhere, including OneDrive
- **Company login and backup** (Firebase free Spark plan): Google or email + password login; shared company card list plus private cards; admin sees everything and manages the team; cards and photos restored on a new phone. Switches on once `app/google-services.json` is added (see "SETUP LOGIN AND BACKUP (Firebase).md")
- **Events:** group cards by exhibition or trip and share an event with the colleagues who attend. "Scanning into" strip, past events, per-event Excel export, who-scanned-how-many
- **Bulk import from Gallery:** many photos at once, ordered by time taken; back sides auto-paired; Stop button; summary with possible-duplicate badges
- **What they do + search keywords:** from AI (or offline vocabulary), editable; saved in contact Notes (+ Department), Excel, vCard CATEGORIES
- **Address split for Contacts:** street / city / state / PIN / country (Indian addresses), so Google/Samsung Contacts show it
- **Duplicate check:** same mobile / email / name + company → Open existing, Keep both or Discard
- **Gallery album "Card Scanner":** front/back photos saved to Pictures/Card Scanner, named after person and company
- **Sub-groups inside an event** (admins manage): AI suggests from what the company does, user confirms; home filter chips; batch "sort unsorted cards with AI" (text only, 30 cards per request); in contact notes, Excel, vCard
- **QR codes on cards** (ML Kit barcode scanning, offline): vCard / MeCard / BizCard combined with the printed details (QR wins, differences orange), website, WhatsApp, .vcf links downloaded, social / map / UPI links to other text
- **Note from their website:** background queue reads home + About + Products pages on the phone, free Gemini writes a 2–3 line note + extra keywords (falls back to Gemini's URL tool for script-built sites); "From website:" in contact notes and Excel
- **Friendly design:** welcome pages on first launch, drawn logo and illustrations (no image files), home screen with counts (cards / last 7 days / not in contacts, tap to filter), date-grouped list with labels and a call button, Scan + Gallery buttons, details grouped into sections with round Call / WhatsApp / Email / Map buttons, steel-blue + copper theme with dark mode
- **Permissions:** internet only (plus storage on Android 8–9 for the Gallery album)

## Project layout
```
app/src/main/java/com/bothrametals/cardscanner/
  parser/CardParser.kt    ← the logic that turns card text into fields (pure Kotlin, unit tested)
  parser/CardFields.kt    ← the contact data model
  parser/CrossCheck.kt    ← free double-checks (email typos, name/email, GSTIN check digit)
  ai/GeminiProtocol.kt    ← Gemini request/response + the card-reading instructions (prompt)
  ai/GeminiClient.kt      ← HTTP call with free-model fallback and retries
  ai/AiPostProcess.kt     ← cleans the AI answer into app fields
  data/AppSettings.kt     ← AI key, your name, model list, sync state
  cloud/CloudService.kt   ← Firebase login, team (admins/staff), card backup
  cloud/CloudMapper.kt    ← card ↔ cloud format, merge rules, permissions (unit tested)
  util/GallerySaver.kt    ← copies photos into the Gallery album "Card Scanner"
  ui/AuthScreens.kt       ← sign in, verify email, company setup, waiting for access
  ui/TeamScreen.kt        ← admin: add / remove staff
  ui/EventScreens.kt      ← event strip, new event, manage event, duplicate dialog
  data/EventGroup.kt, EventStore.kt, Duplicates.kt ← events and duplicate finder (unit tested)
  data/BulkPairing.kt     ← bulk import: which photo is the back of which card (unit tested)
  parser/AddressSplitter.kt ← one-line address → street/city/state/PIN/country (unit tested)
  parser/KeywordExtractor.kt ← offline "what they do" line + product/service keywords (unit tested)
  ui/Graphics.kt          ← logo, welcome illustrations, avatars, section cards (drawn in code)
  ui/OnboardingScreen.kt  ← three welcome pages shown once
  util/DateGroups.kt      ← Today / Yesterday / Last 7 days / month headings (unit tested)
  data/SubGroups.kt       ← sub-groups inside an event: names, AI-name matching, keyword guess (unit tested)
  parser/QrParser.kt, QrMerge.kt ← what a card's QR code holds, and combining it with the printed card (unit tested)
  util/QrReader.kt        ← ML Kit QR detection on card photos
  web/HtmlText.kt, WebReader.kt ← company website → readable text for the AI note (unit tested)
  util/OcrEngine.kt       ← ML Kit text recognition
  util/ContactSaver.kt    ← pre-filled "New contact" screen
  util/Exporter.kt        ← CSV / vCard export
  data/                   ← stores cards as JSON + images in the app's private storage
  ui/                     ← Jetpack Compose screens (Home, Review)
  MainActivity.kt         ← scanner / gallery / contacts launchers
app/src/test/…/CardParserTest.kt ← 10 sample cards the reader must parse correctly
app/src/test/…/AiReaderTest.kt   ← 14 tests: AI request/response, quota fallback, cross-checks
app/src/test/…/CloudMapperTest.kt, GalleryNamesTest.kt ← sync merge, permissions, photo names
app/cardscanner.keystore         ← fixed signing key so updates install over the old app
firestore.rules                  ← database security rules (paste into Firebase console)
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

# Card Scanner: how to get it on your phone

The **BizCardScanner** folder holds the complete app source code. To install it on a phone, you first turn it into an installable **APK file**. There are two ways to do that. **Option A is easiest** because you install nothing on your PC.

---

## Option A: Build for free on GitHub (no software to install, about 15 minutes)

1. **Create a free GitHub account** at https://github.com/signup. Skip this if you already have one.
2. Click **+** (top right) → **New repository**.
   - Name: `card-scanner`
   - Choose **Private**
   - Click **Create repository**
3. On the next page, click the link **"uploading an existing file"**.
4. Open the **BizCardScanner** folder in Windows Explorer, select **everything inside it** (Ctrl+A), including the `gradle` folder, and drag it onto the GitHub page.
   - Wait until all files finish uploading, then click **Commit changes**.
5. Add the build recipe, which takes one minute:
   - Click the **Actions** tab → **set up a workflow yourself**.
   - Delete the sample text in the editor.
   - Open `build-apk-workflow.yml` from the BizCardScanner folder in Notepad, copy all of it, and paste it into the GitHub editor.
   - Click **Commit changes…** → **Commit changes**.
   - The build named **"Build APK"** now starts on its own in the **Actions** tab.
6. Wait about 6–10 minutes for the green tick ✅. Click the finished run, scroll down to **Artifacts**, and download **CardScanner-APK**. It's a zip containing `app-debug.apk`.
7. Send `app-debug.apk` to your phone, for example on WhatsApp to yourself, by email, or through OneDrive. Then do **Installing on the phone** below.

> Every time you upload changed code, GitHub builds a fresh APK automatically. Private repositories get 2,000 free build minutes a month, which is far more than you need.

---

## Option B: Build on your PC with Android Studio

1. Download and install **Android Studio** (free) from https://developer.android.com/studio. Accept the default options. The first start downloads about 1–2 GB.
2. **File → Open** → select the **BizCardScanner** folder → **Trust project**.
3. Wait for "Gradle sync" to finish at the bottom of the window. The first time takes 5–10 minutes.
4. To install directly on a phone connected by USB:
   - On the phone, go to Settings → About phone and tap **Build number** 7 times to turn on Developer options.
   - Go to Settings → Developer options and turn on **USB debugging**.
   - Connect the phone, choose it in the device list at the top of Android Studio, and press the green **Run ▶** button.
5. Or, to create an APK file to share: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, then click **locate**. The file is `app\build\outputs\apk\debug\app-debug.apk`.

---

## Installing on the phone

1. Open the `app-debug.apk` file on the phone.
2. Android asks you to allow installing from this source (WhatsApp, Files, Chrome…). Tap **Settings**, turn on **Allow from this source**, and go back.
3. Tap **Install**. If Play Protect shows a warning about an unknown app, tap **More details → Install anyway**. It shows this warning because the app isn't from the Play Store.
4. You can share the same APK with family or staff phones.

**Needs:** Android 8.0 or newer with Google Play services, which almost every Android phone in India has. The first time you open the scanner, Google downloads the scanner module once, so keep internet on for that first scan. After that, card reading works offline.

---

## Using the app

1. Tap **Scan card**. Hold the phone over the card on a plain, contrasting surface, and it captures automatically.
2. For the **back side**, tap **+** inside the scanner, scan the back, then tap **Done**. You can also add the back later with **Add back side** on the details screen.
3. Check the details. If something landed in the wrong field, scroll down to **Text found on card** and tap the correct line to choose where it goes.
4. Add **My notes**, for example "Met at Aluminium expo, needs 20 MT ADC12 monthly". The notes are saved inside the contact too.
5. Tap **Save to Contacts**, then **Save** on the phone's contact screen. The contact goes to your Google account, so it shows on all your devices.

**To store the data in this OneDrive folder:** in the app, open the **⋮ menu → Save all to Excel (CSV)…**, choose **OneDrive** in the file picker, and go to *KYC → Claude → SnapBizz*. The OneDrive app must be installed on the phone. The CSV opens in Excel with one row per card: name, designation, company, mobiles, phones, email, website, address, GSTIN, notes and scan date.

Other menu options:
- **Share Excel (CSV)** sends the file on WhatsApp or email
- **Share all as vCard** imports every card into another phone or Outlook in one go
- **Pick card photos from gallery** reads cards you photographed earlier

---

## Good to know
- The card photos and details are stored **only inside the app on the phone**. If you uninstall the app, the history is deleted, but contacts already saved to Contacts stay. Export to Excel now and then as a backup.
- Text reading accuracy is very good on printed cards. Very fancy fonts, glossy reflections or cards in Hindi may need a quick correction on the edit screen.
- The app is signed for in-house use. To publish it on the Play Store later, create a proper signing key in Android Studio (Build → Generate Signed App Bundle).
- An iPhone version is possible later. It needs a Mac and an Apple developer account (US$99, about ₹8,500 a year).

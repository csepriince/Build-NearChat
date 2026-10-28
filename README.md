# NearChat (MVP: Bluetooth chat + file transfer)

Native Android (Kotlin + Jetpack Compose + Material 3). No internet, no accounts, no server.

## Kya working hai (code mein)
- Real Bluetooth discovery (Classic RFCOMM), paired + nearby devices
- Connection request with Accept/Reject on the other phone
- ECDH (P-256) key exchange + AES-256-GCM on every message/file chunk; 6-digit security code on both phones (match = no man-in-the-middle)
- WhatsApp jaisa chat-list dashboard; purani chats phone mein save, kisi bhi message/chat ko long-press karke delete
- Photo/video thumbnails chat mein, tap karke full-screen preview
- Aapka logo: app icon + app ke andar
- Text chat; delivery tick (double tick) only after the other phone sends an ACK
- File transfer: multi-select, preview + size, receiver Accept/Reject, progress %, cancel, retry, disconnect handling
- Light/Dark theme, animations (logo reveal, radar scan, bubbles, attachment menu, progress)
- Permissions for Android 6 to 15+ with Hinglish rationale

## Abhi MVP mein NAHI hai
- Wi-Fi Direct (phase 2: WifiP2pManager + TCP sockets; same Conn/protocol layer reuse hoga)
- Foreground service (app background mein jaye to Android connection band kar sakta hai)
- Ye code is chat mein compile/test nahi hua. Pehli build par chhoti compile errors aa sakti hain; error paste karoge to fix kar dunga.

## Build (Android Studio Ladybug ya naya)
1. Android Studio > Open > `NearChat` folder. Gradle sync hone dein (internet chahiye, sirf build ke liye).
2. Phone: Settings > About > Build number 7 baar tap > Developer options > USB debugging ON.
3. Run (green play) ya **Build > Build Bundle(s)/APK(s) > Build APK(s)**.
   Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Two-phone testing
1. Dono phones par APK install karein, NearChat kholein, permission Allow karein.
2. Phone B: "Make my phone visible (5 min)".
3. Phone A: Find Nearby Devices > phone B ke naam par Connect. Android pairing popup aaye to dono par OK.
4. Phone B par Connection request aayegi: dono par **security code same** hona chahiye, phir Accept.
5. Text bhejein. Double tick sirf tab aata hai jab B ne receive karke ACK diya.
6. Attach > Photos/PDF/Any file > select > Send. B par Accept dabayein; progress dikhega.
   Received files: `Android/data/com.nearchat.app/files/Download/NearChat/`
7. Test: B ka Bluetooth band karo (Connection lost banner + Reconnect), transfer beech mein Cancel, badi file (50 MB+; Bluetooth slow hai, ~1-2 Mbps real speed).
Emulator par Bluetooth nahi chalta; sirf real phones.

## Signed AAB (Play Store)
1. Android Studio > Build > Generate Signed Bundle/APK > Android App Bundle > Create new keystore. **Keystore + password safe rakhein, kho diya to app update nahi hoga.**
2. Ya command line ke liye root mein `keystore.properties` banayein:
   storeFile=../nearchat.jks / storePassword=... / keyAlias=... / keyPassword=...
   phir Build > Generate Signed Bundle (release config use hogi). Output: `app/release/app-release.aab`
3. Play Console: new app, upload AAB, Play App Signing ON.
4. Zaroori: Privacy Policy URL (draft: PRIVACY.md host karein), Data safety form ("no data collected, no data shared"), content rating, target API Play ki current requirement ke hisaab se `targetSdk` update karein.
5. Naye personal developer accounts ko production se pehle closed testing (12+ testers, 14 din) chahiye ho sakti hai; Play Console ki current rules check karein.
6. Permission declaration: BLUETOOTH_SCAN `neverForLocation` hai, Location 11 ya kam par hi maangi jaati hai.

## Honest limits
- Discovery sab nearby Bluetooth devices dikhata hai (headphones bhi), sirf NearChat wale nahi filter hote. Connect fail ho to wo NearChat nahi chala raha.
- Key exchange authenticated nahi hai; MITM se bachne ke liye security code compare karna zaroori hai.
- Bluetooth Classic range ~10 m, speed low. Badi videos ke liye Wi-Fi Direct phase 2.

## Android Studio ke bina APK (GitHub se)
1. github.com par naya repo banao, is folder ki saari files upload/push karo (branch `main`).
2. Repo > Actions > "Build NearChat APK" > Run workflow.
3. 5-10 min baad run kholo > Artifacts > `NearChat-debug-apk` download > unzip > `app-debug.apk` phone mein install.

## Kin phones par chalega
Android 8.0+ (API 26+), Bluetooth wale kisi bhi phone par. iPhone par nahi chalega. Dono phones par NearChat hona zaroori hai.

# DontMissMOM 📱🚨

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com)
[![Firebase](https://img.shields.io/badge/firebase-%23039BE5.svg?style=for-the-badge&logo=firebase)](https://firebase.google.com)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-orange.svg?style=for-the-badge)](https://developer.android.com/topic/libraries/architecture)

**DontMissMOM** is a high-utility emergency bypass communication application. It solves the critical problem of missing urgent calls or alerts from loved ones when your phone is on silent or in "Do Not Disturb" (DND) mode.

---

## 📺 Project Demo
To see the application in action and how the DND bypass logic works, view the video demo below:
👉 **[https://drive.google.com/file/d/1OnjkF9pzsTH3eTtHNDqOmVOfuc0fgJuV/view?usp=drive_link]** *(Recommended: Upload a screen recording to YouTube as 'Unlisted' and paste the link here)*

---

## 🌟 Key Features
- **DND Override:** Programmatically bypasses system-level "Do Not Disturb" settings for authorized emergency alerts.
- **Consent-Based Access:** A secure system ensuring that emergency bypass is only active once both parties have provided authorization.
- **Firebase Integration:** Real-time data synchronization and high-priority push notifications via FCM.
- **Material 3 Design:** Built with modern Android design standards for a clean and intuitive user experience.

## 🛠 Tech Stack
- **Language:** Kotlin
- **UI Framework:** XML / Jetpack Compose 
- **Database/Backend:** Firebase Realtime Database & Authentication
- **Architecture:** MVVM (Model-View-ViewModel)
- **System Services:** NotificationManager & Policy Access API

## 📁 Project Structure
- `app/`: Contains the Android application logic and UI components.
- `functions/`: (Optional) Backend triggers for managing alert priorities.
- `gradle/`: Project build configuration files.

## 🚀 Getting Started
1. **Clone the Repo:** `git clone https://github.com/ASRcodes/DontMissMOM.git`
2. **Firebase Setup:** Place your `google-services.json` in the `app/` directory.
3. **Run:** Build in Android Studio and deploy to a device running API 26+.

---

## 🤝 Contributing
I welcome contributions to enhance the safety features of this project.
1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---
**Developed by [Anubhav Singh](https://github.com/ASRcodes)**

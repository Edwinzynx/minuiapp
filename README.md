# Void UI 🌌

Void UI is a premium, minimal, terminal-style home launcher for Android. Void UI replaces the standard Android home screen with a clean, text-based dashboard that keeps you productive and distraction-free. 
Make sure to change your phone wallpaper to a dark/light colour first ;)

---

## 🎨 Premium Design & Theme
- **Dual Themes**: Dynamic dark/light mode toggle via a slider in the top right.
  - **AMOLED Night Mode**: Pitch-black background with neon green/gold terminal outputs to save battery.
  - **Day Theme**: A soft, high-contrast light cream background with deep charcoal text.
- **Transparent Status & Nav Bars**: Full-screen layout that draws seamlessly under and around your device's camera cutout, with adaptive status bar icons (dark in light theme, light in dark theme).

---

## 🚀 Key Features

### 1. Dynamic Widgets (CLI Themed)
* **`$ date`**: Shows system date and time. Tapping this widget opens your phone's default **Clock app**.
* **`$ battery`**: Displays precise battery level, charging status, temperature, and health indicators.
* **`$ screen`**: Computes and displays your screen time (hours and minutes) alongside a limit bar indicator. Tapping this widget opens your system's **Digital Wellbeing app**.
* **`$ ls ~/apps`**: A list of your custom home screen shortcut apps. Tapping `[edit]` allows you to configure which apps are visible.

### 2. Full-Screen Swipe Gestures
You can swipe anywhere on the screen (including on top of widgets and lists) to trigger actions:
* **Swipe Left** 👈: Opens the app drawer bottom sheet (`$ find /system/apps -type f`).
* **Swipe Right** 👉: Instantly launches the device **Camera app** (with robust intent package fallbacks).

### 3. High-Accuracy Screen Time Tracker
Unlike rolling calculations, Void UI implements custom **UsageEvents log parsing** at the OS layer. It calculates the exact elapsed time between application foreground (`ACTIVITY_RESUMED`) and background (`ACTIVITY_PAUSED`) transitions since midnight to match system wellbeing settings.

### 4. Interactive Default Launcher Setup Dialogs
On first launch, Void UI shows a clean terminal-styled prompt offering two modes:
- **Inspect App**: Dismisses the prompt so you can explore the launcher workspace.
- **Set as Default**: Displays a step-by-step guidance dialog (`$ steps --set_default`) and redirects you directly to the Android **Default Home App Settings** selection menu when you tap **Proceed**.

---

## 📥 Installation

You can download and install the latest build directly from the repository:
1. Download [VoidUI.apk](VoidUI.apk) from the root folder of this repository.
2. Install the APK on your Android device (ensure "Install from unknown sources" is enabled in settings).
3. Open the app and follow the setup instructions to set Void UI as your default launcher.

---

## 🛠️ Technical Stack
* **Language**: Kotlin
* **SDK Version**: targetSdk 34, minSdk 24 (Android 7.0+)
* **Dependencies**: AndroidX, Material Components, CoordinatorLayout, ConstraintLayout, Recyclerview, CardView
* **Architecture**: Clean, lifecycle-aware event handling, SharedPreferences flags for onboarding, and customized theme style overlays.

# Call Forwarding

A premium, lightweight Call Forwarding Android application implemented in Java. It allows users to set and stop call forwarding via carrier MMI/USSD codes.

## Features
- **Broad Carrier Support**: Built-in templates for GSM (Global Standard) and CDMA (Verizon/Sprint), plus a fully customizable code builder for any network provider.
- **Multi-SIM Support**: Automatically detects multiple active SIM cards and allows selecting which SIM slot to run codes on.
- **Routing History**: Keeps track of recently forwarded phone numbers for quick one-click reuse.
- **Modern Dark UI**: Features Material 3 dark-themed visual cards with dynamic status bar spacing compatibility across Android versions.
- **Release Optimization**: Signed release configuration with code shrinking and custom obfuscation dictionary enabled out-of-the-box.

## Build Requirements
- Android SDK 26 (Min) to 35 (Target)
- Gradle build tool

To build the project:
```bash
# Debug APK
./gradlew assembleDebug

# Obfuscated & Signed Release APK
./gradlew assembleRelease
```

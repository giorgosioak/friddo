# Privacy Policy for Friddo

**Last Updated:** April 15, 2026

## 1. Introduction

Friddo is an Android utility for managing Frida server binaries on rooted devices.
This policy explains what data the app accesses, stores, and sends over the network.

## 2. Data Collected by the App

- Personal data: Friddo does not ask for accounts, email addresses, phone numbers, or other directly identifying personal information.
- Local app data: The app stores settings, cached release metadata, downloaded Frida binaries, and local session state on your device.
- Device information: The app reads your device ABI and related runtime state locally in order to select compatible Frida binaries and display server status.
- Root access: Friddo uses local `su` access to start and control `frida-server`. Root actions happen on-device.

## 3. Network Access

Friddo connects to the following upstream services when you choose features that need them:

- GitHub API: Used to fetch Frida release metadata from the upstream Frida repository.
- GitHub release asset URLs: Used to download `frida-server` archives selected by the user.
- Frida website links: The app may open external documentation links in your browser.

When these requests happen, the service you contact may receive standard network metadata such as your IP address, user agent, and request time.

## 4. Telemetry and Tracking

Friddo does not include analytics, crash reporting SDKs, advertising SDKs, or telemetry collection.

## 5. Data Sharing

Friddo does not operate any developer-controlled backend and does not send your app activity to the developer.

Network requests are sent directly from your device to the third-party services you choose to access.

## 6. Data Retention and Deletion

All app data is stored locally on your device until you remove it.

You can delete app data by:

- Deleting installed Frida versions inside the app
- Clearing Friddo app storage from Android system settings
- Uninstalling the app

## 7. Security Notice

Friddo is intended for advanced users and security researchers. Because it works with root privileges and instrumentation tooling, you are responsible for how you use it and for the security impact on your device.

## 8. Contact

Developer: Georgios Ioakeimidis

Email: giorgosioak95@gmail.com

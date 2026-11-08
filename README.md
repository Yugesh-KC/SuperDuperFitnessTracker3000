# Super Duper Fitness Tracker 3000

An Android app for tracking weight, food, and water, with a Gemini-powered coach. Built with Kotlin and Jetpack Compose (Material 3). All data is stored on the device.

## Features

- **Weight**: log daily weigh-ins and watch the trend.
- **Food**: type what you ate in plain language. Gemini estimates calories, protein, carbs, fat, fiber, sugar and sodium for each item.
- **Water**: log intake against a daily target. Optional reminders run between configurable start and end times at a set interval, with a one-tap "add water" action in the notification. Reminders are rescheduled after a reboot, an app update, or a time or timezone change.
- **Progress**: charts of weight, nutrition and water over selectable time ranges.
- **Coach**: chat with an AI coach that sees your logged data and profile. It can remember facts you tell it.
- **Settings**: profile and targets (calories, protein, water), reminder schedule, Gemini model and API keys.

## Gemini API keys

The AI features need a Google Gemini API key. You can get one free at [Google AI Studio](https://aistudio.google.com/apikey). Add it in **Settings** inside the app. No key is bundled with the app.

- You can add several keys. Requests are spread across them round-robin. If a key hits a quota, auth or server error, the app tries the next one.
- The default model is `gemini-2.5-flash`. You can change it in Settings.

## Install

Download the APK from the [latest release](../../releases/latest) and open it on your phone. You may need to allow installs from unknown sources.

Requires Android 8.0 (API 26) or later.

## Build from source

Requirements: Android Studio (or JDK 17+ and the Android SDK, compileSdk 37).

```sh
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease      # release APK -> app/build/outputs/apk/release/
```

### Release signing

Release builds are signed only when a `keystore.properties` file exists in the project root. It is gitignored, so you need to create your own:

```properties
storeFile=../release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`storeFile` is resolved relative to the `app/` module. Without this file, `assembleRelease` produces an unsigned APK.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Calls to the Gemini API |
| `POST_NOTIFICATIONS` | Water reminders |
| `SCHEDULE_EXACT_ALARM` | Reminders fire on time |
| `RECEIVE_BOOT_COMPLETED` | Reschedule reminders after a reboot |

## Project layout

```
app/src/main/java/com/example/superduperfitnesstracker3000/
├── ai/          Gemini REST client and prompts (food analysis, coach)
├── data/        SQLite database, models, settings, stats
├── reminders/   Water reminder alarms and notification receivers
└── ui/          Compose screens, charts, theme, ViewModel
```

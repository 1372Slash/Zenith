# Zenith - A Material Design 3 Expressive Digital Wellbeing App

Zenith is a smart focus assistant for Android designed to help users reduce digital distractions through proactive Shield and Goal systems. The application monitors app usage in real-time and provides mindful interventions before users get caught in endless scrolling behavior.

## Key Features

- Shield Mode: Protects you from addictive applications by providing mindful pauses and limiting the number of uses per period.
- Goal Pursuit: Helps you achieve usage time targets for specific applications (e.g., educational or productivity apps).
- Delay App: Forces a time delay (e.g., 5-10 seconds) before protected apps can be opened, allowing the brain a moment to reconsider.
- Smart Schedules: Automatically block or allow applications based on specific time schedules.
- Emergency Use: A hold-to-unlock system for emergency usage when limits are reached, with a restricted quota.
- Session HUD: A floating overlay that transparently shows the remaining time for an active application session.

## Installation Guide

1. Clone this repository.
2. Open it in Android Studio Ladybug or a newer version.
3. Ensure Android SDK 33+ is installed.
4. Build and run on a physical device (recommended as it requires Accessibility Service permissions).

## Required Permissions

To function optimally, Zenith requires:
1. Accessibility Service: To instantly detect foreground application changes.
2. Usage Access: To accurately calculate daily application usage statistics.
3. Overlay Permission: To display Shield intervention screens over other applications.

## License

This project is created for learning and self-development purposes.
[GNU GPL v3.0](LICENCE)

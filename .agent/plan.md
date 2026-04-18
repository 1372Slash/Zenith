# Project Plan

A digital wellbeing app named Zenith (com.etrisad.mindspace).
Features:
- Material Design 3 (Expressive) & Material You, full Edge-to-Edge.
- 3 Navigation Tabs: Home, Focus (Shields), Settings.
- Home: Welcome message, daily screen time dashboard, top 5 apps expandable section, list of apps under restriction with decreasing progress bars.
- Focus: List of restricted apps (Shields). FAB to add. Bottom sheet for app selection with search. Shield settings: Limits, Emergency use count, Reminders toggle, Strict mode toggle.
- Settings: Theme (Auto/Light/Dark).
- Core Logic: When a restricted app is opened, an overlay (bottom sheet style) appears. It shows icon, name, progress bar (time left), and "How long do you want to use?" options (2, 5, 10, 20 mins) with a delay/buffering before the 5, 10, 20 min buttons become active. Close button.
- Vibrant, energetic color scheme.
- Adaptive icon.

## Project Brief

# Zenith - Project Brief

Zenith is a high-energy digital wellbeing application designed to help users reclaim their focus through expressive Material Design 3 interfaces and intelligent app usage interventions.

## Features

*   **Zenith Dashboard (Home):** A vibrant, Material You-powered dashboard displaying daily screen time and an expandable section for the top 5 most used apps. It includes a real-time list of restricted apps with decreasing progress bars to visualize remaining focus time.
*   **Zenith Shields (Focus):** A dedicated management hub where users can "shield" specific apps. It features a searchable bottom sheet for app selection and granular settings for each shield, including time limits, emergency use counts, and a "Strict Mode" toggle.
*   **Intelligent Intercept Overlay:** A bottom-sheet style intervention that triggers when a restricted app is opened. It forces a "mindful pause" by offering usage extensions (e.g., 2, 5, 10 mins) with a calculated buffering delay before the longer duration buttons become active.
*   **Adaptive Theme & Edge-to-Edge:** Full implementation of Material Design 3 with dynamic color support (Material You), a high-energy color palette, and a seamless Edge-to-Edge experience including a custom adaptive icon.

## High-Level Technical Stack

*   **Kotlin:** The primary language for robust and concise Android development.
*   **Jetpack Compose:** A modern toolkit for building the expressive, reactive UI and the intercept overlay.
*   **Kotlin Coroutines & Flow:** For handling asynchronous app usage monitoring and reactive data updates.
*   **Room (via KSP):** Necessary for persisting "Shield" configurations, app limits, and usage statistics.
*   **Jetpack DataStore:** For managing user preferences like theme selection and "Strict Mode" states.
*   **Material 3 / Material You:** Utilizing the latest Material components and dynamic color utilities for a personalized, energetic aesthetic.

## Implementation Steps
**Total Duration:** 14m 10s

### Task_1_Core_Infrastructure: Set up the data layer and core service logic for app monitoring. This includes configuring Room for persistent 'Shield' data, Jetpack DataStore for user preferences, and implementing a service to monitor app usage via UsageStatsManager. Handle necessary permissions like PACKAGE_USAGE_STATS and SYSTEM_ALERT_WINDOW.
- **Status:** COMPLETED
- **Updates:** Successfully set up the data layer and core service logic for Zenith.
- **Acceptance Criteria:**
  - Room database and DAO for Shields are implemented
  - DataStore for settings is configured
  - App usage monitoring logic is functional
  - Required permissions are requested and handled
- **Duration:** 3m 42s

### Task_2_Main_UI_Navigation_and_Home: Implement the main navigation structure (Home, Focus, Settings tabs) using Material Design 3 and Edge-to-Edge display. Build the Home screen featuring the daily screen time dashboard, the expandable top 5 apps section, and the list of restricted apps with real-time progress bars.
- **Status:** COMPLETED
- **Updates:** Implemented main navigation and Home screen for Zenith.
- **Acceptance Criteria:**
  - Navigation between tabs works seamlessly
  - Edge-to-Edge and Material 3 theme applied
  - Home screen displays usage statistics and restricted apps list correctly
- **Duration:** 4m 44s

### Task_3_Focus_and_Shields_Management: Develop the Focus screen for managing 'Shields'. This includes the list of restricted apps, a searchable bottom sheet for selecting apps from the device, and a settings interface for each shield (Limits, Emergency use, Reminders, Strict mode).
- **Status:** COMPLETED
- **Updates:** Developed the Focus screen for managing 'Shields' in Zenith.
- **Acceptance Criteria:**
  - Focus screen lists active shields
  - Searchable app picker bottom sheet is functional
  - Shield settings can be saved and updated in Room
- **Duration:** 3m 10s

### Task_4_Intercept_Overlay_and_Pause_Logic: Implement the Intelligent Intercept Overlay that triggers when a restricted app is opened. Create the bottom-sheet style UI and the 'mindful pause' logic, including usage extension buttons (2, 5, 10, 20 mins) with a buffering delay for longer durations.
- **Status:** COMPLETED
- **Updates:** Implemented the Intelligent Intercept Overlay and 'mindful pause' logic.
- `InterceptOverlayContent` created using Jetpack Compose with buffering delay logic for extension buttons.
- `InterceptOverlayManager` implemented to display Compose UI over other apps using `WindowManager` and `TYPE_APPLICATION_OVERLAY`.
- `AppUsageMonitorService` updated to trigger the overlay when a shielded app is detected.
- Usage extension logic implemented (e.g., granting 2 mins, 5 mins, etc.) with associated delays.
- "Close App" functionality implemented to return to the home screen.
- Overlay aesthetics matched with the vibrant Material 3 Zenith theme.
- **Acceptance Criteria:**
  - Overlay appears correctly when a restricted app is launched
  - Usage extension buttons work with appropriate delays
  - Overlay can be dismissed/closed correctly
- **Duration:** 2m 34s

### Task_5_Polish_Icon_and_Verification: Finalize the visual design with a vibrant, energetic color scheme and Material You dynamic color support. Create and implement a custom adaptive app icon. Conduct a final run and verify application stability, alignment with Material Design 3 guidelines, and overall performance.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Vibrant M3 color scheme and dynamic colors implemented
  - Adaptive icon is present and matches Zenith branding
  - App builds and runs without crashes
  - Critic_agent verifies stability and requirement alignment
- **StartTime:** 2026-04-18 14:09:32 WITA


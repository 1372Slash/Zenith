---
name: performance-check
description: Use when asked to check, review, or optimize performance, stability, RAM, battery, or CPU usage of Android code changes. Triggered by keywords like "performance", "stability", "optimize", "RAM", "battery", "CPU", "resource usage", "memory leak", "lag". Also triggered at session end when file changes have been made. Use ONLY for performance/stability/resource-usage review — not for general code review.
---

# Performance Check

When invoked, review ALL file changes made in the current session against these Android performance and stability criteria. For every issue found, **apply the fix directly** — edit the file to resolve the problem — not just report it.

## Memory / RAM

- **Large allocations in hot paths** — avoid per-frame object allocations in `onDraw`, `Layout`, composable recomposition
- **Bitmap handling** — `recycle()` or use `BitmapFactory.Options.inSampleSize`; avoid loading full-resolution images into memory
- **Context leaks** — never store `Activity`/`Context` in singletons, static fields, or long-lived collections; use `ApplicationContext` or `WeakReference`
- **ViewBinding/DataBinding** — null out bindings in `onDestroyView()` (Fragment) or `onDestroy()` (Activity)
- **Large lists** — use `RecyclerView` with `ViewHolder` pattern, `Paging 3` for infinite lists, avoid loading entire datasets into memory
- **Cursor / File / Stream leaks** — every `open()` must have a matching `close()` in `finally` or `use {}`
- **Kotlin objects** — `object` declarations hold singletons; ensure they don't reference heavyweight dependencies
- **Coroutine leaks** — every `viewModelScope` / `lifecycleScope` launch should be properly scoped; cancel unused jobs; avoid `GlobalScope`

## Battery

- **Wake locks** — any `acquire()` must have a paired `release()`; prefer `PowerManager.WakeLock` with timeouts; never hold wake locks longer than necessary
- **Network calls** — batch network requests; use `WorkManager` for deferrable work; avoid polling; prefer `WebSocket`/`Firebase Cloud Messaging` over constant polling
- **Location updates** — use `FusedLocationProviderClient` with appropriate priority; remove updates when not needed; use `geofencing` for region monitoring
- **Sensors** — unregister listeners in `onPause()` / `onStop()`; never register in `onResume()` without unregistering
- **Alarms / JobScheduler** — set reasonable intervals; use `flex` window; prefer `WorkManager` over raw `AlarmManager`
- **Animations** — disable or reduce in `onStop()`; avoid infinite or long-running animations when app is backgrounded

## CPU

- **Main thread** — never block the main thread with network I/O, database queries (use `Dispatchers.IO`), large file operations, or complex calculations
- **Recomposition optimization** — use `remember`, `derivedStateOf`, `snapshotFlow`, `key()` to minimize unnecessary recompositions in Jetpack Compose
- **Algorithmic efficiency** — avoid O(n²) or worse in loops over collections; prefer `Sequence` for large chains; use appropriate data structures
- **Reflection** — avoid in hot paths; prefer `Sealed classes`, `polymorphism`, or `when` exhaustive branches
- **Logging** — strip debug logs in release builds (use `BuildConfig.DEBUG` guards or `Timber`)
- **Serialization** — prefer `kotlinx.serialization` or `Moshi` over `Gson` (reflection); use `ProtoBuf` for large data
- **Coroutine dispatching** — CPU-intensive work should use `Dispatchers.Default` (not `IO`); IO-bound work uses `Dispatchers.IO`

## Stability

- **Null safety** — every `!!` is a crash waiting to happen; prefer `?.`, `?:`, `.let {}`, `.requireNotNull()`
- **Crash handling** — validate that `try/catch` blocks log errors and don't silently swallow exceptions
- **Thread safety** — mutable state accessed from multiple threads must use `Mutex`, `Atomic*`, `@Synchronized`, or `StateFlow`/`MutableStateFlow`
- **Resource cleanup** — every `LifecycleOwner` / `ViewModel` / `Fragment` / `Activity` must clean up observers, listeners, and bindings when destroyed
- **State persistence** — ensure `SavedStateHandle` or `onSaveInstanceState` preserves critical UI state across config changes and process death

## Action — Fix, then report

For each issue found, **edit the file to fix it immediately** using the correct approach from the guidelines above. If the fix is unambiguous (e.g., replace `!!` with `?.`, add `use {}`, scope a coroutine), do it.

### Critical: preserve UI and behavior

- **Never change visual output** — don't alter layouts, colors, text, spacing, animations (except reducing or disabling them when backgrounded), or composable structure
- **Never change business logic** — don't modify algorithm outputs, data transformations, API contracts, or user-visible behavior
- **Safe optimization targets only**: resource cleanup, thread dispatching, scope/lifetime management, null safety, logging, data structure efficiency, bitmap scaling
- If an optimization **might** affect UI or behavior (e.g., changing a collection type, modifying a serialization format), skip the edit and flag it for user review

Only if a fix requires user input or would change app behavior do you skip the edit and flag it.

## Summary

After scanning and fixing, report:

```
📁 <file-path>
- ✅ PASS / ⚠️ WARNING (unfixable, needs input) / 🔧 FIXED — <criterion>
  <explanation>
```

End with a summary of what was fixed, what needs user attention, and the net performance impact.

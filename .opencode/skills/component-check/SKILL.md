---
name: component-check
description: Use after making ANY code change to a Kotlin/Compose UI, Activity, Fragment, ViewModel, Repository, API service, DI module, or other Android component. Creates a session checklist of all modified components and verifies they compile, work correctly, and are compatible with the rest of the system. Use ONLY for session-level verification — not for single-line typos or trivial comment changes.
---

# Component Check

After every meaningful change session, create and run through this checklist for each modified component.

## 1. Track All Changed Components

Before finishing any task, inventory every file you touched:

| Component | File | Change Type | Status |
|-----------|------|-------------|--------|
| `ExampleComponent` | `path/File.kt` | new / modified / deleted | pending |

**Rules:**
- Every file you read+edit must be listed
- Group related files under the same component where applicable
- Delete this section once the checklist is done

## 2. Compilation Check

For each component, verify:

- [ ] Project compiles with `./gradlew assembleDebug` (or relevant build command)
- [ ] No new warnings directly related to your changes
- [ ] Any new XML resources, drawables, or strings are properly referenced

## 3. Functional Check

- [ ] Component initializes without crash (check lifecycle: constructor → init → onCreate/onCreateView)
- [ ] State management is correct — no stale or leaked state
- [ ] Navigation to/from the component works (if applicable)
- [ ] User interactions produce expected results (clicks, inputs, gestures)
- [ ] Screen rotation / config change does not break the component (if relevant)

## 4. Compatibility Check

- [ ] **Theme/Style**: Uses existing theme attributes or adds new ones consistently
- [ ] **DI wiring**: If the component has a ViewModel or Repository, check it is provided in the correct DI module (`@HiltViewModel`, `@Module`, `@Provides`, `@Binds`)
- [ ] **API layer**: If the component calls a remote service, verify the endpoint, request/response models, and error handling match the actual API
- [ ] **Room/DB**: If the component reads/writes to the database, verify DAO methods and entity migrations are compatible
- [ ] **SharedPreferences / DataStore**: Key names and data types are consistent with other consumers
- [ ] **Permissions**: Any new runtime permissions are declared in `AndroidManifest.xml` and requested properly
- [ ] **ProGuard / R8**: No rules need updating for new reflection or serialization usage

## 5. Regression Check

- [ ] Existing features that interact with the changed component still work
- [ ] Edge cases tested: empty state, error state, loading state
- [ ] No unused imports or dead code left behind

## 6. Final Sign-off

- [ ] All items above checked and passing
- [ ] `git diff` reviewed to confirm only intended changes
- [ ] Commit message aligns with the change scope

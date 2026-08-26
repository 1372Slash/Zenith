---
name: commit-message
description: Use when the user asks to commit changes, generate a commit message, write a git commit, or describe what changed. Use ONLY for generating commit messages in conventional commits format.
---

When the user asks to commit changes or generate a commit message, format it as:

```
type(scope): description
```

Output ONLY the commit message line(s) with no additional explanation, bullet points, or text.

## Type reference

| Type       | Usage                                        |
|------------|----------------------------------------------|
| `feat`     | A new feature                                |
| `fix`      | A bug fix                                    |
| `docs`     | Documentation only changes                   |
| `style`    | Formatting, missing semicolons, etc          |
| `refactor` | Code change that neither fixes nor adds      |
| `perf`     | Performance improvement                      |
| `test`     | Adding/updating tests                        |
| `build`    | Build system or dependency changes           |
| `ci`       | CI config changes                            |
| `chore`    | Other changes that don't modify src or tests |

## Rules

- `scope` is a short noun identifying the affected area (e.g. `auth`, `api`, `ui`, `db`). Omit if truly global.
- `description` is imperative, lowercase, no trailing period.
- Max 72 chars for the header line.
- If the user provides a specific format, use theirs instead.

## Examples

```
feat(auth): add OAuth2 login flow
fix(api): handle null response on timeout
docs(readme): update installation steps
```
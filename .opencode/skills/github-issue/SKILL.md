---
name: github-issue
description: Use when the user provides a GitHub issue URL and asks you to investigate, analyze, or implement a fix/feature from it. Triggered by phrases like "investigate this issue", "fix this issue", "implement this feature", or any GitHub issue URL. Read the entire issue and ALL comments before acting.
---

# GitHub Issue Investigation & Implementation

When the user provides a GitHub issue URL (from their own repository), follow this process:

## Step 1: Fetch the Issue

Use `webfetch` to retrieve the issue page. Fetch both:
- The main issue page
- The `?page=1` through `?page=N` comment pages if there are many comments

Extract:
- Issue title and description
- All comments (especially maintainer/contributor comments that may clarify requirements)
- Labels, milestones, and assigned people
- Any code snippets or error messages

## Step 2: Understand the Codebase

Before proposing changes:
- Search the codebase for relevant files using `grep` and `glob`
- Understand how the affected area works
- Identify all places that need changes

## Step 3: Implement

Make all necessary changes across the codebase:
- Follow existing code style and conventions
- Update tests if applicable
- Run lint/typecheck to verify

## Important Notes

- Always read ALL comments — the real requirements often emerge in discussion threads
- Check if the issue has a linked PR or related issues
- Respect any contributing guidelines in the repository
- If the issue references specific commits, branches, or code, look those up

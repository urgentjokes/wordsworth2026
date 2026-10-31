---
name: triad-pipeline
description: >-
  Coordinates a 3-stage autonomous software engineering workflow (Architect -> Builder -> Reviewer)
  using Google Antigravity multi-agent orchestration. Activates when asked to execute complex tasks,
  refactors, or new features using the Plan-Build-Review triad or multi-agent pipeline.
---

# Triad Pipeline: Plan, Build, Review

The **Triad Pipeline** divides non-trivial engineering tasks among three specialized agent roles to maximize reasoning quality, execution speed, and token efficiency.

```
       [Orchestrator Agent]
                │
    ┌───────────┼───────────┐
    ▼           ▼           ▼
1. Architect  2. Builder  3. Reviewer
 (Model: pro) (Model: flash) (Model: pro)
```

---

## Model Allocation Strategy

| Stage | Role | Model Tier | Why |
| :--- | :--- | :--- | :--- |
| **1. Plan** | `Architect` | `'pro'` (Argon) | Deep system reasoning, edge-case identification, schema design. |
| **2. Build** | `Builder` | `'flash'` (3.8 Flash) | High speed, low latency, rapid iterative coding and `./gradlew test` loops. |
| **3. Review** | `Reviewer` | `'pro'` (Argon) | Unbiased audit with fresh context, concurrency review, regression defense. |

---

## Execution Workflow

When running a task under this skill, follow these sequential phases:

### Phase 1: Architecture & Specification
Launch the architect subagent with `Model: 'pro'` to analyze the codebase and generate an actionable implementation plan.

```kotlin
invoke_subagent(
    TypeName = "self",
    Role = "Software Architect",
    Model = "pro",
    Prompt = """
    Analyze the codebase and design an implementation plan for:
    <TASK_DESCRIPTION>
    
    Deliverables:
    1. Architectural strategy & data models.
    2. Concrete file-by-file changes required.
    3. Concurrency, state preservation, and edge cases to handle.
    4. Unit test plan and verification commands.
    
    Do NOT modify code files. Return your plan directly in your reply.
    """
)
```

### Phase 2: Implementation & Verification
Once the Architect responds with the plan, launch the builder subagent with `Model: 'flash'` to write the code and verify tests pass.

```kotlin
invoke_subagent(
    TypeName = "self",
    Role = "Software Engineer",
    Model = "flash",
    Prompt = """
    Implement the following approved architectural plan:
    <PLAN_FROM_ARCHITECT>
    
    Requirements:
    1. Apply all file edits cleanly. Maintain existing comments and docstrings.
    2. Add unit tests covering the new functionality.
    3. Run `./gradlew test` (and `./gradlew assembleDebug` if applicable) to verify builds succeed.
    4. Do NOT commit or push to git.
    5. Reply with a summary of modified files and test verification results.
    """
)
```

### Phase 3: Independent Code Review
Once the Builder completes implementation, retrieve the `git diff` and launch the reviewer subagent with `Model: 'pro'` in a fresh context.

```kotlin
invoke_subagent(
    TypeName = "self",
    Role = "Code Reviewer",
    Model = "pro",
    Prompt = """
    Perform a strict, production-readiness code review for the following changes:
    
    Plan:
    <PLAN_FROM_ARCHITECT>
    
    Git Diff:
    <GIT_DIFF_OUTPUT>
    
    Audit Checklist:
    1. Concurrency & thread safety (coroutine dispatchers, race conditions).
    2. Lifecycle handling (process death, background transitions, orientation).
    3. Backward compatibility (handling legacy JSON/data formats).
    4. Performance (unnecessary allocations in UI loops, memory leaks).
    
    If critical issues exist, list concrete fixes needed.
    If acceptable, output 'APPROVED' along with a bulleted assessment.
    """
)
```

### Feedback Loop (If Reviewer Requests Changes)
If the Reviewer identifies defects:
1. Message the `Builder` subagent (`send_message`) with the Reviewer's feedback.
2. The Builder resolves the issues and re-runs unit tests.
3. Once tests pass, the Reviewer performs a final sign-off.

---

## User Interaction & Workspace Rules
- Never run `git commit` or `git push` autonomously.
- Once the Reviewer marks the task `APPROVED`, present a concise summary of the changes and test results to the user and request confirmation before committing or deploying.

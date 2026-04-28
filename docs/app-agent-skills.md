# App Agent Skills

App skills are structured, advisory app-use procedures for the Android agent.
They are not executable scripts and cannot directly tap the phone.

## Runtime Contract

1. The user starts an agent session explicitly.
2. `InstalledAppCatalog` identifies target package candidates.
3. `SkillResolver` loads built-in skill procedures for those packages.
4. `SessionRepository` loads learned skill memories from previous terminal outcomes.
5. `AgentOrchestrator` injects skill guidance into `PlannerInput`.
6. The model emits strict JSON.
7. The local validator compiles JSON into `AgentAction`.
8. Policy and Accessibility executor run only validated DSL actions.

## Skill JSON Schema

```json
{
  "appName": "Settings",
  "packageNames": ["com.android.settings"],
  "procedures": [
    {
      "name": "search_settings",
      "goalPatterns": ["settings", "wifi"],
      "riskPoints": ["Changing settings requires confirm_user before commit."],
      "steps": [
        {
          "intent": "Open search",
          "expectedObservation": "Editable search field is visible",
          "fallbackStrategy": "Inspect visible nodes before retrying",
          "preferredSelectors": [
            {
              "contentDescription": "Search settings",
              "clickable": true
            }
          ]
        }
      ]
    }
  ]
}
```

## Safety Rules

- Current accessibility tree wins over stale skill guidance.
- Skills provide selector hints, expected observations, fallback strategy, and risk points.
- Skills must never contain raw tap coordinates as the only strategy.
- High-risk actions still stop at `confirm_user`.
- Learned memories are written only at terminal success/failure.

## QA Evidence Required

For each new app skill, keep:

- screenshot
- UI tree dump
- top window package
- planner trace with `skills=<count>`
- session DB row
- action result log

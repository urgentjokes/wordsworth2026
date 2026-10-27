# Workspace Rules

## Git & Version Control
- **Never commit or push without explicit user permission or command**: You must never run `git commit`, `git push`, or otherwise commit/push changes to any git branch or remote repository without first explicitly receiving confirmation or a direct command from the user.

## Launcher Icon Strategy
- **Development / Current (Approach B)**: Dynamic in-app icon switching via `<activity-alias>` (`MainActivityAliasEn` with "WORD" icon, `MainActivityAliasJa` with "ます" icon) based on the active in-app language setting.
- **Production / Shipping (Approach A)**: When ready to ship, switch to system locale qualifiers (`res/mipmap-ja-...` for Japanese, default `res/mipmap-...` for English) and localized Google Play Store listings.

# color_block

Дитяча розвиваюча гра для Android (APK) для дітей 3-5 років: кубики у
стилі LEGO потрібно рухати та виводити за межі ігрового поля. Перемога —
коли всі кубики виведені. 40 рівнів.

## Development Orchestration
This development follows the vault-submodule rule:
@vault/Розробка/Оркестрація розробки — Правило.md

Before any work: route the request through 0-prompt-refiner, then
3-quality-architect opens the session in
vault/Розробка/Активні розробки/Сесії/, then 2-builder implements a new
feature (or 4-code-auditor — audit/improve existing code); on pause or
finish, 3-quality-architect closes the session with an essay.
Subagents: .claude/agents/{0-prompt-refiner,1-scout,2-builder,3-quality-architect,4-code-auditor}.md

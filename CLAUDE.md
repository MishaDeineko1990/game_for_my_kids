# game_for_my_kids (Kids games)

Родинний хаб дитячих ігор для Android. Головний застосунок ("Kids games",
package `com.deineko.kidsgames`, модуль `hub/`) — це єдиний APK, що на
титульному екрані показує внутрішній "стор" ігор: список ігор з репо,
кнопки "Встановити/Видалити" (показати/приховати гру та її прогрес),
запуск гри. Код усіх ігор постійно живе всередині цього APK (окремі ігри
не є окремими інстальованими застосунками); "довантажується" — вміст
(рівні, картинки) і сам APK хаба при виході оновлення.

Автооновлення: хаб при старті читає `catalog/index.json` з цього репо
(raw.githubusercontent.com), і якщо `hub.versionCode` в каталозі більший
за версію хаба — пропонує завантажити й встановити новий APK. Android
завжди вимагає підтвердження встановлення від користувача на системному
екрані для АPK з невідомих джерел — це обмеження ОС, обійти його без
прав власника пристрою/root неможливо.

## Структура репо

- `hub/` — Android-застосунок (єдиний APK): титульний екран/стор,
  каталог, автооновлення, реєстр ігор (`games/GameRegistry.kt`).
- `games/color_block/` — перша гра, вбудована в хаб:
  - `game-core/` — чиста Kotlin JVM логіка гри (без залежності на Android).
  - `ui/` — Android-бібліотека (Compose UI), підключається хабом як
    залежність; має власний `com.deineko.colorblock` package і назву,
    але вже не є окремим застосунком (немає власного launcher/MainActivity).
- `catalog/index.json` — каталог, який читає хаб (версія хаба + список ігор).
- `vault/` — субмодуль другого мозку розробки (Obsidian).

## Development Orchestration
This development follows the vault-submodule rule:
@vault/Розробка/Оркестрація розробки — Правило.md

Before any work: route the request through 0-prompt-refiner, then
3-quality-architect opens the session in
vault/Розробка/Активні розробки/Сесії/, then 2-builder implements a new
feature (or 4-code-auditor — audit/improve existing code); on pause or
finish, 3-quality-architect closes the session with an essay.
Subagents: .claude/agents/{0-prompt-refiner,1-scout,2-builder,3-quality-architect,4-code-auditor}.md

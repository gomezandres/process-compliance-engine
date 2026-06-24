---
description: Use gpush to commit and push changes instead of manual git add/commit/push
alwaysApply: true
---

# gpush — commit + push automático

## Qué hace

`gpush` es un alias definido en `~/.bashrc` que ejecuta `bash ~/apps/init-scripts/git-ai.sh`. El script también está versionado en este repositorio en `scripts/git-ai.sh`.

El script:
1. Hace `git add .` (stagea todo)
2. Clasifica los archivos modificados por tipo: `feat` (`.java` no-test), `test` (`Test.java`, `/test/`), `chore` (`pom.xml`, `.yml`, `.properties`), `docs` (`.md`, `.txt`), `refactor` (resto)
3. Genera el mensaje de commit automáticamente en formato convencional
4. Ejecuta `git commit` + `git push`

## Cuándo usarlo

Usar `gpush` siempre que el usuario pida hacer commit y push de los cambios, en lugar de la secuencia manual `git add` + `git commit` + `git push`.

```bash
# CORRECTO — un solo comando
gpush

# INCORRECTO — no usar la secuencia manual cuando el usuario quiere commit+push
git add .
git commit -m "..."
git push
```

## Cuándo NO usarlo

- El usuario quiere **solo commit** sin push → usar `git add` + `git commit` normalmente.
- El usuario quiere un **mensaje de commit específico** → respetar su mensaje, no delegar al script.
- El usuario pide hacer `git add` de archivos puntuales (no todo) → stagear manualmente y preguntar antes de usar `gpush`.

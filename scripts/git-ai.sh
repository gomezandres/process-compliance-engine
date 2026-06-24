#!/bin/bash

# 1. Agregar todos los cambios al área de preparación
git add .

# 2. Capturar archivos por categoría (ORDEN CRÍTICO PARA JAVA)
# Extraemos primero los tests para poder excluirlos de la categoría feat luego
TEST_FILES=$(git diff --cached --name-only | grep -E 'Test\.java$|/test/' | xargs -n 1 basename | tr '\n' ' ')

# Capturamos los .java pero EXCLUIMOS los que terminan en Test.java
FEAT_FILES=$(git diff --cached --name-only | grep '\.java$' | grep -v 'Test\.java' | xargs -n 1 basename | tr '\n' ' ')

# Configuraciones y dependencias
CHORE_FILES=$(git diff --cached --name-only | grep -E 'pom\.xml|build\.gradle|.*\.properties|.*\.yml|.*\.yaml' | xargs -n 1 basename | tr '\n' ' ')

# Documentación
DOCS_FILES=$(git diff --cached --name-only | grep -E '\.md$|\.txt$|/docs/' | xargs -n 1 basename | tr '\n' ' ')

# 3. Verificar si hay cambios antes de seguir
if [ -z "$FEAT_FILES" ] && [ -z "$CHORE_FILES" ] && [ -z "$DOCS_FILES" ] && [ -z "$TEST_FILES" ]; then
    # Si hay algo que no encajó arriba, lo mandamos a refactor
    REMANENTE=$(git diff --cached --name-only | xargs -n 1 basename | tr '\n' ' ')
    if [ -z "$REMANENTE" ]; then
        echo "⚠️ No hay cambios detectados para subir."
        exit 1
    fi
    REFACTOR_FILES=$REMANENTE
fi

# 4. Construir la lista de categorías presentes para el título
CATEGORIAS=""
[ -n "$FEAT_FILES" ] && CATEGORIAS="${CATEGORIAS}feat "
[ -n "$CHORE_FILES" ] && CATEGORIAS="${CATEGORIAS}chore "
[ -n "$DOCS_FILES" ] && CATEGORIAS="${CATEGORIAS}docs "
[ -n "$TEST_FILES" ] && CATEGORIAS="${CATEGORIAS}test "
[ -n "$REFACTOR_FILES" ] && CATEGORIAS="${CATEGORIAS}refactor "

# 5. Determinar el título (MAIN_MSG)
COUNT_CAT=$(echo $CATEGORIAS | wc -w)
if [ $COUNT_CAT -le 1 ]; then
    # Título simple si solo hay una categoría
    MAIN_MSG="$(echo $CATEGORIAS | xargs): update"
else
    # Título multi si hay mezcla
    MAIN_MSG="multi: updates en $(echo $CATEGORIAS | xargs)"
fi

# 6. Construir el cuerpo del mensaje (BODY)
BODY=""
[ -n "$FEAT_FILES" ] && BODY="${BODY}\n- feat: $FEAT_FILES"
[ -n "$CHORE_FILES" ] && BODY="${BODY}\n- chore: $CHORE_FILES"
[ -n "$DOCS_FILES" ] && BODY="${BODY}\n- docs: $DOCS_FILES"
[ -n "$TEST_FILES" ] && BODY="${BODY}\n- test: $TEST_FILES"
[ -n "$REFACTOR_FILES" ] && BODY="${BODY}\n- refactor: $REFACTOR_FILES"

# 7. Ejecutar el commit y el push
echo -e "🚀 Preparando super-commit con categorías:$BODY"
git commit -m "$MAIN_MSG" -m "$(echo -e "$BODY")"
git push
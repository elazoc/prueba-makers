#!/usr/bin/env bash
# Despliega Prestemos en el servidor (docs/DESPLIEGUE.md) y se detiene ante cualquier fallo.
# Uso, en la carpeta del proyecto en el servidor:
#   bash scripts/deploy.sh            trae main, compila, cambia la imagen y verifica
#   bash scripts/deploy.sh rollback   vuelve a la imagen anterior del backend
#
# Todo va dentro de main(): git pull puede reemplazar este archivo mientras corre, y así bash ya
# lo leyó completo antes de empezar.

main() {
  set -euo pipefail
  local DIR; DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
  cd "$DIR"
  local QUE=${1:-desplegar}
  case "$QUE" in desplegar|rollback) ;; *) echo "uso: deploy.sh [rollback]"; exit 1 ;; esac

  local RAMA=main IMAGEN=prestemos/backend
  local URL=http://127.0.0.1:8050
  # El jar se compila con este JDK en un contenedor desechable con 1 núcleo: el VPS es compartido.
  local JDK=eclipse-temurin:21-jdk
  # Usuario sin root del contenedor del backend (docker-compose.prod.yml)
  local UID_APP=10001
  local G=(git -c "safe.directory=$DIR")
  export GIT_TERMINAL_PROMPT=0

  paso()  { echo; echo "== $*"; }
  falla() { echo; echo "✘ $*"; exit 1; }

  [ -f .env ] || falla "falta el .env (ver docs/DESPLIEGUE.md)"
  [ -f secrets/jwt-private.pem ] && [ -f secrets/jwt-public.pem ] || falla "faltan las claves JWT en secrets/"
  # Las claves solo las lee el usuario del contenedor
  chown -R "$UID_APP:$UID_APP" secrets
  chmod 700 secrets
  chmod 600 secrets/*.pem

  # ¿Atiende? El contenedor está healthy y el login de un usuario de prueba responde 200
  # (eso prueba que llegó a la base y que firma tokens). Devuelve error con el motivo.
  sano() {
    local i estado="" login
    for i in $(seq 1 40); do
      estado=$(docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q backend)" 2>/dev/null || true)
      [ "$estado" = healthy ] && break
      [ "$estado" = unhealthy ] && break
      sleep 5
    done
    [ "$estado" = healthy ] || { echo "el backend quedó '${estado:-sin contenedor}'"; return 1; }
    login=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$URL/api/v1/auth/login" \
      -H 'Content-Type: application/json' -d '{"email":"admin@test.com","password":"123"}' || true)
    [ "$login" = 200 ] || { echo "el login de prueba respondió '$login' (se esperaba 200)"; return 1; }
  }

  volver() {
    docker image inspect "$IMAGEN:anterior" >/dev/null 2>&1 || falla "no hay imagen anterior a la cual volver"
    docker tag "$IMAGEN:anterior" "$IMAGEN:latest"
    docker compose up -d --no-build --force-recreate backend </dev/null
  }

  if [ "$QUE" = rollback ]; then
    paso "Volviendo a la imagen anterior"
    volver
    local motivo; motivo=$(sano) || falla "después de volver atrás: $motivo"
    echo "✔ Atiende la imagen anterior."
    exit 0
  fi

  paso "Código"
  [ "$("${G[@]}" branch --show-current)" = "$RAMA" ] || falla "la carpeta no está en la rama $RAMA"
  [ -z "$("${G[@]}" status --porcelain --untracked-files=no)" ] \
    || falla "hay archivos cambiados a mano en el servidor; no se tocó nada"
  "${G[@]}" pull --ff-only --quiet origin "$RAMA" || falla "git pull no pudo avanzar; no se tocó nada"
  "${G[@]}" --no-pager log --oneline -1

  paso "Compilar el backend (1 núcleo; lo publicado sigue atendiendo)"
  rm -rf backend/target
  docker run --rm --cpus 1 --memory 1536m \
    -v "$DIR/backend":/src -v prueba-makers-m2:/root/.m2 -w /src "$JDK" \
    ./mvnw -q -B -Dmaven.test.skip=true package </dev/null \
    || falla "el backend no compiló; lo publicado sigue igual"
  ls backend/target/prestemos-*.jar >/dev/null 2>&1 || falla "la compilación no dejó el jar"

  paso "Imagen nueva"
  local actual; actual=$(docker image inspect --format '{{.Id}}' "$IMAGEN:latest" 2>/dev/null || true)
  [ -z "$actual" ] || docker tag "$IMAGEN:latest" "$IMAGEN:anterior"
  docker compose build backend </dev/null || falla "no se pudo construir la imagen; lo publicado sigue igual"
  docker compose up -d </dev/null || falla "no se pudo levantar la versión nueva"

  paso "Verificación"
  local motivo
  if ! motivo=$(sano); then
    echo "✘ La versión nueva no atiende: $motivo"
    docker compose logs --tail 30 backend 2>&1 | sed 's/^/    /' || true
    [ -n "$actual" ] || falla "no hay versión anterior a la cual volver"
    echo "Volviendo a la anterior…"
    volver
    motivo=$(sano) || falla "ni la anterior atiende: $motivo"
    falla "se volvió a la versión anterior, que sí atiende. Corrige y despliega de nuevo"
  fi
  docker image prune -f >/dev/null 2>&1 || true
  echo
  echo "✔ Desplegado: $("${G[@]}" --no-pager log --oneline -1)"
  echo "  https://prestamos.nebulasoftware.app  ·  para volver atrás: bash scripts/deploy.sh rollback"
}

main "$@"

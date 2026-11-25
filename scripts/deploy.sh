#!/usr/bin/env bash
# Despliega Prestemos en el servidor (docs/DESPLIEGUE.md) y se detiene ante cualquier fallo.
# Uso, en la carpeta del proyecto en el servidor:
#   bash scripts/deploy.sh            trae main, compila back y front, cambia las imágenes y verifica
#   bash scripts/deploy.sh rollback   vuelve a las imágenes anteriores
#
# Todo va dentro de main(): git pull puede reemplazar este archivo mientras corre, y así bash ya
# lo leyó completo antes de empezar.

main() {
  set -euo pipefail
  local DIR; DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
  cd "$DIR"
  local QUE=${1:-desplegar}
  case "$QUE" in desplegar|rollback) ;; *) echo "uso: deploy.sh [rollback]"; exit 1 ;; esac

  local RAMA=main
  local SERVICIOS=(backend frontend)
  local URL=http://127.0.0.1:8050
  # Se compila en contenedores desechables con 1 núcleo: el VPS es compartido.
  local JDK=eclipse-temurin:21-jdk
  local NODE=node:24-alpine
  # Usuario sin root del contenedor del backend (docker-compose.prod.yml)
  local UID_APP=10001
  local G=(git -c "safe.directory=$DIR")
  export GIT_TERMINAL_PROMPT=0

  paso()  { echo; echo "== $*"; }
  falla() { echo; echo "✘ $*"; exit 1; }
  imagen() { echo "prestemos/$1"; }

  [ -f .env ] || falla "falta el .env (ver docs/DESPLIEGUE.md)"
  [ -f secrets/jwt-private.pem ] && [ -f secrets/jwt-public.pem ] || falla "faltan las claves JWT en secrets/"
  # Las claves solo las lee el usuario del contenedor
  chown -R "$UID_APP:$UID_APP" secrets
  chmod 700 secrets
  chmod 600 secrets/*.pem

  # ¿Atiende? Los dos contenedores están healthy y, a través del nginx del front: la página
  # carga, Swagger responde y el login de un usuario de prueba da 200 (llega a la base y firma
  # tokens). Devuelve error con el motivo.
  sano() {
    local i s estado codigo
    for s in "${SERVICIOS[@]}"; do
      estado=""
      for i in $(seq 1 40); do
        estado=$(docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q "$s")" 2>/dev/null || true)
        [ "$estado" = healthy ] && break
        [ "$estado" = unhealthy ] && break
        sleep 5
      done
      [ "$estado" = healthy ] || { echo "$s quedó '${estado:-sin contenedor}'"; return 1; }
    done
    curl -sf "$URL/" | grep -q '<app-root' || { echo "la página no carga el Angular"; return 1; }
    codigo=$(curl -s -o /dev/null -w '%{http_code}' "$URL/v3/api-docs" || true)
    [ "$codigo" = 200 ] || { echo "Swagger (/v3/api-docs) respondió '$codigo'"; return 1; }
    codigo=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$URL/api/v1/auth/login" \
      -H 'Content-Type: application/json' -d '{"email":"admin@test.com","password":"123"}' || true)
    [ "$codigo" = 200 ] || { echo "el login de prueba respondió '$codigo' (se esperaba 200)"; return 1; }
  }

  volver() {
    local s
    for s in "${SERVICIOS[@]}"; do
      docker image inspect "$(imagen "$s"):anterior" >/dev/null 2>&1 \
        && docker tag "$(imagen "$s"):anterior" "$(imagen "$s"):latest"
    done
    docker compose up -d --no-build --force-recreate "${SERVICIOS[@]}" </dev/null
  }

  if [ "$QUE" = rollback ]; then
    paso "Volviendo a las imágenes anteriores"
    volver
    local motivo; motivo=$(sano) || falla "después de volver atrás: $motivo"
    echo "✔ Atienden las imágenes anteriores."
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
  ls backend/target/prestemos-*.jar >/dev/null 2>&1 || falla "la compilación del backend no dejó el jar"

  paso "Compilar el front (1 núcleo; lo publicado sigue atendiendo)"
  # El código se copia dentro del contenedor (sin node_modules ni dist) y se compila ahí:
  # la carpeta del servidor solo se lee. --ignore-scripts: ningún paquete corre código al instalarse.
  rm -rf frontend-dist && mkdir frontend-dist
  docker run --rm --cpus 1 --memory 2g -e HOME=/tmp -e npm_config_cache=/tmp/npm \
    -v "$DIR/frontend":/src:ro -v "$DIR/frontend-dist":/out "$NODE" sh -c '
      set -e
      mkdir /tmp/build
      cd /src && tar --exclude=./node_modules --exclude="./dist*" --exclude=./.angular -cf - . | tar -xf - -C /tmp/build
      cd /tmp/build
      npm ci --no-audit --no-fund --ignore-scripts > /tmp/npm.log 2>&1 \
        || { grep -v "^npm warn" /tmp/npm.log | tail -n 20; exit 1; }
      npx --no-install ng build --output-path /out > /tmp/ng.log 2>&1 \
        || { tail -n 30 /tmp/ng.log; exit 1; }
      grep -iE "complete|built|Output location" /tmp/ng.log | tail -n 2 || true
    ' </dev/null || falla "el front no compiló; lo publicado sigue igual"
  [ -f frontend-dist/browser/index.html ] || falla "la compilación del front no dejó index.html"
  cp deploy/frontend.nginx.conf frontend-dist/default.conf

  paso "Imágenes nuevas"
  local s hay_anterior=""
  for s in "${SERVICIOS[@]}"; do
    if docker image inspect "$(imagen "$s"):latest" >/dev/null 2>&1; then
      docker tag "$(imagen "$s"):latest" "$(imagen "$s"):anterior"
      hay_anterior=1
    fi
  done
  docker compose build "${SERVICIOS[@]}" </dev/null || falla "no se pudieron construir las imágenes; lo publicado sigue igual"
  docker compose up -d </dev/null || falla "no se pudo levantar la versión nueva"

  paso "Verificación"
  local motivo
  if ! motivo=$(sano); then
    echo "✘ La versión nueva no atiende: $motivo"
    docker compose logs --tail 20 "${SERVICIOS[@]}" 2>&1 | sed 's/^/    /' || true
    [ -n "$hay_anterior" ] || falla "no hay versión anterior a la cual volver"
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

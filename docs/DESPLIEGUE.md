# Despliegue

La aplicación está publicada en **https://prestamos.nebulasoftware.app**.

Corre en un VPS Linux con Docker Compose, detrás del nginx del servidor, que pone el HTTPS
(Let's Encrypt, renovación automática). El VPS es compartido con otros proyectos, así que cada
servicio tiene un tope de memoria y ninguno publica puertos fuera del servidor.

## Cómo está armado

```
Internet ──HTTPS──▶ nginx del servidor ──▶ 127.0.0.1:8050
                                              │
                         docker compose (este repositorio, rama main)
                         ├── frontend: nginx con el Angular compilado; /api → backend
                         ├── backend:  Spring Boot (perfil postgres), sin puertos publicados
                         └── postgres: PostgreSQL 17, sin puertos publicados
```

- **Un solo dominio** para el front y la API: el Angular llama a `/api` y no hace falta CORS.
  La cookie del refresh token (`Secure`, `SameSite=Strict`, `Path=/api/v1/auth`) funciona sin
  ajustes porque todo va por HTTPS en el mismo origen.
- **Eventos en vivo (SSE):** `/api/v1/loans/events` pasa por los dos nginx sin buffer y con
  conexiones de hasta 35 minutos.
- **Imágenes compiladas en el servidor** con los Dockerfile multietapa de `backend/` y
  `frontend/`: el servidor no necesita Java ni Node.
- **Secretos fuera del repositorio:** la clave de PostgreSQL y el par de claves RS256 de los JWT
  se generan en el servidor y viven en su `.env` y en una carpeta `secrets/` ignorada por git.
  Así los tokens sobreviven a los reinicios.
- **Datos de demostración:** los usuarios de prueba (`usuario@test.com` y `admin@test.com`,
  clave `123`) y préstamos de ejemplo se cargan con Flyway al crear la base.

## Desplegar

Desde la carpeta del proyecto en el servidor:

```bash
bash scripts/deploy.sh
```

El script trae `main`, construye las imágenes mientras la versión anterior sigue atendiendo,
las pone en marcha y comprueba que la app responda. Si la versión nueva no responde, vuelve a la
anterior.

## Archivos

- `docker-compose.prod.yml`: el compose de producción, independiente del de desarrollo.
- `scripts/deploy.sh`: el despliegue (y `rollback` para volver a la imagen anterior).
- En el servidor, fuera de git: `.env` (`COMPOSE_PROJECT_NAME`, `COMPOSE_FILE`, `DB_PASSWORD`) y
  `secrets/` (`jwt-private.pem` en PKCS#8 y `jwt-public.pem` en X.509).

El backend se compila en un contenedor desechable limitado a un núcleo, para no quitarles CPU a
los demás proyectos del servidor, y la imagen final solo empaqueta el jar sobre un JRE, con un
usuario sin privilegios.

## Estado

| Paso | Estado |
|---|---|
| Dominio, HTTPS y nginx del servidor | Listo (2026-11-25) |
| Repositorio en GitHub | Listo |
| Carpeta, `.env` y claves en el servidor | Listo |
| Backend (API + Swagger) con PostgreSQL | Desplegado, sin puertos publicados |
| Front (Angular) | Desplegado en versión provisional: algunas pantallas aún están en construcción |

### Front provisional

Mientras el front no tenga su propio `frontend/Dockerfile`, `scripts/deploy.sh` lo compila con
Node 24 en un contenedor desechable (`ng build`, configuración de producción, sin simulador) y lo
sirve un nginx sin root con `deploy/frontend.nginx.conf`. Ese nginx atiende el puerto publicado y
reparte:

| Ruta | Va a |
|---|---|
| `/` y rutas del Angular | `index.html` del Angular (sin caché, con CSP) |
| `/api/…` | backend (`/api/v1/loans/events` sin buffer, hasta 35 min) |
| `/swagger-ui.html`, `/v3/api-docs`, `/actuator/health` | backend, sin la CSP del front |

Cuando exista el Dockerfile del front, el servicio `frontend` pasa a usarlo y
`deploy/frontend.nginx.conf` se retira.

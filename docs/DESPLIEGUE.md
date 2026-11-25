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

## Estado

| Paso | Estado |
|---|---|
| Dominio, HTTPS y nginx del servidor | Listo (2026-11-25) |
| Repositorio en GitHub | En curso |
| Carpeta, `.env` y claves en el servidor | Pendiente |
| `docker-compose.prod.yml` y `scripts/deploy.sh` | Pendiente: dependen del `docker-compose.yml` y los Dockerfile del back y del front |
| Primer despliegue | Pendiente |

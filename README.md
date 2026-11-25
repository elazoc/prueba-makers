# Prestemos

Sistema de gestión de préstamos bancarios: el usuario solicita un préstamo (monto y plazo) y
consulta su estado; el administrador lo aprueba o lo rechaza. API REST en **Spring Boot 4.2 /
Java 21** con arquitectura hexagonal. Marca Caelum.

- **En vivo:** https://prestamos.nebulasoftware.app (ver [despliegue](docs/DESPLIEGUE.md))
- **Parte teórica:** [Parte teórica](docs/Prueba%20teorica%20Java%20Angular.pdf)

## Cómo correrlo

Solo necesitas un JDK 21. Maven viene en el repositorio (`mvnw`).

```bash
cd backend
./mvnw spring-boot:run        # API en http://localhost:8080 con H2 en memoria y datos de demo
./mvnw verify                 # todas las pruebas (las de PostgreSQL usan Docker; sin Docker se omiten)
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Consola H2: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:prestemos`, usuario `sa`, sin clave)

### Usuarios de prueba

| Rol | Email | Contraseña |
|-----|-------|-----------|
| Usuario | `usuario@test.com` | `123` |
| Administrador | `admin@test.com` | `123` |

### Ejemplo rápido

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"usuario@test.com","password":"123"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
curl -s -X POST localhost:8080/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"amount":1500000,"termMonths":24}'
curl -s localhost:8080/api/v1/me/loans -H "Authorization: Bearer $TOKEN"
```

## API (`/api/v1`)

| Método y ruta | Quién | Qué hace |
|---|---|---|
| `POST /auth/login` | público | Devuelve el token de acceso (15 min) y deja el refresh token en una cookie `HttpOnly` |
| `POST /auth/register` | público | Crea una cuenta con rol usuario |
| `POST /auth/refresh` · `POST /auth/logout` | cookie + cabecera `X-Requested-With: prestemos` | Renueva o cierra la sesión |
| `POST /loans` | usuario | Solicita un préstamo (`{amount, termMonths}`) |
| `GET /me/loans` | usuario | Mis préstamos (paginado, filtro `status`) |
| `GET /loans/{id}` | dueño o admin | Detalle (un préstamo ajeno responde 404) |
| `PUT /loans/{id}` · `DELETE /loans/{id}` | dueño | Edita o cancela mientras está pendiente |
| `GET /loans` | admin | Todos los préstamos con el email del solicitante |
| `POST /loans/{id}/approve` · `POST /loans/{id}/reject` | admin | Decide (el rechazo exige motivo) |
| `GET /loans/events` | autenticado | Avisos en vivo (SSE): el admin recibe todos; el usuario, las decisiones sobre sus préstamos |
| `GET /users/me` | autenticado | Mi perfil |
| `GET/POST /users` · `GET/PUT/DELETE /users/{id}` | admin | Administración de usuarios (DELETE desactiva) |

Reglas de negocio: monto de 100 a 50.000.000, plazo de 1 a 120 meses, máximo 3 préstamos
pendientes por usuario. Solo un préstamo `PENDING` puede aprobarse, rechazarse, editarse o
cancelarse.

Los errores siguen **RFC 9457** (`application/problem+json`) con `code`, `timestamp` y un
`detail` en español listo para mostrar: 400 datos inválidos, 401 sin sesión, 403 sin permiso,
404 no existe, 409 conflicto de estado o de concurrencia, 422 regla de negocio.

## Arquitectura

Hexagonal, en un solo módulo (`backend/src/main/java/com/wecaelum/prestemos`):

```
domain          Java puro: Loan (máquina de estados), User, value objects, política, eventos
application     casos de uso (port.in), puertos de salida (port.out) y servicios con @Transactional
infrastructure  adaptadores: web (REST), persistencia (JPA), seguridad (JWT), eventos, caché, config
```

`infrastructure → application → domain`, nunca al revés. Lo comprueba `ArchitectureTest`
(ArchUnit): el dominio no importa Spring, JPA ni Jackson, y la aplicación solo usa
`@Transactional`.

## Decisiones técnicas

**Arquitectura hexagonal.** El dominio no depende de ningún framework, así que las reglas del
préstamo se prueban sin Spring. Las entidades JPA están separadas del modelo de dominio y se
mapean a mano. Concesión pragmática: `@Transactional` en los servicios de aplicación.

**Spring MVC con hilos virtuales, no WebFlux.** El enunciado pide JPA, y JPA es bloqueante:
mezclarlo con WebFlux bloquearía el event loop o obligaría a envolver todo en `boundedElastic`,
que es volver a un hilo por petición con más complejidad. Con Java 21 y
`spring.threads.virtual.enabled=true`, MVC atiende miles de peticiones concurrentes con código
simple.

**Avisos en vivo con SSE.** Es el único punto reactivo: un `Flux` de Reactor servido desde MVC en
`GET /loans/events`. Los eventos salen solo después del commit (una transacción revertida no
avisa nada), con un heartbeat cada 15 s para que los proxies no corten la conexión, y el servidor
la cierra a los 30 minutos para que el front se reconecte con su token vigente. Se eligió SSE y no
WebSockets porque los avisos van en una sola dirección y viajan por HTTP normal, con el mismo JWT
en la cabecera y las mismas reglas de seguridad que el resto de la API.

**Transacción y concurrencia en la aprobación.** Aprobar o rechazar ocurre en una transacción
que lee el préstamo sin caché, aplica la transición en el dominio y guarda. El bloqueo optimista
(`@Version`) hace que, si dos administradores deciden el mismo préstamo a la vez, uno gane y el
otro reciba 409; `LoanFlowIT` lo prueba con dos hilos. El bloqueo pesimista solo se usa al
renovar el refresh token, donde dos renovaciones simultáneas del mismo token no deben cruzarse.

**Seguridad.** JWT RS256 con el resource server de Spring Security (sin librerías de terceros),
contraseñas con BCrypt, rol y estado del usuario leídos de la base en cada petición: desactivar a
alguien o cambiarle el rol rige de inmediato, no al vencer su token. Un login fallido siempre da
el mismo 401, exista o no el email.

**Token en memoria y refresh token en cookie `HttpOnly`.** El token de acceso dura 15 minutos y
el front lo guarda solo en memoria. La sesión se mantiene con un refresh token opaco (solo se
guarda su hash SHA-256) en una cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`, que
rota en cada uso; si alguien reusa un token ya rotado, se revoca toda la sesión.

**CSRF.** Está desactivado porque el token de acceso viaja en la cabecera `Authorization`, que el
navegador no envía solo. Las dos rutas que sí usan la cookie (`/auth/refresh` y `/auth/logout`)
se protegen con `SameSite=Strict`, la ruta limitada de la cookie y una cabecera propia obligatoria
(`X-Requested-With`), que otro sitio no puede enviar sin que CORS lo bloquee.

**Caché (Ehcache sobre JCache).** Cachea el detalle de cada préstamo, la lista de cada usuario y
el estado de autenticación de cada usuario. Las invalidaciones ocurren solo después del commit,
con los mismos eventos de dominio, así que una transacción revertida no toca la caché. Ninguna
decisión de negocio lee de la caché; en las consultas puede quedar un dato viejo durante una
ventana corta, acotada por un TTL de 2 a 10 minutos.

**Persistencia.** Flyway manda sobre el esquema y Hibernate solo lo valida. H2 en modo
PostgreSQL para correr sin instalar nada; las pruebas de persistencia corren también contra
PostgreSQL 17 real con Testcontainers, y producción usa PostgreSQL.

**Errores.** RFC 9457 con códigos estables para el front. Un préstamo ajeno responde 404 y no 403,
para no revelar que existe.

## Pruebas

| Nivel | Ejemplos |
|---|---|
| Dominio | `LoanTest`, `LoanPolicyTest`, `AmountTest`, `RefreshTokenTest` |
| Casos de uso (Mockito) | `LoanCommandServiceTest`, `UserServiceTest`, `AuthServiceTest`, `RefreshSessionTest` |
| Web y seguridad | `LoanControllerTest`, `GlobalExceptionHandlerTest`, `SecurityIT`, `AuthControllerIT` |
| Flujos completos | `LoanFlowIT` (incluye la aprobación concurrente), `RefreshFlowIT`, `UserAdminIT` |
| PostgreSQL real | `*PostgresIT` con Testcontainers |
| Arquitectura | `ArchitectureTest` (ArchUnit) |

`./mvnw verify` genera además el informe de cobertura en `backend/target/site/jacoco/`.

## Límites conocidos y trabajo futuro

- La regla de "máximo 3 pendientes" puede admitir un cuarto si el mismo usuario envía dos
  solicitudes exactamente a la vez.
- El bus de los avisos en vivo y la caché viven en memoria y sirven a una sola instancia: con
  varias harían falta Redis pub/sub o un broker.
- Sin cálculo de intereses ni cuotas, sin recuperación de contraseña y sin rate limiting en el login.

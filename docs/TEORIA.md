# Parte teórica

Respuestas a las seis preguntas de la parte teórica de la prueba (30 %).

1. [Optimización en sistemas financieros](#1-optimización-en-sistemas-financieros)
2. [Seguridad en APIs financieras](#2-seguridad-en-apis-financieras)
3. [Transacciones en sistemas distribuidos](#3-transacciones-en-sistemas-distribuidos)
4. [Pruebas unitarias y de integración](#4-pruebas-unitarias-y-de-integración)
5. [Front-end](#5-front-end)
6. [Spring Boot](#6-spring-boot)

---

## 1. Optimización en sistemas financieros

> ¿Qué estrategias aplicarías para mejorar el rendimiento y la escalabilidad de una aplicación bancaria que procesa transacciones en tiempo real? Considera concurrencia, caché y patrones de diseño.

**Medir antes de optimizar.** Lo primero es saber dónde está el cuello de botella: métricas con Micrometer (latencia p95/p99, throughput, uso del pool de conexiones), trazas distribuidas con OpenTelemetry y pruebas de carga con Gatling o k6. Optimizar sin datos suele mover el problema de sitio.

### Concurrencia

- **Proteger el saldo sin serializar todo el sistema.** Para cuentas con poca contención uso *bloqueo optimista* (`@Version` en la entidad): no bloquea filas y, si dos operaciones chocan, una falla y se reintenta. Para cuentas muy disputadas uso *bloqueo pesimista* (`SELECT … FOR UPDATE`) solo sobre la fila de la cuenta y por el menor tiempo posible.
- **Evitar deadlocks** bloqueando siempre las cuentas en el mismo orden (por ejemplo, por id ascendente) cuando una operación toca dos cuentas.
- **Particionar por cuenta.** Si las transacciones pasan por una cola (Kafka), uso el número de cuenta como clave de partición: las operaciones de una misma cuenta se procesan en orden y sin competir entre sí, y las de cuentas distintas se procesan en paralelo.
- **Hilos para I/O.** Con Java 21+ los *virtual threads* permiten atender muchas peticiones que esperan a la base de datos o a otros servicios sin agotar el pool de hilos. Los pools que sí se mantienen (conexiones HikariCP, ejecutores) se dimensionan según la capacidad real de la base de datos, no "lo más grande posible".
- **Idempotencia.** Cada transacción lleva una clave de idempotencia; si el cliente reintenta por un timeout, no se cobra dos veces.

### Caché

- **Qué cachear:** datos que se leen mucho y cambian poco: catálogos, tasas, parámetros de productos, perfil del cliente, estado de una solicitud ya resuelta.
- **Qué no cachear como fuente de verdad:** el saldo disponible para autorizar una operación. Ese valor se lee de la base de datos dentro de la transacción. Un saldo en caché solo sirve para *mostrar*, con un TTL corto y sabiendo que puede ir unos segundos atrasado.
- **Dos niveles:** caché local (Caffeine/Ehcache) para lo más caliente y una caché distribuida (Redis) cuando hay varias instancias que deben ver lo mismo.
- **Invalidación por eventos** cuando un dato cambia (`@CacheEvict` o un evento que limpie la entrada en todas las instancias), más un TTL como red de seguridad. Para evitar la *estampida* (muchas peticiones recalculando la misma entrada vencida) se puede usar `@Cacheable(sync = true)` o un bloqueo por clave.

### Base de datos

- Índices pensados para las consultas reales (por cuenta y fecha), paginación por cursor en historiales largos y operaciones en *batch* para cargas masivas.
- Movimientos en un **libro mayor (ledger) de solo inserción**: insertar es más barato y seguro que actualizar, y el saldo se puede reconstruir y auditar.
- **Separar lecturas de escrituras (CQRS):** las consultas pesadas (extractos, reportes) van a réplicas de lectura o a una vista materializada; la base principal queda para escribir.
- Particionar tablas grandes por fecha para que las consultas y el mantenimiento no recorran años de datos.

### Patrones de diseño y arquitectura

- **Servicios sin estado** detrás de un balanceador, para escalar horizontalmente añadiendo instancias.
- **Procesamiento asíncrono:** la API acepta la transacción, la registra y responde rápido (`202 Accepted`); el procesamiento ocurre por eventos. Para publicar esos eventos sin perderlos uso el patrón **Transactional Outbox** (ver pregunta 3).
- **Resiliencia con Resilience4j:** *circuit breaker* hacia servicios externos (por ejemplo, el de antifraude), *bulkhead* para que un servicio lento no consuma todos los hilos, *retry* con backoff exponencial y *jitter*, y *timeouts* explícitos en toda llamada remota.
- **Rate limiting** por cliente para proteger el sistema de picos y abusos.
- Patrones de código como **Strategy** (reglas distintas por tipo de producto o canal) y **Chain of Responsibility** (validaciones encadenadas: límites, antifraude, saldo) mantienen las reglas aisladas y fáciles de probar.

---

## 2. Seguridad en APIs financieras

> ¿Cómo protegerías una API que maneja información sensible de cuentas bancarias contra inyección SQL, CSRF, XSS y otros ataques comunes?

Sigo como guía el **OWASP API Security Top 10** y aplico defensa en profundidad: ninguna capa confía en que la anterior lo hizo bien.

### Inyección SQL

- **Nunca concatenar entrada del usuario en una consulta.** Spring Data JPA, los métodos derivados y JPQL con parámetros (`:id`) generan consultas parametrizadas; si hace falta SQL nativo, también con parámetros enlazados.
- **Validar la entrada** en el borde con Bean Validation (`@NotNull`, `@Positive`, `@Pattern`, tamaños máximos), preferiblemente con listas de lo permitido y no de lo prohibido.
- **Mínimo privilegio en la base de datos:** el usuario de la aplicación no puede borrar tablas ni leer esquemas que no necesita.

### XSS (Cross-Site Scripting)

- La API responde JSON con `Content-Type: application/json` y la cabecera `X-Content-Type-Options: nosniff`, para que el navegador no lo interprete como HTML.
- El front-end escapa por defecto (Angular lo hace en la interpolación); se evita `innerHTML` y `bypassSecurityTrust…` con datos del usuario.
- Una **Content Security Policy** estricta limita de dónde se pueden cargar scripts, de modo que un XSS que se cuele tenga poco alcance.
- Si un texto del usuario se va a mostrar como HTML (por ejemplo, una nota), se sanitiza en el servidor con una lista blanca (OWASP Java HTML Sanitizer).

### CSRF (Cross-Site Request Forgery)

Depende de **cómo viaja la credencial**:

- Si la sesión o el token van en una **cookie**, el navegador la envía sola, así que hay riesgo de CSRF. Se activa la protección de Spring Security (`CookieCsrfTokenRepository.withHttpOnlyFalse()`: Spring deja el token en la cookie `XSRF-TOKEN` y el `HttpClient` de Angular lo devuelve automáticamente en la cabecera `X-XSRF-TOKEN`) y las cookies se marcan `SameSite=Strict` o `Lax`, `HttpOnly` y `Secure`.
- Si el token va en la cabecera **`Authorization: Bearer`** y no hay cookies de autenticación, el navegador no lo adjunta por su cuenta y el CSRF clásico no aplica. En ese caso se puede desactivar la protección CSRF, **dejándolo documentado y justificado**.

### Autenticación y autorización

- **OAuth2 / OpenID Connect** con un servidor de identidad, o JWT propios firmados, de **vida corta** (minutos) y con *refresh tokens* rotados y revocables. Se valida firma, expiración, emisor (`iss`) y audiencia (`aud`).
- Contraseñas con **BCrypt o Argon2**, nunca reversibles. MFA para operaciones sensibles.
- **Autorización por rol** (`@PreAuthorize("hasRole('ADMIN')")`) y, lo más importante en banca, **autorización por objeto**: comprobar que la cuenta o el préstamo pedido pertenece al usuario autenticado. Es el ataque número uno de OWASP API (*Broken Object Level Authorization*): cambiar `/cuentas/123` por `/cuentas/124`.

### Otros ataques y medidas

- **Fuerza bruta y abuso:** rate limiting, bloqueo temporal tras varios intentos fallidos y CAPTCHA en el login.
- **Transporte:** TLS 1.2+ con HSTS; mTLS entre servicios internos.
- **Datos sensibles:** cifrado en reposo, números de cuenta enmascarados en respuestas y logs (`****1234`), secretos en un gestor (Vault, AWS Secrets Manager) y nunca en el repositorio.
- **CORS** restringido a los orígenes del front-end, sin `*`.
- **Errores sin información interna:** respuestas uniformes (`ProblemDetail`, RFC 9457) sin trazas de pila ni mensajes de la base de datos.
- **Asignación masiva:** usar DTOs de entrada en lugar de exponer entidades, para que nadie envíe `"rol": "ADMIN"` o `"saldo": 1000000` en el JSON.
- **Cadena de suministro:** dependencias revisadas con Dependabot u OWASP Dependency-Check, análisis estático (SAST) en el pipeline y pruebas dinámicas (DAST) antes de producción.
- **Auditoría:** registro inmutable de quién hizo qué y cuándo, sobre todo en operaciones de dinero y cambios de permisos.

---

## 3. Transacciones en sistemas distribuidos

> En un sistema bancario distribuido, ¿cómo implementarías la consistencia y el manejo de errores en una API que procesa transferencias entre cuentas en diferentes servicios?

Cuando las cuentas viven en servicios distintos no existe una transacción de base de datos que las abarque. Las transacciones distribuidas clásicas (2PC/XA) bloquean recursos mientras esperan a todos los participantes, acoplan la disponibilidad de los servicios y escalan mal, así que las evito. En su lugar diseño la transferencia como un **proceso con estados explícitos que se completa o se compensa**.

### El flujo: una Saga orquestada

Un servicio de transferencias actúa como **orquestador** y guarda el estado de cada transferencia:

```
SOLICITADA → FONDOS_RESERVADOS → ACREDITADA → COMPLETADA
                    │                 │
                    ▼                 ▼
               RECHAZADA         EN_COMPENSACIÓN → REVERTIDA
```

1. **Reservar fondos** en la cuenta origen (*hold*): el dinero deja de estar disponible pero aún no sale. Si no hay saldo, la transferencia termina en `RECHAZADA` sin efectos secundarios.
2. **Acreditar** en la cuenta destino.
3. **Capturar** la reserva en origen y marcar `COMPLETADA`.
4. Si el paso 2 falla de forma definitiva, se ejecuta la **compensación**: liberar la reserva en origen (`REVERTIDA`).

Prefiero la saga **orquestada** a la coreografiada para dinero porque el estado completo de cada transferencia está en un solo lugar: es más fácil de auditar, de consultar y de reparar.

### Piezas que hacen que funcione

- **Transactional Outbox:** cada servicio guarda el cambio de negocio y el evento a publicar **en la misma transacción local**; un proceso aparte lee la tabla *outbox* y publica en el broker (Kafka, RabbitMQ). Así nunca queda un débito hecho sin su evento, ni un evento sin su débito.
- **Consumidores idempotentes:** la entrega es "al menos una vez", así que cada mensaje lleva un id y el consumidor registra los ya procesados (tabla *inbox*) para ignorar duplicados.
- **Clave de idempotencia en la API:** el cliente envía `Idempotency-Key`; si repite la petición, recibe el mismo resultado y no se crea una segunda transferencia.
- **Respuesta honesta al cliente:** como el proceso es asíncrono, la API responde `202 Accepted` con el id y la URL para consultar el estado, en lugar de fingir que ya terminó.
- **Libro mayor de doble partida:** cada movimiento tiene su contrapartida y la suma siempre da cero, lo que permite detectar descuadres.

### Manejo de errores

- **Errores transitorios** (timeout, servicio caído): reintentos con backoff exponencial y *jitter*, *circuit breaker* para no insistir contra un servicio caído, y timeouts en cada llamada.
- **Errores de negocio** (fondos insuficientes, cuenta bloqueada): no se reintentan; se rechaza o se compensa de inmediato con un motivo claro.
- **Mensajes que fallan una y otra vez** van a una **cola de mensajes muertos (DLQ)** con alerta, para revisión manual sin bloquear el resto.
- **Transferencias atascadas:** un proceso programado detecta las que llevan demasiado tiempo en un estado intermedio y las reintenta o las compensa.
- **Reconciliación periódica** entre los libros de cada servicio, como última red de seguridad.
- **Trazabilidad:** un *correlation id* que viaja en cada llamada y evento, con trazas en OpenTelemetry, para reconstruir qué pasó con cualquier transferencia.

---

## 4. Pruebas unitarias y de integración

> Describe cómo diseñarías una suite de pruebas que asegure la correcta operación de una API bancaria. ¿Qué herramientas utilizarías?

Sigo la **pirámide de pruebas**: muchas pruebas unitarias rápidas sobre las reglas de negocio, un número medio de pruebas por capa y pocas de extremo a extremo. La arquitectura hexagonal ayuda mucho, porque el dominio no depende de Spring ni de la base de datos y se prueba solo.

### Pruebas unitarias (la base)

- **Qué:** reglas de dominio y casos de uso. Por ejemplo: un préstamo solo se puede aprobar o rechazar si está `PENDIENTE`; el monto debe ser positivo y estar dentro de los límites; el plazo debe ser válido; un usuario no puede ver préstamos ajenos.
- **Cómo:** JUnit 5 + AssertJ; Mockito para simular los puertos de salida (repositorios, notificaciones). Sin levantar el contexto de Spring, de modo que corren en milisegundos.
- **Casos límite** con `@ParameterizedTest` (monto 0, negativo, máximo exacto, máximo + 1) y nombres que describen el comportamiento (`noDebePermitirAprobarUnPrestamoYaRechazado`).

### Pruebas por capa (*slices*)

- **Controladores:** `@WebMvcTest` + MockMvc, para comprobar el contrato HTTP: códigos de estado (201, 400, 401, 403, 404, 409), forma del JSON, mensajes de validación y formato de errores.
- **Seguridad:** `spring-security-test` (`@WithMockUser`, `jwt()`): un `USER` que intenta aprobar recibe `403`, una petición sin token recibe `401`, un usuario no ve préstamos de otro.
- **Persistencia:** `@DataJpaTest` para consultas propias, restricciones y bloqueo optimista.

### Pruebas de integración

- `@SpringBootTest` con **Testcontainers** levantando la misma base de datos que en producción (PostgreSQL), porque H2 no se comporta exactamente igual en bloqueos, tipos y SQL nativo.
- Flujos completos: iniciar sesión → solicitar préstamo → aprobar como administrador → consultar estado; comprobar que la caché se invalida tras la aprobación y que una falla a mitad de la aprobación deja todo como estaba (*rollback*).
- **Concurrencia:** dos aprobaciones simultáneas del mismo préstamo; solo una debe ganar.
- **Servicios externos** simulados con **WireMock** (incluyendo timeouts y errores 500, para probar reintentos y *circuit breakers*).
- **Contratos** entre servicios o con el front-end con Spring Cloud Contract o Pact, para que un cambio en la API no rompa a sus consumidores sin enterarnos.

### Calidad de la suite

- **JaCoCo** para cobertura, con umbral en el pipeline, pero mirando sobre todo el dominio; la cobertura sola no garantiza nada.
- **PIT (mutation testing)** sobre las reglas críticas, para comprobar que las pruebas realmente detectan errores.
- Todo corre en **CI (GitHub Actions)** en cada *pull request*; el *merge* se bloquea si algo falla.
- Fuera de la suite principal: pruebas de carga con Gatling o k6 y unas pocas pruebas E2E del front-end con Playwright o Cypress.

---

## 5. Front-end

> En una aplicación bancaria que muestra el saldo de las cuentas, ¿cómo gestionarías el estado y la autenticación en el front-end, garantizando la seguridad y la coherencia de los datos?

Parto de una idea: **el front-end no es una frontera de seguridad**. Todo lo que hace (ocultar botones, guards) mejora la experiencia, pero la autorización real ocurre en el servidor.

### Autenticación

- **Dónde guardar el token:** lo más seguro es que el backend lo entregue en una **cookie `HttpOnly`, `Secure`, `SameSite`**: el JavaScript no puede leerla, así que un XSS no puede robarla (en ese caso se activa la protección CSRF, ver pregunta 2). Si se usan JWT en cabecera, el *access token* se guarda **en memoria** (en un servicio) y el *refresh token* en cookie `HttpOnly`; **nunca en `localStorage`**, que cualquier script puede leer.
- **Interceptor HTTP:** un `HttpInterceptorFn` añade el token a las peticiones hacia la API (solo hacia ella), y ante un `401` intenta renovar el token una vez o cierra la sesión.
- **Guards:** `CanActivateFn` para exigir sesión y para restringir rutas por rol (por ejemplo, la gestión de préstamos solo para `ADMIN`).
- **Cierre de sesión** por inactividad y al expirar el *refresh token*; al cerrar sesión se limpia todo el estado en memoria.

### Estado

- **Una sola fuente de verdad** para cada dato: un servicio con *signals* para casos sencillos, o **NgRx** cuando hay muchas pantallas compartiendo estado y conviene tener acciones trazables.
- **El saldo es estado del servidor, no del cliente.** El front-end nunca lo calcula ni lo modifica localmente (no hay *optimistic updates* con dinero): después de una operación vuelve a pedir el saldo al backend.
- **Mantenerlo fresco:** recargar al volver a la pestaña o reconectarse, polling moderado o actualizaciones por SSE/WebSocket, y mostrar la hora de la última actualización.
- **Evitar condiciones de carrera** con operadores como `switchMap`, para que una respuesta vieja no pise a una nueva.
- **No persistir datos sensibles** en `localStorage` ni `sessionStorage`; los números de cuenta se muestran enmascarados.

### Seguridad en la vista

- Interpolación de Angular (que escapa por defecto), sin `innerHTML` ni `bypassSecurityTrust…` con datos del usuario.
- Content Security Policy, sin datos sensibles en la consola ni en herramientas de analítica.
- Manejo claro de los estados de carga y error, para que el usuario nunca vea un saldo viejo presentado como actual.

---

## 6. Spring Boot

### a. ¿Qué pasa internamente al usar `@SpringBootApplication` y cómo afecta el arranque?

`@SpringBootApplication` es la combinación de tres anotaciones:

| Anotación | Qué hace |
|---|---|
| `@SpringBootConfiguration` | Marca la clase como fuente de configuración (es un `@Configuration`). |
| `@EnableAutoConfiguration` | Activa la autoconfiguración según lo que haya en el classpath. |
| `@ComponentScan` | Busca `@Component`, `@Service`, `@Repository`, `@Controller`, etc. en el paquete de la clase y sus subpaquetes. |

Al ejecutar `SpringApplication.run(...)`, en resumen:

1. **Prepara el `Environment`:** lee propiedades (`application.yml`, variables de entorno, argumentos) y perfiles activos.
2. **Decide el tipo de aplicación** según el classpath (servlet, reactiva o ninguna) y crea el `ApplicationContext` adecuado.
3. **Escanea componentes** propios desde el paquete de la clase principal. Por eso esa clase va en el paquete raíz: lo que esté fuera no se detecta.
4. **Carga las candidatas de autoconfiguración** declaradas en `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` de cada librería y las evalúa con **condiciones**: `@ConditionalOnClass` (¿está la librería?), `@ConditionalOnMissingBean` (¿el desarrollador ya definió este bean?), `@ConditionalOnProperty`, etc. Se procesan **después** de la configuración propia, y por eso un bean que defines tú tiene prioridad.
5. **Refresca el contexto:** crea los beans singleton, aplica los proxies (transacciones, caché, seguridad) y arranca el servidor embebido (Tomcat, Netty…).
6. Ejecuta los `CommandLineRunner` / `ApplicationRunner` y publica `ApplicationReadyEvent`.

**Efecto en el arranque:** cuantas más dependencias haya en el classpath, más autoconfiguraciones se evalúan y más beans se crean. Se puede revisar qué se aplicó y por qué con `--debug` (informe de evaluación de condiciones) o el endpoint `/actuator/conditions`, y quitar lo que sobra. Para arrancar más rápido existen la inicialización perezosa (`spring.main.lazy-initialization`), excluir autoconfiguraciones innecesarias y, en casos extremos, compilación nativa con GraalVM.

### b. ¿Cómo funciona el ciclo de vida de un bean y cómo intervenir en él?

```
Definición (BeanDefinition)
   └─ BeanFactoryPostProcessor        ← puede modificar definiciones antes de crear nada
Instanciación (constructor)
Inyección de dependencias
Callbacks *Aware (BeanNameAware, ApplicationContextAware…)
BeanPostProcessor#postProcessBeforeInitialization
Inicialización: @PostConstruct → InitializingBean#afterPropertiesSet → initMethod
BeanPostProcessor#postProcessAfterInitialization   ← aquí se crean los proxies AOP
Bean listo para usarse
Destrucción: @PreDestroy → DisposableBean#destroy → destroyMethod
```

Detalles importantes:

- Los proxies de `@Transactional`, `@Cacheable` o `@Async` se crean en `postProcessAfterInitialization`. Por eso una llamada a un método `@Transactional` **desde la misma clase** no pasa por el proxy y no abre transacción.
- La destrucción solo se gestiona para beans **singleton**; en `prototype`, Spring crea el bean y se desentiende.
- El **scope** (`singleton`, `prototype`, `request`, `session`) define cuándo nace y muere cada instancia.

**Formas de intervenir**, de la más común a la más avanzada:

- `@PostConstruct` / `@PreDestroy` para inicializar o liberar recursos del propio bean.
- `initMethod` / `destroyMethod` en `@Bean`, útil para clases de terceros que no se pueden anotar.
- `BeanPostProcessor` para actuar sobre muchos beans (envolverlos, validarlos, registrar métricas).
- `BeanFactoryPostProcessor` / `BeanDefinitionRegistryPostProcessor` para cambiar o registrar definiciones antes de que existan los beans.
- `SmartLifecycle` para arrancar o detener componentes en un orden definido (por ejemplo, consumidores de colas).
- Eventos del contexto (`ApplicationReadyEvent`, `ContextClosedEvent`), además de `@Lazy` y `@DependsOn` para controlar cuándo y en qué orden se crean.

### c. ¿Cómo personalizarías la autoconfiguración sin romper «convención sobre configuración»?

La idea es **cambiar solo lo necesario y dejar que Boot siga decidiendo el resto**. En orden de preferencia:

1. **Propiedades** (`application.yml`): la mayoría de autoconfiguraciones exponen sus opciones (`spring.datasource.*`, `spring.jpa.*`, `server.*`, `spring.cache.*`). Es lo primero que hay que intentar.
2. **Customizers:** Boot ofrece interfaces `*Customizer` (por ejemplo, `WebServerFactoryCustomizer`, `CacheManagerCustomizer`, los de Jackson y `RestClientCustomizer`) para ajustar el bean que crea él mismo sin reemplazarlo.
3. **Definir tu propio bean:** como casi toda autoconfiguración usa `@ConditionalOnMissingBean`, si declaras tu `@Bean` del mismo tipo, Boot se retira solo para ese bean y mantiene el resto.
4. **Excluir una autoconfiguración** concreta cuando no se quiere en absoluto: `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)` o `spring.autoconfigure.exclude`.
5. **Escribir una autoconfiguración propia** (o un *starter*) para comportamiento que se repite en varios proyectos: una clase `@AutoConfiguration` con sus condiciones (`@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`), sus opciones en una clase `@ConfigurationProperties`, el orden respecto a otras (`before` / `after`) y registrada en `AutoConfiguration.imports`. Así se sigue la misma convención que Boot: funciona por defecto y quien la use puede sobrescribirla.
6. **Perfiles** (`@Profile`, `application-{perfil}.yml`) para variar la configuración por entorno sin tocar código.

**Lo que evito:** anotaciones que apagan toda una autoconfiguración sin querer, como `@EnableWebMvc`, que desactiva la configuración MVC de Boot completa cuando normalmente basta con implementar `WebMvcConfigurer`. Para comprobar el efecto de cualquier cambio uso el informe de condiciones (`--debug` o `/actuator/conditions`).

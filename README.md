# Spring Microservices — Referencia de Arquitectura

Implementación de referencia de una arquitectura de microservicios con Spring Boot 3.5 y Spring Cloud. Cubre los patrones más habituales en producción: **hexagonal architecture**, **API gateway como BFF**, **OAuth2/OIDC con JWT**, **service discovery**, **circuit breaker** y **configuración centralizada**.

Cada decisión de diseño está documentada con el razonamiento detrás de ella y comparativas con las alternativas. El objetivo no es solo que funcione, sino explicar *por qué* está construido así.

---

## Arquitectura general

```mermaid
graph TD
    Client["Cliente (navegador / curl / tests)"]

    subgraph "Puerto 8090"
        GW["Gateway<br/>(OAuth2 client + resource server)"]
    end

    subgraph "Puerto 9100"
        AS["Auth Server<br/>(OAuth2/OIDC, JWT RSA)"]
    end

    subgraph "Puerto 8761"
        EU["Eureka<br/>(service discovery)"]
    end

    subgraph "Puerto 8888"
        CS["Config Server<br/>(configuración centralizada)"]
    end

    subgraph "Servicios internos"
        PR["products :8081"]
        IT["items :8082"]
        US["users :8083"]
    end

    Client -->|"HTTP (público)"| GW
    GW -->|"login / token"| AS
    GW -->|"lb://product-service"| PR
    GW -->|"lb://items-service"| IT
    GW -->|"lb://users-service"| US
    IT -->|"Feign / WebClient"| PR
    PR & IT & US & GW -->|"registro + heartbeat"| EU
    PR & IT & US -->|"carga config al arrancar"| CS
```

### Patrones implementados

| Patrón | Dónde | Tecnología |
|--------|-------|------------|
| Hexagonal (Ports & Adapters) | `products`, `items`, `users` | Java interfaces (SPI) |
| API Gateway / BFF | `gateway` | Spring Cloud Gateway (WebFlux) |
| OAuth2 Authorization Code + OIDC | `gateway` → `auth-server` | Spring Security oauth2-client |
| Bearer token (resource server) | `gateway`, microservicios | Spring Security oauth2-resource-server |
| Service discovery | todos | Netflix Eureka |
| Client-side load balancing | `gateway`, `items` | Spring Cloud LoadBalancer |
| Circuit breaker | `items`, `gateway` | Resilience4j |
| Configuración centralizada | `items` | Spring Cloud Config |
| API versioning | `products` | Controllers por versión + gateway routing |
| Propagación de tokens | `gateway` → servicios | Filtro `TokenRelay=` |

---

## Módulos

```
spring/
├── starter/        # POM padre con dependencias compartidas
├── commons/        # Infraestructura y utilidades compartidas
├── products/       # Microservicio de productos (puerto 8081)
├── items/          # Microservicio de items (puerto 8082)
├── users/          # Microservicio de usuarios (puerto 8083)
├── eureka-server/  # Servidor de descubrimiento de servicios (puerto 8761)
├── gateway/        # API Gateway (puerto 8090)
├── config-server/  # Servidor centralizado de configuración (puerto 8888)
├── config-repo/    # Repositorio local con los archivos de configuración
└── auth-server/    # Servidor de autorización OAuth2/OIDC (puerto 9100)
```

### `starter`
POM padre del que heredan todos los módulos. Centraliza versiones y dependencias comunes: Spring Boot 3.4.2, Spring Cloud 2024.0.0, MapStruct, Lombok, etc.

### `commons`
Librería compartida con autoconfiguration. Incluye:
- Jerarquía de excepciones de dominio (`EntityNotFoundException`, `ServiceInvocationException`, `UnexpectedException`)
- `ErrorHandler` global (`@RestControllerAdvice`) con respuesta uniforme `ErrorDto`
- Configuración de Feign (`FeignConfig`, `FeignErrorDecoder`)
- Configuración de WebClient (`WebClientConfig`)
- `AuditedEntity`: clase base JPA con campos `createdAt` / `updatedAt` gestionados automáticamente por Hibernate (`@CreationTimestamp`, `@UpdateTimestamp`)

### `users`
Microservicio de gestión de usuarios con CRUD completo sobre MySQL. Implementa registro con contraseña hasheada (BCrypt), roles por defecto y caché de roles estáticos.

| Endpoint | Descripción |
|----------|-------------|
| `POST /users` | Crea un nuevo usuario (devuelve 201) |
| `GET /users` | Lista todos los usuarios |
| `GET /users/{id}` | Obtiene un usuario por ID (404 si no existe) |
| `PUT /users/{id}` | Actualiza email y estado activo de un usuario |
| `DELETE /users/{id}` | Elimina un usuario (204 sin contenido) |

La documentación OpenAPI está disponible en `/swagger-ui.html` una vez arrancado el servicio.

#### Decisiones de diseño

**Separación de comandos por operación**

En lugar de reutilizar el mismo DTO para crear y actualizar, cada operación tiene su propio command object en el dominio:

- `CreateUserCommand` — username, email, password en texto plano
- `UpdateUserCommand` — id, email, active (sin password para evitar sobreescribirla accidentalmente)

Los DTOs de la capa `app` (`CreateUserDto`, `UpdateUserDto`) se mapean a estos commands antes de llegar al servicio. Así el dominio no conoce el formato de entrada HTTP, y la capa de presentación no conoce la lógica interna.

**Contraseñas con BCrypt**

La contraseña nunca se almacena en texto plano. El `UserService` la hashea con `BCryptPasswordEncoder` antes de construir el objeto de dominio. BCrypt es un hash unidireccional: incluye un salt aleatorio embebido en el resultado, por lo que no requiere ninguna clave secreta adicional. Para verificar, BCrypt extrae el salt del hash almacenado y compara — sin necesidad de descifrar.

El campo `password` está presente en el dominio y en la entidad, pero nunca se expone en los DTOs de respuesta (`UserDto`).

**Roles con caché**

Los roles son un catálogo estático. Al crear un usuario se asigna automáticamente `ROLE_USER`. Para evitar consultar la BD en cada creación, `RoleService.getDefaultRoles()` está anotado con `@Cacheable("defaultRoles")`.

La caché vive en `RoleService` (un bean separado de `UserService`) para que el proxy de Spring AOP pueda interceptar la llamada — las llamadas `this.método()` dentro de la misma clase no pasan por el proxy y no activan la caché.

El enum `RoleNames` evita magic strings al buscar los roles por nombre:

```java
roleRepository.findByName(RoleNames.ROLE_USER.name())
```

**Unicidad de username en actualizaciones**

Para verificar que el username no está ya en uso por otro usuario, se usa una query específica en lugar de traer todos los usuarios a memoria:

```java
userRepository.existsByUsernameAndIdNot(username, id)
```

Esto traduce a un `EXISTS` en BD y aprovecha el índice único de `username`.

**Relación con roles**

`UserEntity` tiene una relación `@ManyToMany` con `RoleEntity` a través de la tabla intermedia `user_roles`. Se define con `FetchType.EAGER` por simplicidad, lo que carga siempre los roles junto con el usuario. Para entornos de alto rendimiento, lo recomendable es `LAZY` con `JOIN FETCH` solo cuando se necesiten los roles.

### `products`
Microservicio que expone un CRUD de productos sobre MySQL.

| Endpoint | Descripción |
|----------|-------------|
| `GET /products` | Lista todos los productos |
| `GET /products/{id}` | Obtiene un producto por ID (404 si no existe) |

### `items`
Microservicio que compone items a partir de productos remotos (producto + cantidad). Demuestra distintos mecanismos de comunicación con `products`.

| Endpoint | Descripción |
|----------|-------------|
| `GET /items` | Lista todos los items |
| `GET /items/{id}` | Obtiene un item por ID |

### `eureka-server`
Servidor de descubrimiento de servicios (Netflix Eureka). Los microservicios `products` e `items` se registran en él al arrancar. Debe iniciarse antes que el resto de servicios.

### `config-server`
Servidor centralizado de configuración basado en **Spring Cloud Config Server**. Sirve archivos de configuración a los microservicios al arrancar, de forma que la configuración vive fuera del JAR y puede actualizarse sin redesplegar.

Usa el perfil `native`, que lee los archivos directamente del sistema de ficheros local (el directorio `config-repo/`). En producción se usaría el perfil `git` apuntando a un repositorio Git remoto.

```yaml
# config-server/application.yaml
spring:
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: file:///home/luis/cursos/spring/config-repo
```

La anotación `@EnableConfigServer` en la clase principal activa el servidor:

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication { ... }
```

### `auth-server`

Servidor de autorización OAuth2/OIDC implementado con **Spring Authorization Server**. Emite tokens JWT firmados con RSA que otros microservicios pueden verificar descargando la clave pública del endpoint `/oauth2/jwks`.

Puerto: **9100**

#### Endpoints disponibles

| Endpoint | Descripción |
|----------|-------------|
| `GET /oauth2/jwks` | Clave pública RSA en formato JWK Set (para que los resource servers verifiquen tokens) |
| `GET /oauth2/authorize` | Inicio del flujo Authorization Code — redirige al login si no hay sesión |
| `POST /oauth2/token` | Intercambio de authorization code por access_token, id_token y refresh_token |
| `GET /userinfo` | Devuelve datos del usuario autenticado (requiere access_token con scope `openid`) |
| `GET /.well-known/oauth-authorization-server` | Metadatos del servidor (issuer, endpoints, algoritmos) |

#### Flujo de prueba (Authorization Code + OIDC)

```
1. Abre en el navegador:
   http://localhost:9100/oauth2/authorize?response_type=code
     &client_id=gateway&scope=openid
     &redirect_uri=http://localhost:8090/authorized

2. Introduce las credenciales (user/user o admin/admin)

3. Copia el parámetro `code` de la URL de redirección

4. Intercambia el code por tokens:
   curl -X POST http://localhost:9100/oauth2/token \
     -H "Authorization: Basic Z2F0ZXdheTpzZWNyZXQ=" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "grant_type=authorization_code&code=CODE&redirect_uri=http://localhost:8090/authorized"
```

> `Z2F0ZXdheTpzZWNyZXQ=` es el Base64 de `gateway:secret`

#### Flujos de peticiones

**Login correcto**

```mermaid
sequenceDiagram
    participant B as Navegador
    participant C1 as Chain 1<br/>(OAuth2 endpoints)
    participant C2 as Chain 2<br/>(Form login)
    participant UDS as UserDetailsService

    B->>C1: GET /oauth2/authorize?response_type=code&client_id=oidc-client&...
    C1->>C1: AuthorizationFilter detecta anónimo → AccessDeniedException
    C1->>C1: ExceptionTranslationFilter guarda petición en sesión
    C1-->>B: 302 /login

    B->>C2: GET /login
    C2-->>B: 200 formulario con _csrf token

    B->>C2: POST /login {username, password, _csrf}
    C2->>UDS: loadUserByUsername("user")
    UDS-->>C2: UserDetails {password: "{noop}user"}
    C2->>C2: DaoAuthenticationProvider: passwords coinciden ✓
    C2->>C2: SavedRequestAwareAuthenticationSuccessHandler
    C2-->>B: 302 /oauth2/authorize?...&continue

    B->>C1: GET /oauth2/authorize?...&continue
    C1->>C1: OAuth2AuthorizationEndpointFilter: valida client, scope, redirect_uri
    C1->>C1: genera authorization code
    C1-->>B: 302 http://localhost:8080/authorized?code=ABC123

    Note over B: Si no hay app en :8080, el código<br/>aparece en la barra de direcciones

    B->>C1: POST /oauth2/token {grant_type=authorization_code, code=ABC123, ...}
    Note over B,C1: normalmente lo hace la app cliente, no el navegador
    C1->>C1: OAuth2TokenEndpointFilter: valida code y client secret
    C1->>C1: genera JWT firmado con clave RSA privada
    C1-->>B: 200 {access_token, id_token, refresh_token}
```

**Login con credenciales incorrectas**

```mermaid
sequenceDiagram
    participant B as Navegador
    participant C1 as Chain 1<br/>(OAuth2 endpoints)
    participant C2 as Chain 2<br/>(Form login)
    participant UDS as UserDetailsService

    B->>C1: GET /oauth2/authorize?response_type=code&...
    C1->>C1: AuthorizationFilter detecta anónimo → AccessDeniedException
    C1->>C1: ExceptionTranslationFilter guarda petición en sesión
    C1-->>B: 302 /login

    B->>C2: GET /login
    C2-->>B: 200 formulario con _csrf token

    B->>C2: POST /login {username="user", password="INCORRECTA", _csrf}
    C2->>UDS: loadUserByUsername("user")
    UDS-->>C2: UserDetails {password: "{noop}user"}
    C2->>C2: DaoAuthenticationProvider: passwords NO coinciden ✗
    C2->>C2: BadCredentialsException
    C2->>C2: SimpleUrlAuthenticationFailureHandler
    C2-->>B: 302 /login?error

    B->>C2: GET /login?error
    C2-->>B: 200 formulario con mensaje de error

    Note over B: La petición original sigue guardada<br/>en sesión — el usuario puede reintentar
```

#### Decisiones de diseño y aprendizajes

**Dos SecurityFilterChain con `@Order`**

El servidor de autorización necesita dos cadenas de seguridad distintas:

- **Chain 1 (`@Order(1)`)** — protege los endpoints OAuth2/OIDC (`/oauth2/authorize`, `/oauth2/token`, etc.). Usa `securityMatcher` para aplicarse solo a esas rutas.
- **Chain 2 (`@Order(2)`)** — protege el resto: activa el formulario de login en `/login` y exige autenticación al resto de rutas.

Sin `@Order`, Spring no garantiza qué cadena se aplica primero y el comportamiento es impredecible.

**`authorizeHttpRequests` en chain 1 es obligatorio**

Sin `.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())` en la primera cadena, un usuario anónimo accediendo a `/oauth2/authorize` no lanza `AccessDeniedException`. La petición llega al servlet sin respuesta, Tomcat la reenvía a `/error` y el usuario nunca ve el formulario de login.

**`server.servlet.session.tracking-modes: cookie`**

Por defecto, Tomcat añade `;jsessionid=...` a las URLs de redirección cuando aún no hay cookie de sesión. Spring Security's `StrictHttpFirewall` rechaza esas URLs porque contienen `;`. La solución es forzar que el tracking de sesión sea solo por cookie.

**`applyDefaultSecurity` deprecated en 1.4**

La llamada `OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http)` está deprecated desde Spring Authorization Server 1.4. La forma actual es usar `OAuth2AuthorizationServerConfigurer.authorizationServer()` directamente con `.with()`:

```java
OAuth2AuthorizationServerConfigurer configurer = OAuth2AuthorizationServerConfigurer.authorizationServer();
http
    .securityMatcher(configurer.getEndpointsMatcher())
    .with(configurer, server -> server.oidc(Customizer.withDefaults()))
    ...
```

**Clave RSA generada en memoria**

La clave privada RSA que firma los JWT se genera al arrancar. Esto implica que cada reinicio del servidor invalida todos los tokens existentes. En producción la clave debe persistirse (KeyStore, Vault, etc.).

**`oauth2-client` en el gateway en lugar de un endpoint manual**

La alternativa didáctica (implementar `/authorized` a mano para entender el flujo) tiene inconvenientes que la hacen inviable en producción:

| | Endpoint `/authorized` manual | `oauth2-client` (implementación actual) |
|--|--|--|
| Validación del parámetro `state` (anti-CSRF) | ❌ No | ✅ Automática |
| Almacenamiento de tokens en sesión | ❌ No | ✅ Automático |
| Renovación automática del token (refresh) | ❌ No | ✅ Automática |
| Propagación del token a microservicios | ❌ Manual | ✅ Filtro `TokenRelay` |
| Credenciales en el código | ❌ Hardcodeadas | ✅ En `application.yaml` |

Con `spring-boot-starter-oauth2-client`, el endpoint `/authorized` desaparece — Spring Security registra internamente `/login/oauth2/code/{registrationId}` sin ningún controller. La configuración del gateway es la que se describe en la sección anterior.

**Cliente OAuth2 vs Resource Server — dos roles distintos**

En OAuth2 hay dos roles que no deben confundirse:

- **Cliente OAuth2** — gestiona el flujo de login: redirige al usuario al auth-server, recibe el authorization code de vuelta e intercambia ese code por tokens. Es el punto de entrada. En este proyecto, **solo el gateway** actúa como cliente OAuth2.

- **Resource Server** — valida el JWT en cada petición protegida. No participa en el flujo de login: simplemente extrae el token del header `Authorization: Bearer ...` y verifica su firma usando la clave pública del auth-server (descargada de `/oauth2/jwks`). Cada microservicio interno debería ser un resource server.

```yaml
# lo que iría en cada microservicio para proteger sus endpoints
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9100
```

**¿Qué pasaría sin gateway?** Sin BFF, el flujo de login (cliente OAuth2) lo gestionaría el frontend (React, Angular...) directamente. El token quedaría expuesto a JavaScript en el navegador — menos seguro. Cada microservicio seguiría siendo resource server. Con el gateway como BFF, los tokens se almacenan en la sesión del servidor y el navegador nunca los ve.

**Patrón BFF — gateway como único cliente OAuth2**

Es habitual registrar el gateway como el único cliente OAuth2 registrado en el auth server, con las `redirectUri` apuntando a su puerto. Los microservicios internos no participan en el flujo de login — solo reciben tokens ya validados.

El filtro `TokenRelay=` en cada ruta del gateway propaga automáticamente el access token al microservicio destino en el header `Authorization`:

```
Navegador → Gateway :8090 → auth-server :9100  (flujo OAuth2: login, code, token)
Navegador → Gateway :8090 → microservicio       (petición + Authorization: Bearer <token>)
Microservicio valida el JWT localmente con la clave pública de /oauth2/jwks
```

| | Gateway como cliente único (BFF) | Frontend como cliente directo |
|--|--|--|
| Los tokens llegan al navegador | No (más seguro) | Sí (expuestos a JS) |
| Los microservicios saben de OAuth2 | No | No (siguen siendo resource servers) |
| Las `redirectUri` cambian al añadir servicios | No | No |
| Complejidad de configuración | Centralizada en el gateway | En el frontend |
| Habitual en producción | Sí (apps tradicionales) | Sí (SPAs con PKCE) |

**Páginas de error y seguridad**

Las trazas de excepción Java no deben exponerse al exterior — revelan la estructura interna del sistema. Se configuran tres propiedades:
```yaml
server.error.include-stacktrace: never
server.error.include-message: never
server.error.whitelabel.enabled: false
```
Y se crean páginas HTML estáticas en `static/error/4xx.html` y `static/error/5xx.html`.

### `config-repo`
Directorio que actúa como repositorio de configuración. Cada archivo `{nombre-servicio}.yaml` contiene la configuración externalizada de ese servicio. El Config Server sirve el archivo cuyo nombre coincida con el `spring.application.name` del cliente.

```
config-repo/
└── items.yaml    # configuración externalizada del servicio items
```

### `gateway`
Punto de entrada único para todos los clientes externos. Implementado con **Spring Cloud Gateway** (basado en WebFlux) en el puerto 8090. Cumple tres roles a la vez:

1. **OAuth2 client (BFF)** — gestiona el flujo de login: redirige al auth-server, recibe el authorization code, intercambia el code por tokens y mantiene la sesión. El navegador nunca ve los tokens.
2. **OAuth2 resource server** — acepta Bearer tokens en el header `Authorization` para clientes automatizados (tests, CI, scripts).
3. **Proxy inteligente** — enruta peticiones a los microservicios con load balancing (Eureka), strip de prefijos y circuit breaker.

| Ruta pública (gateway:8090) | Se redirige a | Filtros activos |
|-----------------------------|---------------|-----------------|
| `GET /api/products/**`      | `product-service :8081` | StripPrefix, TokenRelay, CircuitBreaker |
| `GET /api/items/**`         | `items-service :8082`   | StripPrefix, TokenRelay |
| `GET /api/users/**`         | `users-service :8083`   | StripPrefix, TokenRelay |

El filtro `TokenRelay=` propaga automáticamente el access token de la sesión al microservicio destino en el header `Authorization: Bearer ...`. El filtro `StripPrefix=1` elimina el segmento `/api` antes de reenviar, de modo que `/api/products/1` llega al servicio como `/products/1`.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          gateway:
            client-id: gateway
            client-secret: secret
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: openid, profile
        provider:
          gateway:
            issuer-uri: http://localhost:9100  # descarga metadatos OIDC automáticamente
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9100    # valida Bearer tokens
```

```java
@Bean
public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http
        .authorizeExchange(exchanges -> exchanges
            .pathMatchers("/logged-out").permitAll()
            .anyExchange().authenticated())
        .oauth2Login(Customizer.withDefaults())           // flujo navegador
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(...)) // flujo API / CI
        .build();
}
```

Spring Security evalúa primero si hay un Bearer token en el header; si no, usa la sesión de login. Ambos modos coexisten sin conflicto.

**¿Dónde iría esta configuración sin gateway?**

El gateway concentra dos roles OAuth2 distintos. Sin él, cada configuración va a un sitio diferente:

| Configuración | Con gateway (este proyecto) | Sin gateway |
|---|---|---|
| `oauth2.client` | Solo en el gateway (BFF) | En el frontend con PKCE (SPA) o en cada servicio expuesto al navegador |
| `oauth2.resourceserver` | En el gateway + idealmente en cada microservicio | En cada microservicio |

Los microservicios de este proyecto no tienen `resourceserver` configurado — confían en que el gateway ya validó el token. Eso simplifica el código, pero en producción cada microservicio debería validar el JWT de forma independiente (defensa en profundidad): si alguien accede directamente al puerto interno saltándose el gateway, no habría ninguna validación.

---

## Arquitectura hexagonal

Cada servicio sigue la estructura:

```
app/          → Capa de entrada (controllers, DTOs, mappers de presentación)
domain/       → Núcleo (modelos, servicios, interfaces SPI)
infra/        → Adaptadores (persistencia, clientes HTTP, etc.)
```

Las interfaces SPI (`domain/spi/`) desacoplan el dominio de la infraestructura, permitiendo sustituir implementaciones sin tocar la lógica de negocio.

---

## Implementaciones alternativas

Una de las metas del proyecto es comparar distintas formas de resolver el mismo problema.

### Comunicación entre servicios (`items` → `products`)

Ambas implementaciones se conectan a través del mismo puerto SPI (`ProductService`), por lo que cambiar de una a otra no requiere modificar el dominio. Se selecciona con la propiedad `clients.products.type` en `application.yaml`:

| Implementación | Clase | `clients.products.type` |
|----------------|-------|--------|
| **Spring Cloud OpenFeign** | `ProductServiceFeignAdapter` | `feign` |
| **Spring WebFlux WebClient** | `ProductServiceWebClient` | `webclient` |

> `ProductServiceWebClient` usa `.block()` para adaptarse a la interfaz síncrona, lo que rompe la naturaleza reactiva de WebClient pero permite intercambiarlo con Feign sin cambiar el dominio.

> **¿Afecta al gateway que `items` use Feign en vez de WebClient?** No. El gateway es reactivo en cuanto a cómo gestiona sus propias conexiones, pero se comunica con los servicios downstream mediante HTTP normal. No comparte hilo ni contexto reactivo con `items`: simplemente reenvía la petición y espera la respuesta. El cliente HTTP que `items` use internamente para llamar a `products` es un detalle de implementación completamente transparente para el gateway. El problema de `.block()` es interno a `items`: si el servicio fuera completamente reactivo (Spring WebFlux), bloquear un hilo sería problemático. Pero eso no tiene nada que ver con el gateway.

#### Feign vs WebClient

**Feign** es un cliente **declarativo**: defines una interfaz con anotaciones y Spring genera la implementación. No escribes código de llamada HTTP, solo el contrato:

```java
@FeignClient(name = "product-service", path = "/products")
public interface ProductServiceFeignClient {
    @GetMapping
    List<ProductDto> findAll();

    @GetMapping("/{id}")
    ProductDto findById(@PathVariable Long id);
}
```

**WebClient** es un cliente **programático y reactivo**: construyes la petición paso a paso. Está diseñado para programación non-blocking, aunque aquí se usa `.block()` para adaptarlo a la interfaz síncrona del dominio:

```java
return clientBuilder.build().get()
    .uri("http://product-service/products")
    .retrieve()
    .bodyToFlux(ProductDto.class)
    .collectList()
    .block(); // rompe la reactividad para cumplir la interfaz síncrona
```

| Aspecto | Feign | WebClient |
|---|---|---|
| Estilo | Declarativo (interfaz + anotaciones) | Programático (fluent API) |
| Modelo de ejecución | Síncrono (bloqueante) | Reactivo (non-blocking) por defecto |
| Verbosidad | Mínima | Mayor |
| Control de la petición | Limitado | Total (headers, timeouts, retry…) |
| Streaming / SSE | No soporta bien | Soporte nativo (`bodyToFlux`) |
| Manejo de errores | Vía `ErrorDecoder` | Vía `.onStatus()` / operadores reactivos |

**¿Cuándo usar cada uno?** Feign es más simple y legible para apps Spring MVC clásicas. WebClient es la opción recomendada para apps reactivas (Spring WebFlux), cuando se necesita streaming, o cuando se requiere control fino sobre timeouts y reintentos. Spring Framework 6 introdujo `@HttpExchange` como alternativa declarativa nativa a Feign, sin dependencias adicionales de Spring Cloud.

#### `@HttpExchange` — cliente declarativo nativo (Spring Framework 6+)

Similar a Feign pero sin dependencias de Spring Cloud. Se define una interfaz anotada y Spring genera el cliente a partir de un `RestClient` (síncrono) o `WebClient` (reactivo):

```java
// definición del cliente — igual que Feign
@HttpExchange("/products")
public interface ProductServiceHttpClient {

    @GetExchange
    List<ProductDto> findAll();

    @GetExchange("/{id}")
    ProductDto findById(@PathVariable Long id);
}
```

```java
// registro del bean — aquí se elige el transporte
@Configuration
public class HttpClientConfig {

    @Bean
    public ProductServiceHttpClient productServiceHttpClient() {
        RestClient restClient = RestClient.builder()
            .baseUrl("http://product-service")
            .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build();
        return factory.createClient(ProductServiceHttpClient.class);
    }
}
```

La interfaz es idéntica a la de Feign; la diferencia está en el bean de configuración. Sustituyendo `RestClientAdapter` por `WebClientAdapter` el mismo cliente pasa a ser reactivo sin cambiar la interfaz.

### Balanceo de carga y descubrimiento de servicios

Se usa **Netflix Eureka** como servidor de descubrimiento y **Spring Cloud LoadBalancer** para el balanceo. Los servicios se identifican por nombre lógico (`product-service`) sin necesidad de configurar URLs concretas.

Para levantar varias instancias de `products` y ver el balanceo en acción:

```bash
# Instancia 1 (puerto por defecto 8081)
./mvnw spring-boot:run -pl products

# Instancia 2
./mvnw spring-boot:run -pl products -Dspring-boot.run.arguments=--server.port=8091
```

#### Con Eureka vs sin Eureka

Tanto Feign como WebClient siempre usan el **nombre lógico** del servicio — ese código no cambia. Lo que cambia es quién resuelve ese nombre: un yaml estático o el registro de Eureka.

**Sin Eureka — opción A: URL fija** (sin balanceo):
```yaml
# items/application.yaml
clients:
  products:
    url: http://localhost:8081/products
```
```java
@FeignClient(name = "product-service", url = "${clients.products.url}")
```
Una sola instancia hardcodeada. Si cae, no hay fallback.

**Sin Eureka — opción B: LoadBalancer simple con instancias fijas**:
```yaml
# items/application.yaml
spring:
  cloud:
    discovery:
      client:
        simple:
          instances:
            product-service:
              - uri: http://localhost:8081
              - uri: http://localhost:8091
```
El cliente usa el nombre lógico y Spring LoadBalancer reparte entre las URIs configuradas. Pero la lista es estática: añadir una instancia nueva requiere tocar el yaml y reiniciar.

**Con Eureka — descubrimiento dinámico:**

Los servicios se registran al arrancar y se dan de baja al parar. El cliente consulta el registro en cada llamada.

Configuración del servidor (`eureka-server/application.yaml`):
```yaml
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false   # es el servidor, no se registra a sí mismo
    fetch-registry: false
```

Configuración de cada microservicio:
```yaml
spring:
  application:
    name: product-service         # nombre con el que se registra en Eureka
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
```

Para que `WebClient` pueda resolver nombres de Eureka a través del LoadBalancer, el bean necesita `@LoadBalanced` (en `WebClientConfig` del módulo `commons`):
```java
@Bean
@LoadBalanced
public WebClient.Builder webClientBuilder() { ... }
```

| Aspecto | Sin Eureka (estático) | Con Eureka (dinámico) |
|---|---|---|
| Registro de instancias | Manual en `application.yaml` | Automático al arrancar |
| Añadir/quitar instancias | Requiere cambiar config y reiniciar | En caliente, sin tocar config |
| Detección de caídas | No (el balanceador simple no redirige en error) | Sí (heartbeat periódico) |
| Infraestructura extra | Ninguna | Requiere levantar `eureka-server` |
| Útil en entorno local/simple | Sí | Añade complejidad innecesaria |
| Útil en producción / múltiples instancias | No escala | Diseñado para esto |

### Resiliencia: Circuit Breaker

Un **circuit breaker** protege a un servicio de fallos en cascada: si el servicio al que llamas empieza a fallar o a ir lento, el circuit breaker se "abre" y devuelve una respuesta alternativa sin esperar — evitando que el hilo quede bloqueado y que el problema se propague.

Hay tres estados:

| Estado | Qué ocurre |
|--------|-----------|
| **Closed** (normal) | Las llamadas pasan. Se cuentan los fallos. |
| **Open** (circuito abierto) | Las llamadas se cortan inmediatamente. Se ejecuta el fallback. |
| **Half-open** (prueba) | Se dejan pasar unas pocas llamadas para ver si el servicio se recuperó. |

En este proyecto hay dos enfoques implementados.

#### Enfoque 1 — `@CircuitBreaker` en `items` (anotación Resilience4j)

Protege la llamada que hace `items` al servicio `products`. Se configura a nivel de método con la anotación de Resilience4j:

```java
// ItemController.java
@CircuitBreaker(name = "itemService", fallbackMethod = "fallbackItem")
@GetMapping("/{id}")
public ItemDto findById(@PathVariable Long id) {
    return itemService.findById(id)
        .map(mapper::toDto)
        .orElseThrow(() -> new EntityNotFoundException("Item not found with id: %s", id));
}

private Optional<Item> fallbackItem(Long id, Throwable throwable) {
    // devuelve un item por defecto cuando el circuit breaker está abierto o hay error
    return Optional.of(new Item(new Product(id, "Default Product on error", BigDecimal.ZERO, ...), 0));
}
```

La instancia `itemService` referencia la configuración `default` definida en `commons`:

```yaml
# items/application.yaml
resilience4j:
  circuitbreaker:
    instances:
      itemService:
        base-config: default
  timelimiter:
    instances:
      itemService:
        base-config: default
```

#### Enfoque 2 — Filtro `CircuitBreaker` en el gateway

Protege la ruta hacia `product-service` directamente desde el gateway, antes de que la petición llegue al servicio. No requiere cambiar código en `items` ni en `products`:

```yaml
# gateway/application.yaml
spring:
  cloud:
    gateway:
      server.webflux.routes:
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/products/**
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: productService
                statusCodes: "500,502,503,504"
```

El nombre `productService` referencia la instancia Resilience4j configurada en `gateway/application.yaml`. Los `statusCodes` indican qué respuestas HTTP se contabilizan como fallos.

#### Configuración compartida (`commons`)

Los valores por defecto del circuit breaker están centralizados en `commons/src/main/resources/resilience4j-defaults.yaml` y cada servicio los importa con una línea:

```yaml
# en el application.yaml de cada servicio
spring:
  config:
    import: classpath:resilience4j-defaults.yaml
```

```yaml
# commons/resilience4j-defaults.yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        register-health-indicator: true
        sliding-window-size: 10        # ventana de las últimas N llamadas
        minimum-number-of-calls: 5     # mínimo de llamadas antes de evaluar
        failure-rate-threshold: 50     # % de fallos para abrir el circuito
        wait-duration-in-open-state: 10000  # ms en estado Open antes de pasar a Half-open
  timelimiter:
    configs:
      default:
        timeout-duration: 2000         # ms máximo de espera por respuesta
```

> El gateway tiene su propia copia de estos valores en `gateway/application.yaml` porque no usa el módulo `commons` como dependencia.

#### Simulación de errores en `products`

`ProductController` incluye dos endpoints de prueba para disparar el circuit breaker sin necesidad de infraestructura caída:

| Petición | Comportamiento |
|----------|---------------|
| `GET /products/10` (o vía gateway `/api/products/10`) | Lanza `RuntimeException` — cuenta como fallo |
| `GET /products/11` (o vía gateway `/api/products/11`) | Duerme 5s — supera el timeout de 2s y cuenta como fallo |

Haciendo suficientes llamadas fallidas (`minimum-number-of-calls: 5`, `failure-rate-threshold: 50%`) el circuito se abre y las siguientes llamadas devuelven el fallback directamente sin llegar a `products`.

#### Comparativa de enfoques

| Aspecto | `@CircuitBreaker` en `items` | Filtro en gateway |
|---------|------------------------------|-------------------|
| Dónde actúa | Dentro del servicio llamante | En el punto de entrada de red |
| Granularidad | Por método Java | Por ruta HTTP |
| Fallback | Lógica Java arbitraria | Solo redirección o respuesta de error |
| Requiere cambiar el servicio | Sí | No |
| Útil para | Proteger una dependencia concreta con lógica de negocio | Proteger toda una ruta sin tocar código |

Ambos pueden coexistir: el gateway puede abrir primero el circuito para peticiones externas, mientras que `items` tiene su propio circuit breaker para las llamadas internas que hace a `products`.

---

### Versionado de APIs

El versionado aplica al **contrato público** de un servicio — los endpoints que expone a otros servicios o al exterior. No toda evolución de la API requiere una nueva versión.

#### Cuándo versionar

| Tipo de cambio | ¿Requiere nueva versión? |
|----------------|--------------------------|
| Eliminar un campo o endpoint | Sí (breaking) |
| Cambiar el tipo o semántica de un campo | Sí (breaking) |
| Añadir un campo opcional | No |
| Añadir un endpoint nuevo | No |

#### Cómo versionar: versión en la URL

La opción más común y explícita:

```
GET /v1/products
GET /v2/products
```

Alternativas menos habituales: header `Accept: application/vnd.myapi.v2+json` o query param `?version=2`. La URL es preferible porque es visible, cacheable y no requiere que el cliente manipule headers.

#### Organización del código

Cada versión tiene sus propios DTOs y controller. El dominio interno no cambia — el versionado es solo una preocupación de la capa de presentación:

```
app/
  controllers/
    v1/ProductController.java   →  @RequestMapping("/v1/products")
    v2/ProductController.java   →  @RequestMapping("/v2/products")
dto/
  v1/ProductDto.java
  v2/ProductDto.java            # puede tener campos distintos
```

Ambos controllers delegan en el mismo `ProductService`. Solo difieren en cómo mapean la respuesta:

```java
// v1
public v1.ProductDto findById(...) {
    return v1Mapper.toDto(productService.findById(id));
}

// v2 — mismo servicio de dominio, distinto mapper/DTO
public v2.ProductDto findById(...) {
    return v2Mapper.toDto(productService.findById(id));
}
```

#### Contrato como artifact: `products-api`

En un ecosistema de microservicios, una práctica recomendable es publicar el contrato de cada servicio como un módulo independiente (`products-api`). Los consumidores dependen de ese artifact, no del servicio completo:

```
products-api:1.0  →  v1.ProductDto  (consumidores en v1)
products-api:2.0  →  v2.ProductDto  (consumidores migrados a v2)
```

Esto permite que cada consumidor migre a su ritmo, y que el equipo de `products` mantenga ambas versiones activas durante la transición.

`products-api` solo contiene DTOs y opcionalmente la interfaz del cliente Feign. Las entidades de dominio (`Product`) nunca deben compartirse — cada servicio tiene su propia visión del dominio.

#### Migración de no-versionado a versionado

Cuando una API empieza sin versión en la URL (`/products`) y se introduce la primera versión con breaking changes, hay dos opciones:

- **No añadir `/v1` retroactivamente**: `/products` sigue funcionando como versión implícita y se publica `/v2/products`. Los consumidores existentes no tocan nada.
- **Redirigir `/products` → `/v2/products`**: más limpio a largo plazo. La URL sin versión queda como alias de la versión vigente.

La segunda opción es preferible, y la redirección debe hacerse en el **gateway**, no en el servicio. El servicio solo expone `/products` — es el gateway quien gestiona el contrato público y las reglas de routing:

```yaml
routes:
  # ruta activa: /api/v2/products/** → product-service
  - id: products-v2
    uri: lb://product-service
    predicates:
      - Path=/api/v2/products/**
    filters:
      - StripPrefix=2

  # redirección permanente: /api/products/** → /api/v2/products/**
  - id: products-legacy
    uri: no://op
    predicates:
      - Path=/api/products/**
    filters:
      - RewritePath=/api/products/(?<segment>.*), /api/v2/products/${segment}
      - RedirectTo=301, https://gateway-host
```

El `301` indica redirección permanente, lo que permite a los consumidores y proxies intermedios actualizar la URL cacheada. Centralizar esto en el gateway significa que si en el futuro `/v2` también queda obsoleto, el cambio es solo de configuración, sin tocar código de negocio.

#### Cuándo empezar a versionar

No versionar desde el día uno. Añadir `v2` solo cuando haya un cambio breaking real con consumidores activos. El sobreingeniería de versiones antes de tener consumidores es tiempo perdido — y cada versión nueva obliga a reimplementar todos los endpoints del recurso aunque solo haya cambiado uno.

---

### Configuración centralizada: Spring Cloud Config

En lugar de que cada microservicio tenga toda su configuración en su propio `application.yaml`, **Spring Cloud Config** permite centralizar los valores en un servidor externo. Los servicios consultan ese servidor al arrancar y cargan su configuración desde allí.

Esto resuelve varios problemas habituales en microservicios:
- Cambiar un valor (una URL, un timeout) en 10 servicios requeriría redesplegar 10 JARs.
- Los archivos de configuración con credenciales no deberían estar en el código fuente.
- Es difícil saber qué configuración está activa en producción en un momento dado.

#### Cómo funciona

```
items-service :8082
    │
    │  al arrancar: "dame la configuración de 'items-service'"
    ▼
config-server :8888
    │
    │  busca items.yaml en config-repo/
    ▼
config-repo/items.yaml  (sistema de ficheros local)
```

El nombre del archivo en `config-repo/` debe coincidir exactamente con el `spring.application.name` del microservicio cliente.

#### Configuración del servidor

```yaml
# config-server/application.yaml
server:
  port: 8888
spring:
  profiles:
    active: native          # lee del sistema de ficheros (no de Git)
  cloud:
    config:
      server:
        native:
          search-locations: file:///ruta/a/config-repo
```

> El perfil `native` es conveniente en local. En producción se usa el perfil `git` apuntando a un repositorio remoto, lo que añade historial de cambios, ramas por entorno y auditoría.

#### Configuración del cliente

Cada microservicio declara en su `application.yaml` de dónde importar la configuración:

```yaml
spring:
  application:
    name: items-service     # debe coincidir con el nombre del archivo en config-repo
  config:
    import: optional:configserver:http://localhost:8888
```

El prefijo `optional:` hace que el servicio arranque igualmente aunque el Config Server no esté disponible, usando los valores locales como fallback. Sin él, el servicio falla al arrancar si el servidor no responde.

> **Forma alternativa (clásica, pre-Spring Boot 2.4):** algunos proyectos usan `spring.cloud.config.uri: http://localhost:8888`. Es equivalente pero no soporta el prefijo `optional:`, por lo que el servicio siempre falla si el servidor no está disponible.

#### Endpoint de refresco en caliente

Sin reiniciar el servicio, es posible recargar la configuración llamando al endpoint de Actuator `/actuator/refresh` con un POST:

```bash
curl -X POST http://localhost:8082/actuator/refresh
```

Para que este endpoint esté disponible hay que exponerlo en `application.yaml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, refresh
```

Y los beans que usen propiedades del Config Server deben anotarse con `@RefreshScope` para que Spring los recree con los nuevos valores:

```java
@RestController
@RefreshScope
public class ItemsController { ... }
```

Sin `@RefreshScope`, el endpoint responde 200 pero los beans ya construidos conservan los valores anteriores.

#### Consultar la configuración directamente

El Config Server expone un endpoint REST para verificar qué configuración serviría a un cliente:

```
GET http://localhost:8888/{application}/{profile}
```

Ejemplos:

| URL | Qué devuelve |
|-----|-------------|
| `http://localhost:8888/items-service/default` | Configuración de `items-service` con perfil `default` |
| `http://localhost:8888/items-service/prod` | Configuración con perfil `prod` (si existe `items-service-prod.yaml`) |

---

### Gateway + Eureka: cómo encajan

El gateway incluye `spring-cloud-starter-netflix-eureka-client`, por lo que **también es un cliente de Eureka**: al arrancar se suscribe al registro y mantiene una caché local que se refresca periódicamente mediante heartbeats.

Cuando llega una petición al gateway, el flujo completo es:

```
Cliente HTTP
    │
    │  GET /api/products/1
    ▼
Gateway :8090
    │
    ├─ 1. el predicado Path=/api/products/** coincide → ruta product-service
    ├─ 2. StripPrefix=1 elimina /api  →  uri destino: lb://product-service/products/1
    ├─ 3. Spring Cloud LoadBalancer consulta la caché local del registro de Eureka
    │         │
    │         │  heartbeats periódicos
    │         ▼
    │    Eureka :8761 ←── product-service :8081
    │                 ←── product-service :8091  (si hay segunda instancia)
    │
    ├─ 4. LoadBalancer elige una instancia (round-robin por defecto)
    │
    │  GET /products/1
    ▼
product-service :8081
```

Puntos clave:

- **El cliente externo solo conoce el gateway** (`:8090`). Los puertos internos 8081/8082 quedan ocultos.
- **El balanceo es client-side**: el gateway resuelve el nombre lógico localmente usando su caché de Eureka, no hay ningún proxy centralizado entre él y los servicios.
- **Si se levanta una segunda instancia** de `products` en el puerto 8091, se registra en Eureka y el gateway empieza a repartir tráfico hacia ella sin ningún cambio de configuración.
- El gateway no forma parte del módulo `commons` ni usa `@LoadBalanced` en un `WebClient` propio — el routing reactivo de Spring Cloud Gateway tiene su propia integración con el LoadBalancer mediante el prefijo `lb://`.

---

## Manejo de errores

Los errores del servicio remoto se propagan de forma estructurada:

1. `products` lanza `EntityNotFoundException` → responde con `ErrorDto` y HTTP 404
2. `FeignErrorDecoder` deserializa el cuerpo del error y lo envuelve en `ServiceInvocationException`
3. El `ErrorHandler` global de `items` lo convierte en una respuesta HTTP coherente

---

## Tecnologías

| Tecnología | Uso |
|-----------|-----|
| Java 21 | Lenguaje |
| Spring Boot 3.5 | Framework base |
| Spring Cloud 2025.0 | OpenFeign, LoadBalancer, Eureka, Gateway, Config |
| Spring WebFlux | WebClient (alternativa a Feign) |
| Spring Data JPA | Persistencia |
| Spring Cache | Caché en memoria para datos estáticos (roles) |
| Spring Security Crypto | BCryptPasswordEncoder para hashing de contraseñas |
| Spring Authorization Server 1.5 | Servidor OAuth2/OIDC, emisión de JWT firmados con RSA |
| Springdoc OpenAPI 2.8.8 | Generación automática de Swagger UI (`/swagger-ui.html`) |
| Jakarta Bean Validation | Validación de entidades (`@NotBlank`, `@Email`...) |
| MySQL | Base de datos |
| MapStruct | Mapeo entre capas |
| Lombok | Reducción de boilerplate |

---

## Requisitos

- Java 21
- MySQL corriendo en `localhost:3306`
- Bases de datos `products`, `items` y `users` creadas

## Arrancar el proyecto

```bash
# Compilar módulos de soporte primero
./mvnw install -pl commons,starter

# 1. Arrancar el Config Server (debe ser el primero si los servicios no usan optional:)
./mvnw spring-boot:run -pl config-server

# 2. Arrancar Eureka
./mvnw spring-boot:run -pl eureka-server

# 3. Arrancar el Auth Server (debe estar levantado antes que el gateway)
./mvnw spring-boot:run -pl auth-server

# 4. Arrancar products
./mvnw spring-boot:run -pl products

# 5. Arrancar items
./mvnw spring-boot:run -pl items

# 6. Arrancar users
./mvnw spring-boot:run -pl users

# 7. Arrancar el gateway (requiere auth-server en :9100 para resolver issuer-uri al arrancar)
./mvnw spring-boot:run -pl gateway
```

| Servicio | URL |
|----------|-----|
| Config Server | http://localhost:8888/items-service/default |
| Eureka (panel) | http://localhost:8761 |
| Auth Server (JWK Set) | http://localhost:9100/oauth2/jwks |
| Products (directo) | http://localhost:8081/products |
| Items (directo) | http://localhost:8082/items |
| Users (directo) | http://localhost:8083/users |
| Users (Swagger) | http://localhost:8083/swagger-ui.html |
| Gateway | http://localhost:8090/api/products, http://localhost:8090/api/items |
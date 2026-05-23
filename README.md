# Spring Microservices - Arquitectura Hexagonal

Proyecto de aprendizaje que implementa una arquitectura de microservicios con **arquitectura hexagonal (Ports & Adapters)**. El objetivo es explorar distintas formas de resolver los mismos problemas: comunicación entre servicios, manejo de errores, balanceo de carga, etc.

---

## Módulos

```
spring/
├── starter/        # POM padre con dependencias compartidas
├── commons/        # Infraestructura y utilidades compartidas
├── products/       # Microservicio de productos (puerto 8081)
├── items/          # Microservicio de items (puerto 8082)
├── eureka-server/  # Servidor de descubrimiento de servicios (puerto 8761)
└── gateway/        # API Gateway (puerto 8090)
```

### `starter`
POM padre del que heredan todos los módulos. Centraliza versiones y dependencias comunes: Spring Boot 3.4.2, Spring Cloud 2024.0.0, MapStruct, Lombok, etc.

### `commons`
Librería compartida con autoconfiguration. Incluye:
- Jerarquía de excepciones de dominio (`EntityNotFoundException`, `ServiceInvocationException`, `UnexpectedException`)
- `ErrorHandler` global (`@RestControllerAdvice`) con respuesta uniforme `ErrorDto`
- Configuración de Feign (`FeignConfig`, `FeignErrorDecoder`)
- Configuración de WebClient (`WebClientConfig`)

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

### `gateway`
Punto de entrada único para todos los clientes externos. Implementado con **Spring Cloud Gateway** (basado en WebFlux). Expone los servicios bajo una ruta unificada en el puerto 8090, ocultando los puertos internos.

| Ruta pública (gateway:8090) | Se redirige a | Puerto interno |
|-----------------------------|---------------|----------------|
| `GET /api/products/**`      | `product-service` | 8081 |
| `GET /api/items/**`         | `items-service`   | 8082 |

El gateway se registra en Eureka como cliente y usa `lb://` para resolver los nombres lógicos de los servicios a través del Spring Cloud LoadBalancer, beneficiándose del mismo descubrimiento dinámico que el resto de microservicios.

El filtro `StripPrefix=1` elimina el segmento `/api` antes de reenviar la petición, de modo que `/api/products/1` llega al servicio `products` como `/products/1`.

```yaml
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
        - id: items-service
          uri: lb://items-service
          predicates:
            - Path=/api/items/**
          filters:
            - StripPrefix=1
```

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

**¿Cuándo usar cada uno?** Feign es más simple y legible para apps Spring MVC clásicas. WebClient es la opción recomendada para apps reactivas (Spring WebFlux), cuando se necesita streaming, o cuando se requiere control fino sobre timeouts y reintentos. Spring no recomienda Feign para nuevos proyectos.

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
| Spring Boot 3.4.2 | Framework base |
| Spring Cloud 2024.0.0 | OpenFeign, LoadBalancer, Eureka, Gateway |
| Spring WebFlux | WebClient (alternativa a Feign) |
| Spring Data JPA | Persistencia |
| MySQL | Base de datos |
| MapStruct | Mapeo entre capas |
| Lombok | Reducción de boilerplate |

---

## Requisitos

- Java 21
- MySQL corriendo en `localhost:3306`
- Bases de datos `products` e `items` creadas

## Arrancar el proyecto

```bash
# Compilar módulos de soporte primero
./mvnw install -pl commons,starter

# 1. Arrancar Eureka (debe ser el primero)
./mvnw spring-boot:run -pl eureka-server

# 2. Arrancar products
./mvnw spring-boot:run -pl products

# 3. Arrancar items
./mvnw spring-boot:run -pl items

# 4. Arrancar el gateway
./mvnw spring-boot:run -pl gateway
```

| Servicio | URL |
|----------|-----|
| Eureka (panel) | http://localhost:8761 |
| Products (directo) | http://localhost:8081/products |
| Items (directo) | http://localhost:8082/items |
| Gateway | http://localhost:8090/api/products, http://localhost:8090/api/items |
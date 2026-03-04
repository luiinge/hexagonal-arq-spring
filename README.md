# Spring Microservices - Arquitectura Hexagonal

Proyecto de aprendizaje que implementa una arquitectura de microservicios con **arquitectura hexagonal (Ports & Adapters)**. El objetivo es explorar distintas formas de resolver los mismos problemas: comunicación entre servicios, manejo de errores, balanceo de carga, etc.

---

## Módulos

```
spring/
├── starter/     # POM padre con dependencias compartidas
├── commons/     # Infraestructura y utilidades compartidas
├── products/    # Microservicio de productos (puerto 8081)
└── items/       # Microservicio de items (puerto 8082)
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

| Implementación | Clase | Estado |
|----------------|-------|--------|
| **Spring Cloud OpenFeign** | `ProductServiceFeignClient` | Activa |
| **Spring WebFlux WebClient** | `ProductServiceWebClient` | Pendiente |

Ambas se conectan a través del mismo puerto SPI (`ProductService`), por lo que cambiar de una a otra no requiere modificar el dominio.

### Balanceo de carga

Se usa **Spring Cloud LoadBalancer** con descubrimiento simple (sin Eureka). Las instancias se configuran en `application.yaml`:

```yaml
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

Para levantar varias instancias de `products` en puertos distintos:

```bash
# Instancia 1 (puerto por defecto)
./mvnw spring-boot:run -pl products

# Instancia 2
./mvnw spring-boot:run -pl products -Dspring-boot.run.arguments=--server.port=8091
```

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
| Spring Cloud 2024.0.0 | OpenFeign, LoadBalancer |
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
# Compilar todos los módulos
./mvnw install -pl commons,starter

# Arrancar products
./mvnw spring-boot:run -pl products

# Arrancar items
./mvnw spring-boot:run -pl items
```
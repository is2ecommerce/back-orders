Descripción general

Este proyecto es un backend en Java (Spring Boot) que gestiona órdenes pendientes (back orders). Sirve para registrar, actualizar y consultar órdenes y productos cuando no hay disponibilidad inmediata o se requiere manejo especial de estados de pedido.

El objetivo de este README es explicar de forma clara cómo ejecutar, probar y entender las principales piezas del backend. Está escrito con un estilo de estudiante que entiende el proyecto pero mantiene tono profesional.

Arquitectura y capas

El sistema está organizado en capas:

Controller — Controladores REST que exponen la API.

Service — Lógica de negocio (reglas y validaciones).

Repositories — Acceso a datos (Spring Data JPA).

Model / Entity — Entidades de dominio (Order, Product, OrderItem).

DTO — Objetos de transferencia para respuestas y peticiones.

Exceptions — Excepciones propias y manejador global (GlobalExceptionHandler).

Estructura principal (ejemplo):

back-orders-main/
├─ src/
│  ├─ main/java/com/example/backorders/
│  │  ├─ Controller/
│  │  ├─ Service/
│  │  ├─ Repositories/
│  │  ├─ model/
│  │  ├─ dto/
│  │  ├─ exceptions/
│  │  └─ BackOrdersApplication.java
├─ pom.xml
└─ target/

Tecnologías

Java 17
Spring Boot
Spring Data JPA
Maven
Base de datos: configurable (H2 para pruebas in-memory, MySQL/Postgres en producción)
Tests: JUnit + Spring Boot Test

Dependencias notables (ver pom.xml):
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-test

Instrucciones para ejecutar (local)
Clonar repo: git clone https://github.com/<usuario>/<repositorio>.git
cd back-orders-main

onfigurar variables (si usas MySQL/Postgres, editar application.properties o application.yml):

spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update

Compilar:
mvn clean install

Ejecutar:
mvn spring-boot:run

Flujo general y responsabilidades
Petición HTTP → Controller valida y transforma datos a DTOs → llama a Service → Service realiza lógica (validaciones, transacciones) → Repository accede a la BD → Service arma DTO de respuesta → Controller devuelve JSON con código HTTP apropiado.
Excepciones gestionadas por GlobalExceptionHandler para respuestas coherentes.

Endpoints (resumen y plantilla)

Nota: aquí tienes una plantilla de cómo documentar endpoints. Si me pegas los controladores, relleno exactamente estos bloques con rutas, body de ejemplo, respuestas y códigos HTTP.


Ejemplo: Crear una orden
POST /api/orders
Body (JSON):
{
  "customerId": 123,
  "items": [
    {"productId": 1, "quantity": 2},
    {"productId": 5, "quantity": 1}
  ],
  "notes": "Entrega en días hábiles"
}

Respuesta 201 Created:
{
  "orderId": 456,
  "status": "PENDING",
  "createdAt": "2025-11-06T10:00:00"
}


Ejemplo: Obtener resumen de orden
GET /api/orders/{id}
Respuesta 200 OK:
{
  "orderId": 456,
  "customerId": 123,
  "items": [...],
  "status": "PENDING"



Plantilla para más endpoints

GET /api/orders — listar órdenes (filtros: estado, fecha, cliente)
PUT /api/orders/{id} — actualizar orden
PATCH /api/orders/{id}/status — cambiar estado (validaciones de transición)
GET /api/products — listar productos
GET /api/products/{id} — ver producto


Validación y manejo de transacciones

La lógica para validar estados de orden (ej. PENDING → SHIPPED) debería estar en OrderService. Si la transición no es válida se lanza OrderStateException.

Para operaciones que afectan varias tablas (orden + items + stock) hay que usar transacciones (@Transactional en el servicio) para asegurar que la operación sea atómica.

GlobalExceptionHandler intercepta excepciones y devuelve respuestas con estructura:

{
  "timestamp": "2025-11-06T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Descripción del error"
}


Pruebas

Ejecutar: mvn test

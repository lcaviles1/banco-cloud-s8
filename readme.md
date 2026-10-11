# Banco XYZ con Spring Batch y microservicios

**Evaluación Final Transversal PBY2203 — Desarrollo Backend III**  
Autor: Lucas Avilés  
Docente: Alonso Esteban Castillo Pizarro

Código fuente: [lcaviles1/banco-cloud-s8](https://github.com/lcaviles1/banco-cloud-s8)

La entrega final está en la raíz de la rama main. La versión de semana 8 se conserva en el historial. El sistema procesa los CSV legacy oficiales, entrega respuestas para web, móvil y cajero, y ejecuta pagos mediante un libro de cuentas transaccional. Publica eventos mediante outbox y consume Kafka y JMS con deduplicación.

## Inicio

Requisitos: Docker Desktop, Compose 2.24 o posterior y Python 3.10+. Asigne 8 GB de memoria a Docker, o 12 GB para escalar. Los contenedores no requieren instalar Java ni Maven.

    python3 scripts/configurar.py
    docker compose config --quiet
    docker compose up -d --build --wait --wait-timeout 240
    python3 scripts/demo.py all

Los scripts no muestran secretos ni tokens. La primera compilación descarga dependencias. La demo modifica cuentas de prueba y agrega eventos; se puede repetir. Los jobs completados conservan su salida.

- [instrucciones.md](instrucciones.md): ejecución, contratos HTTP y pruebas.
- [despliegue.md](despliegue.md): contenedores, TLS y servidor Linux.
- [Informe técnico](informe_tecnico.pdf): arquitectura, decisiones y resultados.
- [Datos oficiales](https://github.com/KariVillagran/fin_legacy_data): origen del batch.
- evidencias/: CSV y respuestas JSON reales, sin credenciales.

## Componentes

| Módulo | Función |
|---|---|
| batch-service | Transacciones diarias, intereses mensuales y estados anuales |
| accounts-service | Apertura, mantenimiento, cierre y libro de movimientos |
| payments-service | Depósitos, retiros y transferencias con reconciliación |
| customers-service | Perfiles y consumo auditado de Kafka y JMS |
| bff-web | Agregación concurrente de perfil, cuentas y pagos |
| bff-mobile | Respuesta compacta con identificador y saldo |
| bff-atm | Saldo y retiros con scopes, PIN y límite |
| auth-server | OAuth2, tokens RSA de cinco minutos y permisos por canal |
| config-server | Configuración externa protegida |
| discovery-server | Eureka y descubrimiento dinámico |
| common | Seguridad, LoadBalancer, Resilience4j y publicación outbox |

## Reglas y datos

Versiones: Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3, Spring Batch 5, Resilience4j 2.3.0, PostgreSQL 16.10, Kafka 3.9.1 y Artemis 2.42.0. Maven Wrapper fija Maven 3.9.9.

Los CSV de data/ se copian sin cambios del commit 1ca7c0432319a1fc30f9469078f0cabf7081579f. Las semanas son escenarios independientes; no se concatenan. Cada job tiene particiones con readers aislados, chunks de 100, metadatos persistentes y salida por fila. Los identificadores repetidos se rechazan completos cuando no hay forma de determinar qué saldo es correcto.

El archivo de intereses se llama trimestral, pero el caso pide cálculo mensual: saldo × tasa anual / 12, HALF_UP y dos decimales. Tasas académicas configurables: ahorro 3 %, préstamo 12 % e hipoteca 8 %. No se proporcionaron código COBOL ni tasas del banco; la comparación numérica usa reglas explícitas y valores de referencia.

El dataset no especifica moneda; la demo online usa CLP como supuesto. Los estados anuales muestran movimientos netos y no inventan un saldo inicial. Un retiro de 500 o -500 representa un débito de 500. Los depósitos negativos se rechazan.

## Entorno

Las evidencias corresponden a ejecución local con Docker. El procedimiento de despliegue remoto es portable a un servidor Linux y no se ha ejecutado remotamente. Los clientes OAuth2 técnicos representan identidades de prueba fijas C001 y C002; para una banca real se requiere OIDC/PKCE por usuario y autenticación de tarjeta. El PIN es una segunda comprobación local. TLS tiene un perfil ejecutable. Las comunicaciones internas y brokers usan una red Docker privada.

Las tres APIs de negocio soportan réplicas sobre persistencia compartida. El perfil local tiene PostgreSQL y brokers únicos, por lo que no elimina el fallo del host.

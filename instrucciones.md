# Ejecución y pruebas de Banco XYZ

## 1. Preparar el entorno

Abra una terminal en la raíz del proyecto. Use Docker Desktop con al menos 8 GB de RAM, Compose 2.24+ y Python 3.10+. En Windows sustituya python3 por python y use contenedores Linux. Verifique que los puertos de la tabla final estén libres.

    python3 scripts/configurar.py
    docker compose config --quiet
    docker compose up -d --build --wait --wait-timeout 240
    docker compose ps

El script crea .env con secretos aleatorios y permisos privados; conserva el archivo si ya existe. No borre .env mientras mantenga los volúmenes, porque las contraseñas deben seguir coincidiendo. La firma RSA se guarda en authdata, las cuatro bases en pgdata, los resultados batch en batchdata y los eventos en los volúmenes de los brokers. Config Server entrega los archivos de config-repo, incorporados a su imagen. Después de cambiar configuración, reconstruya Config Server y recree sus clientes.

## 2. Compilar y probar

Con Java 21, Maven Wrapper descarga la versión fijada:

    ./mvnw -B verify

En Windows use mvnw.cmd. Las pruebas cubren redondeo mensual, fechas imposibles, formatos alternativos, importes inválidos, dirección de egresos, tipos con acentos y propiedad de recursos.

La demostración HTTP requiere contenedores saludables:

    python3 scripts/demo.py all

Comprueba los nueve escenarios batch, los tres canales, permisos, cuentas y clientes, transferencias, depósitos autorizados, retiros, idempotencia y consumo Kafka/JMS. No imprime JWT ni PIN. Eureka necesita algunos segundos para propagar instancias; si una llamada inicial indica servicio no disponible, espere 15 segundos y repita.

## 3. Ejecutar los tres jobs

    python3 scripts/demo.py batch

Los tres procesos se ejecutan contra las tres semanas. Cada salida se descarga a runtime/semana_N/daily, interest o annual:

- resultado.csv: agrupación por día y tipo, intereses por cuenta o estado anual.
- detalle.csv: filas válidas y valores transformados.
- rechazados.csv: número de fila, motivo y registro original.

La cuarentena es una salida controlada. Semana 3 tiene 1.000 registros de intereses con identificadores repetidos: se rechazan todos por ambigüedad del saldo y se completa sin emitir intereses para ese escenario.

Para ejecutar individualmente:

    python3 scripts/api.py operator 8084 "/api/batch/daily?week=semana_1&period=2024" --method POST
    python3 scripts/api.py operator 8084 "/api/batch/interest?week=semana_1&period=2024-02&savingsRate=0.03&loanRate=0.12&mortgageRate=0.08" --method POST
    python3 scripts/api.py operator 8084 "/api/batch/annual?week=semana_1&period=2024" --method POST
    python3 scripts/api.py operator 8084 /api/batch/runs
    python3 scripts/api.py operator 8084 /api/batch/runs/ID
    python3 scripts/api.py operator 8084 /api/batch/runs/ID/resultado.csv

Reemplace ID por executionId. HTTP 202 indica aceptación; espere status COMPLETED. La misma entrada, período, tasas y workers identifica una instancia única; repetir una completada devuelve 409 y conserva salidas. FAILED se relanza hasta tres veces con los mismos parámetros y checkpoints. Una modificación del CSV obliga a una nueva instancia; no se permite reiniciar una entrada distinta.

El reporte diario agrupa cada fecha contenida en el CSV del escenario; period=2024 limita el año. Importes de 3.000 o más se marcan HIGH_VALUE. Fechas imposibles, cero, negativos y tipos inválidos se rechazan. El cálculo mensual usa tasas académicas configurables. Los estados anuales expresan movimientos netos; no un saldo final, porque falta saldo inicial.

## 4. Probar Web, Móvil y Cajero

    python3 scripts/api.py web 8091 /api/web/dashboard
    python3 scripts/api.py mobile 8092 /api/mobile/home
    python3 scripts/api.py atm 8093 /api/atm/balance/101

Web agrega perfil, cuentas y veinte pagos recientes en paralelo. Móvil devuelve sólo identificador y saldo. Cajero exige JWT de su canal y PIN; el cliente de prueba lee el PIN de .env sin mostrarlo. Las identidades tienen scopes diferentes y el claim channel debe coincidir con el BFF.

## 5. Cuentas y clientes

    python3 scripts/api.py web 8081 /api/accounts
    python3 scripts/api.py web 8081 /api/accounts/101
    python3 scripts/api.py web 8081 /api/accounts --method POST --data '{"type":"SAVINGS"}'
    python3 scripts/api.py web 8081 /api/accounts/ID --method PATCH --data '{"type":"LOAN"}'
    python3 scripts/api.py web 8081 /api/accounts/ID --method DELETE
    python3 scripts/api.py web 8083 /api/customers/me
    python3 scripts/api.py web 8083 /api/customers/me --method PUT --data '{"name":"Lucas Avilés","email":"lucas@example.test"}'

Use el ID devuelto por apertura. Sólo se cierra una cuenta con saldo cero. El acceso a recursos de otro cliente devuelve 403. El cliente foreign representa C002 y no puede leer la cuenta 101. DELETE /api/customers/me realiza baja lógica; la demo mantiene activo C001.

## 6. Pagos con movimiento efectivo de dinero

    python3 scripts/api.py web 8091 /api/web/payments --method POST --key transferencia-demo-001 --data '{"type":"TRANSFER","source":101,"destination":102,"amount":10}'
    python3 scripts/api.py teller 8082 /api/payments --method POST --key deposito-demo-001 --data '{"type":"DEPOSIT","source":101,"amount":10000}'
    python3 scripts/api.py atm 8093 /api/atm/payments --method POST --key retiro-demo-001 --data '{"type":"WITHDRAWAL","source":101,"amount":100}'
    python3 scripts/api.py web 8082 /api/payments
    python3 scripts/api.py web 8082 /api/payments/ID

202 deja PENDING. Espere COMPLETED antes de confirmar un movimiento. REJECTED representa rechazo definitivo, como fondos insuficientes. Un fallo temporal mantiene PENDING para reconciliar. Repetir la misma clave y contenido devuelve el mismo pago; cambiar contenido devuelve 409. Web no tiene permiso de depósito. Cajero sólo permite retiro y limita cada operación a 500.

Cuentas verifica propiedad y estado, bloquea saldos en orden, aplica débito/crédito en una transacción y registra paymentId único. Si la respuesta se pierde después del commit, Pagos vuelve a llamar con el mismo identificador y no mueve dinero dos veces.

## 7. Eventos Kafka y JMS

    python3 scripts/api.py operator 8083 /api/customers/events
    python3 scripts/api.py operator 8082 /api/payments/operations/status
    docker compose logs --tail=30 payments-service customers-service

COMPLETED genera transactions.completed.v1. Un importe de 10.000 o más genera también security.alerts.v1 y la cola JMS bank.security.alerts. Outbox espera acuse del broker antes de marcar enviado. Inbox tiene eventId único. Kafka usa tres particiones y el grupo customers-audit-v1; las réplicas reparten particiones. Los eventos inválidos se reintentan tres veces después del intento inicial y se publican en el tópico .DLT. JMS usa sesión transaccional y redelivery del broker.

## 8. Recuperación ante fallos

Estas pruebas pausan componentes de la demo y los restauran con finally. Ejecútelas con una réplica y sin peticiones externas simultáneas.

    python3 scripts/probar_fallos.py payments
    python3 scripts/probar_fallos.py batch

Pagos pausa Cuentas, verifica PENDING y dashboard degradado sin saldo inventado, y comprueba un descuento único después de recuperar. Batch impide escritura en una salida de prueba, observa FAILED y relanzamiento automático, restaura permisos y espera COMPLETED. Para repetir la prueba batch use una instalación nueva, porque la instancia de prueba queda completada.

## 9. Escalado horizontal

Asigne al menos 12 GB a Docker antes de añadir réplicas:

    docker compose -f compose.yml -f compose.scale.yml up -d --scale accounts-service=2 --scale payments-service=2 --scale customers-service=2 --wait --wait-timeout 240
    docker compose -f compose.yml -f compose.scale.yml ps
    python3 scripts/probar_escalado.py
    python3 scripts/api.py web 8091 /api/web/dashboard

La sobreescritura elimina puertos fijos de los tres servicios de negocio. BFF conserva sus puertos. Eureka descubre instancias independientes y LoadBalancer distribuye llamadas. Las bases mantienen saldos compartidos. Pagos y Outbox reclaman filas con FOR UPDATE SKIP LOCKED; Clientes usa particiones y consumidores competidores.

Batch escala con BATCH_WORKERS=1..16 en .env y recreación de batch-service; cada worker tiene reader propio. El número de workers forma parte de la identidad del job para comparar ejecuciones sin mezclar checkpoints. No use múltiples réplicas batch sobre salidas locales no compartidas.

Para volver al perfil base y recuperar los puertos de diagnóstico:

    docker compose -f compose.yml -f compose.scale.yml up -d --scale accounts-service=1 --scale payments-service=1 --scale customers-service=1
    docker compose up -d --wait --wait-timeout 240

## 10. Puertos y operación

| Componente | Puerto local |
|---|---:|
| OAuth2 | 9000 |
| Config Server | 8888 |
| Eureka | 8761 |
| Cuentas | 8081 |
| Pagos | 8082 |
| Clientes | 8083 |
| Batch | 8084 |
| BFF Web | 8091 |
| BFF Móvil | 8092 |
| BFF Cajero | 8093 |
| TLS opcional | 8443 |

Kafka, PostgreSQL y Artemis no publican puertos. Todas las publicaciones base se limitan a localhost. Revise docker compose logs --tail=80 NOMBRE si un componente falla. Las aplicaciones usan UID 10001; el init de Kafka configura el volumen para UID 1000. Mantenga secretos fuera de Git y de las evidencias.

    docker compose stop

Stop conserva datos. Una reinstalación voluntaria con docker compose down -v elimina los volúmenes; no hace falta para el uso normal. Guarde las salidas antes de eliminar datos.

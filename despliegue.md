# Despliegue con Docker y servidor Linux

El sistema se ejecutó localmente con Docker. Este procedimiento prepara una instalación remota portable a una máquina Linux y no depende de Amazon. Puede utilizar una máquina virtual habilitada o un servidor institucional.

## 1. Preparar el servidor

Use Ubuntu 24.04, 4 vCPU, al menos 8 GB de RAM (16 GB recomendados al compilar y escalar) y 40 GB de disco. Instale Git, Python y Docker Engine con Compose según las [instrucciones oficiales para Ubuntu](https://docs.docker.com/engine/install/ubuntu/). Añada su usuario al grupo docker y vuelva a iniciar sesión. Permita SSH sólo desde su IP y 80/443 para entrada pública.

    git clone https://github.com/lcaviles1/banco-cloud-s8.git
    cd banco-cloud-s8
    python3 scripts/configurar.py
    docker compose config --quiet

No necesita un registro privado: las imágenes se construyen desde el código. Conserve .env y volúmenes en almacenamiento persistente. PostgreSQL y los brokers sólo son accesibles dentro de la red Docker.

## 2. Dominio y certificados

Cree un registro DNS A hacia la IP pública. En .env añada BANK_DOMAIN=banco.su-dominio.cl y cambie OAUTH_ISSUER=https://banco.su-dominio.cl. Use su dominio real, del que tenga control.

    docker compose -f compose.yml -f compose.cloud.yml config --quiet
    docker compose -f compose.yml -f compose.cloud.yml up -d --build --wait --wait-timeout 240
    curl --fail https://banco.su-dominio.cl/health

Caddy solicita y renueva certificados cuando DNS y puertos están disponibles. [Referencia de HTTPS automático](https://caddyserver.com/docs/automatic-https). Los endpoints públicos se limitan a OAuth2 y los BFF. Cambiar issuer requiere recrear Auth Server y todos los resource servers. Los clientes externos solicitan tokens por HTTPS y no incluyen credenciales en URLs o archivos públicos.

## 3. HTTPS local sin dominio

    docker compose -f compose.yml -f compose.tls.yml up -d edge
    docker compose -f compose.yml -f compose.tls.yml cp edge:/data/caddy/pki/authorities/local/root.crt runtime/caddy-root.crt
    curl --fail --cacert runtime/caddy-root.crt https://localhost:8443/health

Confíe explícitamente en la CA con --cacert. No desactive la verificación TLS. Las rutas /api/web/*, /api/mobile/* y /api/atm/* se enrutan al BFF correspondiente. HTTP de localhost se conserva para diagnóstico. El perfil remoto utiliza certificados del dominio.

## 4. Escalar los tres servicios

    docker compose -f compose.yml -f compose.cloud.yml up -d --scale accounts-service=2 --scale payments-service=2 --scale customers-service=2 --wait --wait-timeout 240
    docker compose -f compose.yml -f compose.cloud.yml ps

Cada réplica tiene identidad propia en Eureka y no publica un puerto fijo. Las tres APIs crecen independientemente. Los workers de Pagos y Outbox usan locks en PostgreSQL; Clientes comparte grupo Kafka y cola JMS. La demostración local equivalente usa compose.scale.yml.

Compose replica procesos dentro de un host; no cubre pérdida completa de la máquina. Para varios hosts se necesita orquestador, descubrimiento compatible, persistencia administrada, brokers replicados y salida batch compartida. [Orientaciones oficiales sobre Compose en producción](https://docs.docker.com/compose/how-tos/production/).

## 5. Actualizar configuración y código

Config Server y Eureka requieren Basic Auth. La configuración común y la específica del módulo están en config-repo; los secretos se resuelven desde variables privadas.

    git pull --ff-only
    docker compose -f compose.yml -f compose.cloud.yml build
    docker compose -f compose.yml -f compose.cloud.yml up -d --wait --wait-timeout 240

Mantenga .env al recrear. Si cambió configuración, reconstruya también Config Server. El Dockerfile multietapa comparte la compilación de módulos y genera imágenes independientes sin privilegios.

## 6. Persistencia y monitorización

Hay cuatro bases con usuarios distintos. El prototipo usa SQL de inicialización idempotente; cambios incompatibles deben migrarse con Flyway o Liquibase. Respalde las bases con herramientas PostgreSQL y conserve claves RSA y secretos en un gestor privado. Compruebe restauraciones antes de reemplazar datos.

Health/readiness controlan arranque. Prometheus se expone con scope ops.read. Monitoree pagos PENDING prolongados, outbox pendiente, DLT y jobs FAILED con reintentos agotados. Los logs identifican servicio y evento sin registrar tokens, PIN o claves.

El perfil privado local usa HTTP interno y Kafka sin TLS. Para varios hosts añada TLS/mTLS interno y SASL/TLS en brokers. La demostración usa clientes OAuth2 técnicos fijos; antes de operación bancaria real se requieren OIDC/PKCE por usuario, identidad de tarjeta, gestión antifraude y redundancia. El despliegue remoto no se presenta como ejecutado.

## 7. Detener la instalación

    docker compose -f compose.yml -f compose.cloud.yml stop

Stop conserva los datos. Elimine volúmenes sólo después de respaldar lo necesario. Retire la máquina y el registro DNS cuando ya no se utilicen.

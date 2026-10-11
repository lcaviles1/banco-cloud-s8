"""Genera secretos locales; mantiene credenciales para no romper los volumenes."""
from pathlib import Path
import secrets,os,hashlib
root=Path(__file__).resolve().parents[1]
p=root/'.env'
if p.exists():
 print('Se conserva .env existente')
else:
 pin=str(secrets.randbelow(900000)+100000)
 keys=['CONFIG_PASSWORD','EUREKA_PASSWORD','BROKER_PASSWORD','DB_ACCOUNTS_PASSWORD','DB_PAYMENTS_PASSWORD','DB_CUSTOMERS_PASSWORD','DB_BATCH_PASSWORD','POSTGRES_PASSWORD','WEB_CLI_SECRET','MOBILE_CLI_SECRET','ATM_CLI_SECRET','TELLER_CLI_SECRET','OPERATOR_CLI_SECRET','READER_CLI_SECRET','FOREIGN_CLI_SECRET','PAYMENTS_WORKER_SECRET']
 values={k:secrets.token_hex(24) for k in keys}
 values.update(CONFIG_USER='config',EUREKA_USER='eureka',BROKER_USER='bank',OAUTH_ISSUER='http://localhost:9000',ATM_PIN=pin,ATM_PIN_HASH=hashlib.sha256(pin.encode()).hexdigest(),BATCH_WORKERS='4')
 with p.open('x') as f:f.write(''.join(k+'='+v+'\n' for k,v in values.items()))
 os.chmod(p,0o600)
 print('.env creado con secretos aleatorios; no se muestran')
(root/'runtime').mkdir(exist_ok=True)

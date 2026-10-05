"""Demostración real contra Docker Compose; registra respuestas sin credenciales."""
import base64
import datetime
import json
import subprocess
import time
import uuid
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode

ROOT = Path(__file__).resolve().parents[1]
ENV = dict(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines() if line and not line.startswith('#'))
LOG = []

def emit(label, value):
    line = f'{datetime.datetime.now(datetime.timezone.utc).isoformat()} {label}: {json.dumps(value, ensure_ascii=False)}'
    print(line, flush=True)
    LOG.append(line)

def http(port, path, token=None, method='GET', data=None, headers=None, form=False):
    h = dict(headers or {})
    if token:
        h['Authorization'] = 'Bearer ' + token
    body = None
    if data is not None:
        body = urlencode(data).encode() if form else json.dumps(data).encode()
        h['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
    try:
        with urlopen(Request(f'http://localhost:{port}{path}', data=body, headers=h, method=method), timeout=15) as r:
            raw = r.read().decode()
            return r.status, json.loads(raw) if raw else None
    except HTTPError as e:
        raw = e.read().decode()
        try:
            raw = json.loads(raw)
        except ValueError:
            pass
        return e.code, raw

def token(client='bank-cli', secret_key='BANK_CLIENT_SECRET', scope=None):
    auth = base64.b64encode((client + ':' + ENV[secret_key]).encode()).decode()
    form = {'grant_type': 'client_credentials'}
    form['scope'] = scope or ('cuentas.read operaciones.read operaciones.write auditoria.read movimientos.read bff.read atm.write' if client == 'bank-cli' else 'cuentas.read operaciones.read')
    status, result = http(9000, '/oauth2/token', method='POST', data=form, form=True, headers={'Authorization': 'Basic ' + auth})
    assert status == 200, (status, result)
    emit('OAuth2 client_credentials', {'cliente': client, 'http': status, 'tipo': result['token_type'], 'expira_en': result['expires_in'], 'scope': result.get('scope')})
    return result['access_token']

def check(label, port, path, expected=200, **kwargs):
    status, result = http(port, path, **kwargs)
    emit(label, {'http': status, 'respuesta': result})
    assert status == expected, (label, status, expected)
    return result

def compose(*args):
    r = subprocess.run(['docker', 'compose', *args], cwd=ROOT, capture_output=True, text=True)
    emit('docker compose ' + ' '.join(args), (r.stdout + r.stderr).strip())
    r.check_returncode()
    return r.stdout

def until(fn, timeout=120):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            value = fn()
            if value:
                return value
        except (URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(2)
    raise AssertionError('Se agotó la espera de recuperación')

def health(port):
    status, body = http(port, '/actuator/health')
    return status == 200 and body.get('status') == 'UP'

def run():
    emit('Entorno', {'fecha': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'ejecucion': 'Contenedores Docker reales', 'fuente': 'bank_legacy_data / semana_3'})
    compose('ps')
    for name, port in [('config',8888),('discovery',8761),('auth',9000),('cuentas',8081),('operaciones',8082),('auditoria',8083),('movimientos',8084),('web',8091),('mobile',8092),('atm',8093)]:
        check('Salud ' + name, port, '/actuator/health')
    check('Configuración protegida',8888,'/account-service/default',401)
    check('Descubrimiento protegido',8761,'/eureka/apps',401,headers={'Accept':'application/json'})
    for port, path in [(8081,'/api/cuentas'),(8082,'/api/operaciones'),(8083,'/api/eventos')]:
        check('Sin JWT',port,path,401)
    check('JWT inválido',8081,'/api/cuentas',401,token='invalid.jwt.signature')
    auth = base64.b64encode(b'bank-cli:incorrecto').decode()
    check('Cliente inválido',9000,'/oauth2/token',401,method='POST',data={'grant_type':'client_credentials'},form=True,headers={'Authorization':'Basic '+auth})
    full = token()
    reader = token('bank-reader','READER_CLIENT_SECRET')
    check('Scope de auditoría ausente',8083,'/api/eventos',403,token=reader)
    check('Escritura sin scope',8082,'/api/operaciones',403,token=reader,method='POST',data={'cuentaId':106,'tipo':'DEPOSITO','monto':25},headers={'Idempotency-Key':'forbidden-001'})
    rows = check('Cuentas importadas',8081,'/api/cuentas',token=full)
    account = rows[0]['ID']
    status, imported = http(8081,'/api/cuentas/importacion',token=full)
    assert status == 200
    emit('Importación legacy', {'http': status, 'resumen': imported['resumen'], 'primeros_rechazos': imported['rechazos'][:5]})
    assert imported['resumen'][0]['ACEPTADOS'] == len(rows)
    check('Cuenta inexistente',8081,'/api/cuentas/999999',404,token=full)
    infra = base64.b64encode(('infra:'+ENV['INFRA_PASSWORD']).encode()).decode()
    registered = until(lambda: http(8761,'/eureka/apps',headers={'Accept':'application/json','Authorization':'Basic '+infra})[1])
    until(lambda: len(http(8761,'/eureka/apps',headers={'Accept':'application/json','Authorization':'Basic '+infra})[1]['applications'].get('application',[])) >= 4)
    registered = http(8761,'/eureka/apps',headers={'Accept':'application/json','Authorization':'Basic '+infra})[1]
    emit('Eureka aplicaciones', [a['name'] for a in registered['applications']['application']])
    assert {'AUTH-SERVER','ACCOUNT-SERVICE','OPERACIONES-SERVICE','AUDITORIA-SERVICE'} <= {a['name'] for a in registered['applications']['application']}
    until(lambda: http(8082,f'/api/operaciones/contexto/{account}',token=full)[1].get('estado') == 'DISPONIBLE')
    check('Consulta entre servicios con Eureka',8082,f'/api/operaciones/contexto/{account}',token=full)
    check('Monto inválido',8082,'/api/operaciones',400,token=full,method='POST',data={'cuentaId':account,'tipo':'DEPOSITO','monto':-5},headers={'Idempotency-Key':'negative-001'})
    key = 'demo-' + uuid.uuid4().hex
    data = {'cuentaId':account,'tipo':'DEPOSITO','monto':125.5}
    op = check('Registrar operación',8082,'/api/operaciones',201,token=full,method='POST',data=data,headers={'Idempotency-Key':key})
    again = check('Repetir clave idempotente',8082,'/api/operaciones',201,token=full,method='POST',data=data,headers={'Idempotency-Key':key})
    assert op['ID'] == again['ID']
    check('Clave con otro monto',8082,'/api/operaciones',409,token=full,method='POST',data=dict(data,monto=200),headers={'Idempotency-Key':key})
    until(lambda: any(e['ID']==op['ID'] for e in http(8083,'/api/eventos',token=full)[1]))
    check('Evento recibido por JMS',8083,'/api/eventos',token=full)
    for port,path in [(8091,'/api/web/accounts/101/dashboard'),(8092,'/api/mobile/accounts/101/home'),(8093,'/api/atm/accounts/101/balance'),(8084,'/internal/data-quality')]:
        check('Canal original sin JWT',port,path,401)
        check('Canal original autenticado',port,path,token=full,headers={'X-ATM-PIN':ENV['ATM_PIN']})
    check('ATM PIN inválido',8093,'/api/atm/accounts/101/balance',401,token=full,headers={'X-ATM-PIN':'incorrecto'})
    before_balance=http(8093,'/api/atm/accounts/101/balance',token=full,headers={'X-ATM-PIN':ENV['ATM_PIN']})[1]['saldoDisponible']
    receipt=check('Retiro ATM original protegido por OAuth2',8093,'/api/atm/accounts/101/withdrawals',token=full,method='POST',headers={'X-ATM-PIN':ENV['ATM_PIN']},data={'monto':1000})
    assert receipt['saldoDisponible']==before_balance-1000
    before = len(http(8082,'/api/operaciones',token=full)[1])
    try:
        compose('stop','account-service')
        for i in range(6):
            degraded = check('Fallback sin cuentas ' + str(i+1),8082,f'/api/operaciones/contexto/{account}',token=full)
            assert degraded['estado'] == 'DEGRADADO'
            state = http(8082,'/api/resiliencia',token=full)[1]
            if state['circuitBreaker'] == 'OPEN':
                break
        state = check('Circuit breaker abierto y reintentos',8082,'/api/resiliencia',token=full)
        assert state['circuitBreaker'] == 'OPEN' and state['retriedFailed'] > 0
        check('Escritura rechazada durante caída',8082,'/api/operaciones',503,token=full,method='POST',data=data,headers={'Idempotency-Key':'failure-'+uuid.uuid4().hex})
        assert len(http(8082,'/api/operaciones',token=full)[1]) == before
    finally:
        compose('start','account-service')
    until(lambda: health(8081))
    until(lambda: http(8082,f'/api/operaciones/contexto/{account}',token=full)[1].get('estado') == 'DISPONIBLE')
    until(lambda: http(8082,f'/api/operaciones/contexto/{account}',token=full)[1].get('estado') == 'DISPONIBLE')
    state = check('Circuit breaker recuperado',8082,'/api/resiliencia',token=full)
    assert state['circuitBreaker'] == 'CLOSED'
    full = token()
    try:
        compose('stop','broker')
        pending = check('Operación con broker detenido',8082,'/api/operaciones',201,token=full,method='POST',data=data,headers={'Idempotency-Key':'offline-'+uuid.uuid4().hex})
        time.sleep(5)
        box = check('Evento persistido en outbox',8082,'/api/operaciones/outbox',token=full)
        assert box['pendientes'] >= 1
    finally:
        compose('start','broker')
    until(lambda: any(e['ID']==pending['ID'] for e in http(8083,'/api/eventos',token=full)[1]),180)
    until(lambda: http(8082,'/api/operaciones/outbox',token=full)[1]['pendientes'] == 0)
    check('Outbox drenado después de recuperación',8082,'/api/operaciones/outbox',token=full)
    events = check('Eventos finales sin duplicados',8083,'/api/eventos',token=full)
    assert len([e for e in events if e['ID']==pending['ID']]) == 1
    # Reinicio de persistencia: la auditoría debe conservar el evento ya recibido.
    compose('restart','auditoria-service')
    until(lambda: health(8083))
    assert any(e['ID']==pending['ID'] for e in http(8083,'/api/eventos',token=full)[1])
    emit('Persistencia tras reinicio', {'eventId':pending['ID'],'conservado':True})
    until(lambda: health(8082))
    compose('ps')
    emit('Fin', 'Demostración completada')

if __name__ == '__main__':
    try:
        run()
    except Exception as exc:
        emit('ERROR',str(exc))
        raise
    finally:
        folder = ROOT / 'evidencias'
        folder.mkdir(exist_ok=True)
        (folder / 'ejecucion-consola.txt').write_text('\n'.join(LOG)+'\n',encoding='utf-8')

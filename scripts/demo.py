"""Demostracion HTTP real. No imprime JWT, secretos ni PIN."""
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.error import HTTPError
from urllib.parse import urlencode
import json,base64,time,uuid,sys,csv,io
root=Path(__file__).resolve().parents[1]
env=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if line and not line.startswith('#'))
def http(port,path,method='GET',token=None,data=None,headers=None):
 h=dict(headers or {})
 if token:h['Authorization']='Bearer '+token
 if data is not None:h['Content-Type']='application/json'
 req=Request(f'http://localhost:{port}'+path,data=json.dumps(data).encode() if data is not None else (b'' if method=='POST' else None),headers=h,method=method)
 try:
  with urlopen(req,timeout=20) as r:return r.status,r.read()
 except HTTPError as e:return e.code,e.read()
def token(client):
 key=client.upper().replace('-','_')+'_SECRET'
 h={'Authorization':'Basic '+base64.b64encode((client+':'+env[key]).encode()).decode(),'Content-Type':'application/x-www-form-urlencoded'}
 req=Request('http://localhost:9000/oauth2/token',data=urlencode({'grant_type':'client_credentials','scope':{'web-cli':'accounts.read accounts.write customers.read customers.write payments.read payments.write','mobile-cli':'accounts.read payments.read payments.write','atm-cli':'accounts.read payments.read payments.write','teller-cli':'accounts.read payments.read payments.write deposits.write','operator-cli':'ops.read batch.read batch.run','reader-cli':'accounts.read','foreign-cli':'accounts.read payments.read payments.write','payments-worker':'accounts.post'}[client]}).encode(),headers=h)
 with urlopen(req,timeout=10) as r:return json.load(r)['access_token']
def check(code,expected,label):
 assert code==expected,f'{label}: esperado {expected}, recibido {code}'
 print(f'{label}: HTTP {code}')
def jsoncall(port,path,token_,method='GET',data=None,headers=None,expected=200):
 code,raw=http(port,path,method,token_,data,headers);check(code,expected,path);return json.loads(raw or b'{}')
def complete(id,t):
 for i in range(40):
  p=jsoncall(8082,'/api/payments/'+id,t)
  if p['status']!='PENDING':return p
  time.sleep(1)
 raise AssertionError('Pago permanece pendiente')
def run_batch():
 t=token('operator-cli')
 for week in ['semana_1','semana_2','semana_3']:
  for kind in ['daily','interest','annual']:
   period='2024-01' if kind=='interest' else '2024'
   code,raw=http(8084,f'/api/batch/{kind}?week={week}&period={period}','POST',t)
   if code==409:
    print(f'{kind} {week}: instancia existente; se conserva resultado')
    runs=jsoncall(8084,'/api/batch/runs',t)['runs']
    chosen=next(r for r in runs if r['run_key'].startswith(kind+'-'+week+'-'+period+'-'))
    job={'executionId':chosen['execution_id']};raw=json.dumps(job).encode();code=202
   check(code,202,'Inicio '+kind+' '+week);job=json.loads(raw);id=job['executionId']
   for i in range(90):
    code,raw=http(8084,f'/api/batch/runs/{id}',token=t);check(code,200,'Estado job');state=json.loads(raw)
    if state['status'] in ['COMPLETED','FAILED','STOPPED','ABANDONED']:break
    time.sleep(1)
   assert state['status']=='COMPLETED',state
   print(json.dumps(state,ensure_ascii=False))
   folder=root/'runtime'/week/kind;folder.mkdir(parents=True,exist_ok=True)
   for f in ['resultado.csv','rechazados.csv','detalle.csv']:
    code,raw=http(8084,f'/api/batch/runs/{id}/{f}',token=t);check(code,200,f);(folder/f).write_bytes(raw)
   if week=='semana_1':
    expected={'daily':(8,2),'interest':(8,0),'annual':(8,1)}[kind]
    counts={x['status']:x['count'] for x in state['records']}
    assert (counts.get('VALID',0),counts.get('REJECTED',0))==expected,counts
    if kind=='interest':
     rows=list(csv.DictReader((folder/'resultado.csv').open()))
     assert rows[0]['interes']=='12.50' and rows[1]['interes']=='80.00'
    if kind=='annual':
     rows=list(csv.DictReader((folder/'resultado.csv').open()))
     assert rows[0]['movimiento_neto']=='500.00'
 print('Tres jobs y tres escenarios completados')
def run_api():
 web=token('web-cli');mobile=token('mobile-cli');atm=token('atm-cli');operator=token('operator-cli')
 check(http(8081,'/api/accounts/101')[0],401,'Sin token')
 check(http(8081,'/api/accounts/102',token=web)[0],403,'Propiedad de cuenta')
 check(http(8092,'/api/mobile/home',token=web)[0],401,'Token de canal equivocado')
 check(http(8082,'/api/payments','POST',token('reader-cli'),{'type':'WITHDRAWAL','source':101,'amount':1}, {'Idempotency-Key':'denied-'+uuid.uuid4().hex})[0],403,'Scope insuficiente')
 check(http(8093,'/api/atm/balance/101',token=atm,headers={'X-ATM-PIN':'invalid'})[0],403,'PIN incorrecto')
 a=jsoncall(8091,'/api/web/dashboard',web);m=jsoncall(8092,'/api/mobile/home',mobile)
 assert len(json.dumps(m))<len(json.dumps(a))
 print('Web bytes:',len(json.dumps(a).encode()),'Movil bytes:',len(json.dumps(m).encode()))
 jsoncall(8093,'/api/atm/balance/101',atm,headers={'X-ATM-PIN':env['ATM_PIN']})
 original=jsoncall(8081,'/api/accounts/101',web)['balance']
 # Account opening, maintenance and zero-balance closure.
 new=jsoncall(8081,'/api/accounts',web,'POST',{'type':'SAVINGS'})
 jsoncall(8081,'/api/accounts/'+str(new['id']),web,'PATCH',{'type':'LOAN'})
 jsoncall(8081,'/api/accounts/'+str(new['id']),web,'DELETE')
 profile=jsoncall(8083,'/api/customers/me',web)
 jsoncall(8083,'/api/customers/me',web,'PUT',{'name':profile['name'],'email':profile['email']})
 # Transfer is atomic across two account aggregates, even across customers.
 key='demo-'+uuid.uuid4().hex
 req={'type':'TRANSFER','source':101,'destination':102,'amount':10}
 p=jsoncall(8091,'/api/web/payments',web,'POST',req,{'Idempotency-Key':key},202)
 p=complete(p['id'],web);assert p['status']=='COMPLETED'
 same=jsoncall(8091,'/api/web/payments',web,'POST',req,{'Idempotency-Key':key},202)
 assert same['id']==p['id']
 assert jsoncall(8081,'/api/accounts/101',web)['balance']==original-10
 check(http(8091,'/api/web/payments','POST',web,dict(req,amount=11),{'Idempotency-Key':key})[0],409,'Clave con contenido diferente')
 # Deposit only permitted for teller; high value creates Kafka and JMS alerts.
 teller=token('teller-cli')
 dep=jsoncall(8082,'/api/payments',teller,'POST',{'type':'DEPOSIT','source':101,'amount':10000},{'Idempotency-Key':'cash-'+uuid.uuid4().hex},202)
 assert complete(dep['id'],teller)['status']=='COMPLETED'
 # Verify ATM effective withdrawal and balance.
 r=jsoncall(8093,'/api/atm/payments',atm,'POST',{'type':'WITHDRAWAL','source':101,'amount':100},{'Idempotency-Key':'atm-'+uuid.uuid4().hex,'X-ATM-PIN':env['ATM_PIN']},202)
 assert complete(r['id'],atm)['status']=='COMPLETED'
 assert jsoncall(8081,'/api/accounts/101',web)['balance']==original+9890
 # Kafka and JMS event consumption and transactional outbox.
 for i in range(30):
  events=jsoncall(8083,'/api/customers/events',operator)
  if {'KAFKA','JMS'}<=set(e['channel'] for e in events['events']):break
  time.sleep(1)
 assert {'KAFKA','JMS'}<=set(e['channel'] for e in events['events']),events
 print(json.dumps(events,ensure_ascii=False))
 print(json.dumps(jsoncall(8082,'/api/payments/operations/status',operator)))
 print('APIs, permisos, canales, pagos, Kafka y JMS comprobados')
if __name__=='__main__':
 mode=sys.argv[1] if len(sys.argv)>1 else 'all'
 if mode in ['all','batch']:run_batch()
 if mode in ['all','api']:run_api()

"""Comprueba registro Eureka y transferencias concurrentes con dos replicas."""
import demo,json,time,base64,uuid,subprocess
from decimal import Decimal
from urllib.request import Request,urlopen
from concurrent.futures import ThreadPoolExecutor
auth=base64.b64encode((demo.env['EUREKA_USER']+':'+demo.env['EUREKA_PASSWORD']).encode()).decode()
req=Request('http://localhost:8761/eureka/apps',headers={'Accept':'application/json','Authorization':'Basic '+auth})
with urlopen(req) as response:
 apps=json.load(response)['applications']['application']
for service in ['ACCOUNTS-SERVICE','PAYMENTS-SERVICE','CUSTOMERS-SERVICE']:
 instances=next(a['instance'] for a in apps if a['name']==service)
 up=[i for i in instances if i['status']=='UP']
 assert len(up)>=2,(service,len(up))
 print(service,len(up),'instancias UP')
web=demo.token('web-cli')
def balance(t):return demo.jsoncall(8091,'/api/web/dashboard',t)['accounts']['accounts'][0]['balance']
def target():
 r=subprocess.run(['docker','compose','exec','-T','postgres','psql','-U','postgres','-d','bank_accounts','-Atc','select balance from accounts where id=102'],cwd=demo.root,capture_output=True,text=True,check=True)
 return Decimal(r.stdout.strip())
source=balance(web);dest=target()
def send(n):
 key='parallel-'+uuid.uuid4().hex
 code,raw=demo.http(8091,'/api/web/payments','POST',web,{'type':'TRANSFER','source':101,'destination':102,'amount':1},{'Idempotency-Key':key})
 assert code==202,(code,raw)
 return json.loads(raw)['id']
with ThreadPoolExecutor(max_workers=8) as executor:ids=list(executor.map(send,range(20)))
for i in ids:
 for attempt in range(90):
  code,raw=demo.http(8091,'/api/web/payments/'+i,token=web);assert code==200
  p=json.loads(raw)
  if p['status']!='PENDING':break
  time.sleep(1)
 assert p['status']=='COMPLETED',p
assert balance(web)==source-20
assert target()==dest+20
print('20 transferencias concurrentes COMPLETED; debito 20 y credito 20, sin perdida de saldo')

"""Cliente HTTP de demostracion. No muestra tokens, claves ni PIN."""
import argparse,json,uuid
import demo
p=argparse.ArgumentParser()
p.add_argument('client',choices=['web','mobile','atm','teller','operator','reader','foreign'])
p.add_argument('port',type=int);p.add_argument('path')
p.add_argument('--method',default='GET');p.add_argument('--data');p.add_argument('--key')
a=p.parse_args();h={}
if a.method=='POST':h['Idempotency-Key']=a.key or 'cli-'+uuid.uuid4().hex
if a.client=='atm':h['X-ATM-PIN']=demo.env['ATM_PIN']
status,body=demo.http(a.port,a.path,a.method,demo.token(a.client+'-cli'),json.loads(a.data) if a.data else None,h)
print('HTTP',status)
try:print(json.dumps(json.loads(body),ensure_ascii=False,indent=2))
except ValueError:print(body.decode())
raise SystemExit(0 if status<400 else 1)

"""Pruebas controladas en los contenedores locales del proyecto."""
from pathlib import Path
import subprocess,time,uuid,sys,json,hashlib
import demo
root=Path(__file__).resolve().parents[1]
def compose(*args):subprocess.run(['docker','compose',*args],cwd=root,check=True,stdout=subprocess.DEVNULL)
def payments():
 web=demo.token('web-cli')
 before=demo.jsoncall(8081,'/api/accounts/101',web)['balance']
 p=demo.jsoncall(8082,'/api/payments',web,'POST',{'type':'WITHDRAWAL','source':101,'amount':1},{'Idempotency-Key':'fault-'+uuid.uuid4().hex},202)
 compose('pause','accounts-service')
 try:
  time.sleep(8)
  state=demo.jsoncall(8082,'/api/payments/'+p['id'],web)
  assert state['status']=='PENDING',state
  print('CUENTAS CAIDO: pago PENDING, saldo no confirmado')
  for i in range(3):
   dash=demo.jsoncall(8091,'/api/web/dashboard',web)
   assert dash['accounts'].get('available') is False,dash
  op=demo.jsoncall(8082,'/api/payments/operations/status',demo.token('operator-cli'))
  print(json.dumps(op))
 finally:compose('unpause','accounts-service')
 state=demo.complete(p['id'],web);assert state['status']=='COMPLETED',state
 after=demo.jsoncall(8081,'/api/accounts/101',web)['balance']
 assert after==before-1,(before,after)
 print('CUENTAS RECUPERADO: pago COMPLETED y descuento unico de 1')
def batch_restart():
 t=demo.token('operator-cli');rates=['0.031','0.12','0.08'];kind='annual';week='semana_1';period='2024'
 source=(root/'data'/week/'estados_financieros_anuales.csv').read_text()
 input_hash=hashlib.sha256(source.encode()).hexdigest()
 workers=demo.env['BATCH_WORKERS']
 key=kind+'-'+week+'-'+period+'-'+hashlib.sha256((input_hash+''.join(rates)+workers+'v1').encode()).hexdigest()[:12]
 compose('exec','-T','batch-service','sh','-c','mkdir -p /app/runtime/'+key+' && chmod 500 /app/runtime/'+key)
 try:
  p=demo.jsoncall(8084,f'/api/batch/{kind}?week={week}&period={period}&savingsRate={rates[0]}',t,'POST',expected=202)
  for i in range(60):
   state=demo.jsoncall(8084,'/api/batch/runs/'+str(p['executionId']),t)
   if len(state['history'])>=2:break
   time.sleep(1)
  assert state['status']=='FAILED' and len(state['history'])>=2,state
  print('JOB FAILED: reejecucion automatica registrada')
 finally:compose('exec','-T','batch-service','chmod','700','/app/runtime/'+key)
 for i in range(90):
  history=demo.jsoncall(8084,'/api/batch/runs/'+str(p['executionId']),t)['history']
  last=demo.jsoncall(8084,'/api/batch/runs/'+str(history[-1]['execution_id']),t)
  if last['status']=='COMPLETED':break
  time.sleep(1)
 assert last['status']=='COMPLETED',last
 print(json.dumps(last,ensure_ascii=False))
 print('JOB RECUPERADO: misma instancia, checkpoints y salida completa')
if __name__=='__main__':
 mode=sys.argv[1] if len(sys.argv)>1 else 'payments'
 if mode=='payments':payments()
 elif mode=='batch':batch_restart()
 else:raise SystemExit('Use payments o batch')

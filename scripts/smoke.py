import json,time,urllib.request,urllib.error
def request(body):
 req=urllib.request.Request('http://localhost:8080/api/quotes',data=json.dumps(body).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req,timeout=8) as response:return json.load(response)
for _ in range(90):
 try:
  with urllib.request.urlopen('http://localhost:8080/actuator/health',timeout=3) as r:
   if r.status==200:break
 except OSError:pass
 time.sleep(2)
else:raise RuntimeError('API not ready')
for mode in ['VIRTUAL','PLATFORM']:
 result=request({'mode':mode,'scenario':'SUCCESS','providers':6})
 assert len(result['results'])==6
 assert all(r['status']=='SUCCESS' and r['virtualThread']==(mode=='VIRTUAL') for r in result['results'])
for scenario in ['ERROR','SLOW','NEVER']:
 result=request({'mode':'VIRTUAL','scenario':scenario,'providers':3})
 assert all(r['status'] in (['HTTP_ERROR'] if scenario=='ERROR' else ['TIMEOUT','CANCELLED']) for r in result['results'])
try:request({'mode':'VIRTUAL','scenario':'SUCCESS','providers':13});raise AssertionError('Validation missing')
except urllib.error.HTTPError as e:assert e.code==400
with urllib.request.urlopen('http://localhost:4200') as r:assert b'<app-root>' in r.read()
print('Both executors, provider errors, deadline, validation and UI passed')

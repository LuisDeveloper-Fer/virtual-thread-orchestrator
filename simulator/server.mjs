import http from 'node:http';
http.createServer((req,res)=>{
 const scenario=new URL(req.url,'http://provider').searchParams.get('scenario');
 if(scenario==='NEVER') return;
 const timer=setTimeout(()=>{res.writeHead(scenario==='ERROR'?500:200,{'Content-Type':'application/json'});res.end(JSON.stringify({fictional:true}));},scenario==='SLOW'?2500:100);
 res.on('close',()=>clearTimeout(timer));
}).listen(Number(process.env.PORT||9096),'0.0.0.0');

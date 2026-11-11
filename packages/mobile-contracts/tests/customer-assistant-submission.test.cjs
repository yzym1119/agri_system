const {test}=require('node:test'),assert=require('node:assert/strict');
const load=()=>import('../dist/customer-assistant-submission.js');
const key='11111111-1111-4111-8111-111111111111';
function fixture(create){let raw=null,epoch=1,posts=0,state='REFERENCE',fail=true;const f={auth:{current:()=>({kind:'CUSTOMER',epoch,customerId:'101'})},storage:{read:()=>raw,write:(c,i,v)=>raw=v,remove:()=>raw=null},createKey:async()=>key,api:{submit:async(id,command)=>{assert.equal(JSON.parse(raw).phase,'SENT');posts++;if(fail)throw Error('NETWORK_FAILED');return{requestKey:key,caseId:id,state:'COMPLETED',queuedVersion:'2',factsVersion:'1',outcomeKind:'STAGED',handoffStatus:''}},result:async id=>({requestKey:key,caseId:id,state,queuedVersion:state==='NOT_FOUND'?null:'2',factsVersion:state==='NOT_FOUND'?null:'1',currentFactsVersion:'1',handoffStatus:'',missingFacts:[],citations:state==='REFERENCE'?[{title:'官方软件资料',url:'https://www.moa.gov.cn/a.html',publishedOn:'2026-11-10',quote:'这是一条用于软件验收的资料引用。'}]:[]})}};return{options:f,make:()=>create(f),raw:()=>raw,replace:v=>raw=v,posts:()=>posts,setState:v=>state=v,allow:()=>fail=false,switch:()=>epoch++};}
test('先可靠保存和SENT再发，丢响应与NOT_FOUND保留原稿，不能自动再付费',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions),s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));
 assert.equal(s.current('7').command.requestKey,key);f.setState('NOT_FOUND');assert.equal((await s.query('7')).state,'NOT_FOUND');assert.equal(f.posts(),1);assert.equal(s.current('7').phase,'SENT');
 await assert.rejects(s.submit('7',{expectedVersion:'2',factsVersion:'1',shareContextConfirmed:true}),/ASSISTANT_PENDING/);
});
test('重建运行时按原号核已完成结果，完整原体重放后才释放，保留已核原号供再读',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions);await assert.rejects(f.make().submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));f.allow();const next=f.make();assert.equal((await next.query('7')).state,'REFERENCE');assert.equal(f.posts(),2);assert.equal(next.current('7').phase,'VERIFIED');assert.equal(next.current('7').command.requestKey,key);
});
test('业务拒绝也不能推断AI多阶段未提交，存储替换/失效会话不能另起新号',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions);f.options.api.submit=async()=>{throw Error('BUSINESS_REJECTED')};const s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));assert.equal(s.current('7').phase,'SENT');f.replace(null);assert.throws(()=>s.current('7'),/STORAGE_RECORD_CHANGED/);
});
test('可靠存储失败零请求，账号切换后旧结果不能写VERIFIED',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions);f.options.storage.write=()=>{throw Error('disk')};await assert.rejects(f.make().submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}),/STORAGE_WRITE_FAILED/);assert.equal(f.posts(),0);
 const g=fixture(createAssistantSubmissions);let resolve;g.options.api.submit=async()=>new Promise(a=>resolve=a);const s=g.make(),work=s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true});await new Promise(r=>setImmediate(r));g.switch();resolve({requestKey:key,caseId:'7',state:'COMPLETED',queuedVersion:'2',factsVersion:'1',outcomeKind:'STAGED',handoffStatus:''});await assert.rejects(work,/STALE_SESSION/);assert.equal(JSON.parse(g.raw()).phase,'SENT');
});
test('已确认原稿重放若版本或内容不一致，继续冻结；查询运行中不POST派出',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions),s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));f.setState('QUEUED');assert.equal((await s.query('7')).state,'QUEUED');assert.equal(f.posts(),1);
 f.setState('REFERENCE');f.options.api.submit=async()=>({requestKey:key,caseId:'7',state:'COMPLETED',queuedVersion:'3',factsVersion:'1',outcomeKind:'STAGED',handoffStatus:''});await assert.rejects(s.query('7'),/ASSISTANT_RESPONSE_INVALID/);assert.equal(s.current('7').phase,'SENT');
});
test('查询返回伪装QUEUED的数组必须拒绝，不得误判终态后POST派出',async()=>{
 const {createAssistantSubmissions}=await load(),f=fixture(createAssistantSubmissions),s=f.make();await assert.rejects(s.submit('7',{expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));
 f.options.api.result=async()=>({requestKey:key,caseId:'7',state:['QUEUED'],queuedVersion:'2',factsVersion:'1',currentFactsVersion:'1',handoffStatus:'',missingFacts:[],citations:[]});
 await assert.rejects(s.query('7'));assert.equal(f.posts(),1);assert.equal(s.current('7').phase,'SENT');
});

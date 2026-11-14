const {test}=require('node:test'),assert=require('node:assert/strict');
const load=()=>import('../dist/customer-assistant.js');
const key='11111111-1111-4111-8111-111111111111';
const result=state=>({requestKey:key,caseId:'7',state,queuedVersion:'2',factsVersion:'1',currentFactsVersion:'1',handoffStatus:'',missingFacts:[],citations:[]});
test('AI结果只收白名单并核原号、版本和状态对应内容',async()=>{
 const {decodeAssistantResult}=await load();const raw={...result('REFERENCE'),citations:[{title:'官方资料',url:'https://www.moa.gov.cn/a.html',publishedOn:'2026-11-10',quote:'这是软件验收使用的观察记录摘录。',excerpt:'private'}],snapshot:'private'};
 const value=decodeAssistantResult(raw,'7',key);assert.equal(value.snapshot,undefined);assert.equal(value.citations[0].excerpt,undefined);
 for(const modify of [r=>r.requestKey='22222222-2222-4222-8222-222222222222',r=>r.caseId='8',r=>r.factsVersion='3',r=>r.citations[0].url='https://www.moa.gov.cn.evil/a',r=>r.state='UNKNOWN',r=>r.citations[0].publishedOn='2026-02-30',r=>r.missingFacts=['password']]){const r=structuredClone(raw);modify(r);assert.throws(()=>decodeAssistantResult(r,'7',key));}
});
test('不存在与未知分开，不能用缺收据或数字ID猜归属',async()=>{
 const {decodeAssistantResult}=await load();assert.equal(decodeAssistantResult({...result('NOT_FOUND'),queuedVersion:null,factsVersion:null},'7',key).state,'NOT_FOUND');
 assert.equal(decodeAssistantResult(result('UNKNOWN'),'7',key).state,'UNKNOWN');assert.throws(()=>decodeAssistantResult(result('NOT_FOUND'),'7',key));assert.throws(()=>decodeAssistantResult({...result('HUMAN'),handoffStatus:''},'7',key));
});
test('客户私有AI请求只发原UUID及版本同意，不传客户和自报授权，旧epoch不发送',async()=>{
 const {createCustomerAssistant}=await load();let epoch=3,calls=[];
 const api=createCustomerAssistant({current:()=>({kind:'CUSTOMER',epoch,customerId:'101'}),requestCustomer:async r=>{calls.push(r);return{requestKey:key,caseId:'7',state:'COMPLETED',queuedVersion:'2',factsVersion:'1',outcomeKind:'STAGED',handoffStatus:''}}});
 await api.submit('7',{requestKey:key,expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true,customerId:'999',authorization:'fake'},3);
 assert.equal(calls[0].url,'/api/client/agronomy/cases/7/assistant/requests');assert.deepEqual(Object.keys(calls[0].data),['requestKey','expectedVersion','factsVersion','shareContextConfirmed']);
 epoch=4;await assert.rejects(api.result('7',key,3),/STALE_SESSION/);assert.equal(calls.length,1);
});
test('状态/能力原因必须是字符串，数组不能冒充处理中或终态',async()=>{
 const {decodeAssistantResult,decodeAssistantCapability,decodeAssistantReceipt}=await load();
 assert.throws(()=>decodeAssistantResult({...result('QUEUED'),state:['QUEUED']},'7',key));
 assert.throws(()=>decodeAssistantCapability({enabled:false,reason:['DISABLED'],version:'1',factsVersion:'1'}));
 assert.throws(()=>decodeAssistantReceipt({requestKey:key,caseId:'7',state:'COMPLETED',queuedVersion:'2',factsVersion:'1',outcomeKind:['STAGED'],handoffStatus:''},'7',{requestKey:key,expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}));
});
test('服务器latest按本人问题解码，结束原号/原版本必须对应，不返回内部状态',async()=>{
 const {createCustomerAssistant,decodeAssistantEnd}=await load();const calls=[],command={requestKey:key,expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true};
 const api=createCustomerAssistant({current:()=>({kind:'CUSTOMER',epoch:1,customerId:'101'}),requestCustomer:async r=>{calls.push(r);return r.url.endsWith('/latest')?{result:result('UNKNOWN')}:{requestKey:key,caseId:'7',state:'CLOSED',expectedVersion:'1',factsVersion:'1',claimKey:'private'}}});
 assert.equal((await api.latest('7')).result.requestKey,key);const ended=await api.end('7',command);assert.equal(ended.state,'CLOSED');assert.equal(ended.claimKey,undefined);assert.equal(calls[1].url,'/api/client/agronomy/cases/7/assistant/end');
 assert.throws(()=>decodeAssistantEnd({...ended,expectedVersion:'2'},'7',command));assert.throws(()=>decodeAssistantEnd({...ended,state:['CLOSED']},'7',command));
});

test('首次服务状态固定本门店和真实会话，仅白名单响应，不承诺派出或收费',async()=>{
 const {createCustomerAssistant,decodeAssistantAvailability}=await load();let calls=[];
 const a=createCustomerAssistant({current:()=>({kind:'CUSTOMER',epoch:4,customerId:'101'}),requestCustomer:async r=>{calls.push(r);return{storeId:'7',enabled:true,reason:'READY',privateQuota:100}}});
 assert.deepEqual(await a.availability('7',4),{storeId:'7',enabled:true,reason:'READY'});
 assert.equal(calls[0].url,'/api/client/agronomy/assistant/availability?storeId=7');assert.equal(calls[0].method,'GET');
 await assert.rejects(a.availability('7',3),/STALE_SESSION/);await assert.rejects(a.availability('7.0',4));assert.equal(calls.length,1);
 for(const v of [{storeId:'8',enabled:true,reason:'READY'},{storeId:'7',enabled:false,reason:'READY'},{storeId:'7',enabled:false,reason:['DISABLED']},{storeId:'7',enabled:false,reason:'QUOTA_EXHAUSTED'}])assert.throws(()=>decodeAssistantAvailability(v,'7'));
});

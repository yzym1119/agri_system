const {test}=require('node:test'),assert=require('node:assert/strict');
const load=()=>import('../dist/customer-cases.js');
const key='11111111-1111-4111-8111-111111111111';
const facts={region:'大庆',crop:'玉米',growthStage:''};
const summary=(id='7')=>({id,storeId:'1',question:'虚构软件问题',facts,status:'COLLECTING',version:'1',factsVersion:'1',createdAt:'2026-11-10T10:20',updatedAt:'2026-11-10T10:20'});
const receipt=()=>({requestKey:key,caseId:'7',entryId:'1',version:'1',factsVersion:'1',status:'COLLECTING'});
const detail=()=>({problem:summary(),entries:[{id:'1',type:'QUESTION',actorType:'CUSTOMER',body:'虚构软件问题',sourceText:'',facts,version:'1',factsVersion:'1',createdAt:'2026-11-10T10:20'}],nextAfter:null,latestReply:null});
const linked=id=>({state:'LINKED',login:{tokenName:'Authorization',tokenValue:'fictional-'+id,userInfo:{id,name:'测试客户',role:'NORMAL',extra:{bindingState:'LINKED'}}}});
test('本人问题白名单不收内部派工或人员数据，农情版本/合法时间必须一致',async()=>{
 const {decodeCaseDetail}=await load();const raw=detail();raw.problem.customerId='999';raw.entries[0].actorId='999';const value=decodeCaseDetail(raw,'7');assert.equal(value.problem.customerId,undefined);assert.equal(value.entries[0].actorId,undefined);
 for(const change of [v=>v.problem.id='8',v=>v.problem.factsVersion='2',v=>v.entries[0].version='2',v=>v.entries[0].type='ASSIGN',v=>v.problem.updatedAt='2026-02-30T10:20',v=>v.entries[0].actorType='STAFF',v=>v.problem.status='CLOSED']){const v=detail();change(v);assert.throws(()=>decodeCaseDetail(v,'7'));}
});
test('本人分页20项及降序游标严格核对，原收据不被当前版本改写',async()=>{
 const {decodeCasePage,decodeCaseReceipt,decodeCaseSubmission}=await load();const rows=Array.from({length:20},(_,n)=>summary(String(30-n)));assert.equal(decodeCasePage({rows,nextAfter:'11'}).nextAfter,'11');assert.throws(()=>decodeCasePage({rows:rows.slice().reverse(),nextAfter:null}));assert.throws(()=>decodeCasePage({rows,nextAfter:'12'}));
 assert.deepEqual(decodeCaseReceipt(receipt(),key),receipt());assert.throws(()=>decodeCaseReceipt(receipt(),'22222222-2222-4222-8222-222222222222'));assert.throws(()=>decodeCaseReceipt(receipt(),key,'8'));assert.throws(()=>decodeCaseSubmission({requestKey:key,status:'UNKNOWN',receipt:receipt()},key));assert.equal(decodeCaseSubmission({requestKey:key,status:'UNKNOWN',receipt:null},key).status,'UNKNOWN');
});
test('真实客户会话才可读写，临时授权不发私有请求，固定URL与原号不得带业务身份',async()=>{
 const {createCustomerCases}=await load(),{createCustomerAuth}=await import('../dist/customer-session.js');const calls=[];
 const auth=createCustomerAuth(async r=>{calls.push(r);return{statusCode:200,data:r.url.endsWith('/wechat')?linked(7):r.url.endsWith('/submissions/'+key)?{requestKey:key,status:'COMPLETED',receipt:receipt()}:r.method==='POST'?receipt():r.url.endsWith('/7')?detail():{rows:[summary()],nextAfter:null}}},async()=> 'test-code');
 const api=createCustomerCases(auth);await assert.rejects(api.page(),/CUSTOMER_LOGIN_REQUIRED/);assert.equal(calls.length,0);await auth.beginWechatLogin().result;
 await api.page();await api.detail('7');await api.create({requestKey:key,storeId:'1',question:'虚构软件问题',facts,customerId:'999'});await api.submission(key);
 assert.equal(calls[3].url,'/api/client/agronomy/cases');assert.equal(calls[3].data.customerId,undefined);assert.equal(calls[3].header.Authorization,'fictional-7');
 const before=calls.length;await assert.rejects(api.detail('7.9'));await assert.rejects(api.create({requestKey:key,storeId:1.9,question:'测试',facts}));assert.equal(calls.length,before);
 const temp=createCustomerAuth(async()=>({statusCode:200,data:{state:'PHONE_REQUIRED',tokenName:'Authorization',tokenValue:'temp',expiresAt:1900000000,phoneMasked:null}}),async()=> 'code');await temp.beginWechatLogin().result;await assert.rejects(createCustomerCases(temp).page(),/CUSTOMER_LOGIN_REQUIRED/);
});
test('迟到私有成功或401不能串到新客户，原epoch请求不能借新token发出',async()=>{
 const {createCustomerCases}=await load(),{createCustomerAuth}=await import('../dist/customer-session.js');let finish,count=0,id=7;
 const auth=createCustomerAuth(async r=>{if(r.url.endsWith('/wechat'))return{statusCode:200,data:linked(id)};if(r.url.endsWith('/logout'))return{statusCode:200,data:{}};count++;return new Promise(resolve=>finish=resolve)},async()=> 'code');await auth.beginWechatLogin().result;const api=createCustomerCases(auth),epoch=auth.current().epoch;const pending=api.page();await auth.logout();id=8;await auth.beginWechatLogin().result;finish({statusCode:401});await assert.rejects(pending,/STALE_SESSION/);assert.equal(auth.current().customerId,'8');await assert.rejects(api.detail('7',undefined,epoch),/STALE_SESSION/);assert.equal(count,1);
});
test('明确发给门店只提交一次，初始待回复与原意图一致，不能把未配置AI的请求停在采集',async()=>{
 const {createCustomerCases}=await load();let command;
 const api=createCustomerCases({current:()=>({kind:'CUSTOMER',epoch:1,customerId:'7'}),requestCustomer:async r=>{command=r.data;return{...receipt(),status:'WAITING_REVIEW'}}});
 assert.equal((await api.create({requestKey:key,storeId:'1',question:'请门店协助',facts,requestHuman:true})).status,'WAITING_REVIEW');assert.equal(command.requestHuman,true);
});
test('返回数据必须落在原游标与状态筛选内，不能把另一页当作更早内容',async()=>{
 const {createCustomerCases}=await load();let result={rows:[summary('11')],nextAfter:null};const api=createCustomerCases({current:()=>({kind:'CUSTOMER',epoch:1,customerId:'7'}),requestCustomer:async()=>result});
 await assert.rejects(api.page('10'),/CASE_RESPONSE_INVALID/);result={rows:[summary('7')],nextAfter:null};await assert.rejects(api.page(undefined,'WAITING_REVIEW'),/CASE_RESPONSE_INVALID/);result=detail();await assert.rejects(api.detail('7','1'),/CASE_RESPONSE_INVALID/);
});
test('最新真实答复单独校验白名单与农情版本，不能用分页缺失冒充没有答复',async()=>{
 const {decodeCaseDetail}=await load();const reply={id:'2',type:'HUMAN_REPLY',actorType:'STAFF',body:'虚构此前答复',sourceText:'软件验收依据',facts,version:'2',factsVersion:'1',createdAt:'2026-11-10T10:20',actorId:'private'};
 const value={problem:{...summary(),version:'23',factsVersion:'2',status:'WAITING_REVIEW'},entries:Array.from({length:20},(_,n)=>({id:String(24-n),type:'HUMAN_REQUEST',actorType:'CUSTOMER',body:'请门店协助',sourceText:'',facts,version:String(23-n),factsVersion:'2',createdAt:'2026-11-10T10:20'})),nextAfter:'5',latestReply:reply};
 const decoded=decodeCaseDetail(value,'7');assert.equal(decoded.latestReply.body,'虚构此前答复');assert.equal(decoded.latestReply.actorId,undefined);assert.equal(decoded.latestReply.factsVersion,'1');
 for(const update of [{type:'SUPPLEMENT',actorType:'CUSTOMER'},{version:'24'},{factsVersion:'3'}])assert.throws(()=>decodeCaseDetail({...value,latestReply:{...reply,...update}},'7'));
 const old=detail();delete old.latestReply;assert.throws(()=>decodeCaseDetail(old,'7'));
});

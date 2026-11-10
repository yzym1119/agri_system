const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),vue=require('vue');
function load(file,names,overrides={}) {
  let source=fs.readFileSync(path.join(__dirname,'../src',file),'utf8');
  if(file.endsWith('.vue'))source=source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
  const ast=ts.createSourceFile(file,source,ts.ScriptTarget.Latest,true);
  const script=ast.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(ast)).join('\n');
  const context={exports:{},...vue,onMounted(){},onUnmounted(){},...overrides};vm.createContext(context);
  vm.runInContext(ts.transpileModule(script+`\nglobalThis.exposed={${names.join(',')}}`,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,context);
  return context.exposed;
}
test('核验路由同时要求原客户查询与关联查询，不因其中一个权限或菜单路径扩权',()=>{
  const p=load('router/registry.ts',['buildAppChildRoutes']);
  function contains(owned){return JSON.stringify(p.buildAppChildRoutes(owned)).includes('CustomerIdentityEnrollments');}
  assert.equal(contains(['customer:query']),false);assert.equal(contains(['customer:identity:query']),false);
  assert.equal(contains(['customer:query','customer:identity:query']),true);
  const permission=load('utils/permission.ts',['hasPermission']);
  assert.equal(permission.hasPermission(['customer:query','customer:identity:query'],['customer:identity:query'],'all'),false);
});
const key='22222222-2222-4222-8222-222222222222';
function detail(id='7'){return {request:{id,requestKey:'11111111-1111-4111-8111-111111111111',displayName:'申请人',phoneMasked:'138****0000',status:'REVIEW_REQUIRED',reviewReason:'PHONE_MATCH',createdAt:'2026-11-10T09:00:00'},appId:'wx1111111111111111',openIdMasked:'***1234',verifiedAt:'2026-11-10T09:00:00',candidates:[{id:'99',name:'原客户',phoneMasked:'138****0000',status:1,deleted:0}],customerId:null,identityId:null,identityAuthVersion:null,reviewedBy:null,verificationMethod:null,reason:null,reviewedAt:null};}
function page(overrides={}) {
  const user=vue.reactive({token:'token-a',userInfo:{id:1}}),permission=vue.reactive({permissions:['customer:query','customer:identity:query','customer:identity:bind']});
  const store=new Map(),calls=[],mounted=[],unmounted=[];
  const context={useUserStore:()=>user,usePermissionStore:()=>permission,crypto:{randomUUID:()=>key},
    localStorage:{getItem:k=>store.get(k)??null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)},
    onMounted:fn=>mounted.push(fn),onUnmounted:fn=>unmounted.push(fn),ElMessageBox:{confirm:async()=>{}},
    getEnrollmentQueue:async()=>({rows:[],nextBefore:null}),getEnrollment:async id=>detail(id),
    findEnrollmentCandidates:async()=>[],submitEnrollmentDecision:async(id,body)=>{calls.push({id,body});return {status:'LINKED'}},
    getEnrollmentDecision:async(id,requestKey)=>({enrollmentId:id,requestKey,status:'UNKNOWN',action:null,targetCustomerId:null,verificationMethod:null,reason:null}),...overrides};
  const p=load('views/customer/identity-enrollments/index.vue',['loadQueue','openEnrollment','draft','pending','detail','rows','error','message','submitDecision','queryPending','retryPending','restorePending','searchCandidates','candidateKeyword','searchedCandidates','canRead','canBind','closePendingApplication','acknowledgeConflict'],context);
  return {...p,user,permission,store,calls,mounted,unmounted};
}
function choose(p){Object.assign(p.draft,{action:'LINK',targetCustomerId:'99',verificationMethod:'IN_PERSON',reason:'已当面核对原客户资料',confirmed:true});}
test('查询失败不冒充空队列，只有关联操作权不能读客户',async()=>{
  const p=page({getEnrollmentQueue:async()=>{throw Error('network')}});await p.loadQueue();assert.ok(p.error.value);assert.equal(p.rows.value.length,0);
  p.permission.permissions=['customer:identity:bind'];assert.equal(p.canRead.value,false);assert.equal(p.canBind.value,false);
});
test('切换申请或账号后迟到详情不覆盖新对象',async()=>{
  let resolve;const p=page({getEnrollment:id=>id==='7'?new Promise(r=>resolve=r):Promise.resolve(detail(id))});
  const old=p.openEnrollment('7');await p.openEnrollment('8');resolve(detail('7'));await old;assert.equal(p.detail.value.request.id,'8');
  let later;const q=page({getEnrollment:()=>new Promise(r=>later=r)});const request=q.openEnrollment('7');q.user.token='token-b';later(detail('7'));await request;assert.equal(q.detail.value,null);
});
test('不默认选候选人，未核验不能提交，提交前必须可靠保存原决定',async()=>{
  const p=page();await p.openEnrollment('7');assert.equal(p.draft.targetCustomerId,'');await p.submitDecision();assert.equal(p.calls.length,0);
  choose(p);await p.submitDecision();assert.equal(p.calls.length,1);assert.equal(p.calls[0].body.requestKey,key);assert.equal(p.calls[0].body.targetCustomerId,'99');assert.equal(p.pending.value.requestKey,key);assert.equal(p.store.size,1);
});
test('丢响应后固定原内容，UNKNOWN不能解冻或当成功，同号重试才可恢复',async()=>{
  let query=0;const p=page({submitEnrollmentDecision:async(id,body)=>{p.calls.push({id,body:JSON.parse(JSON.stringify(body))});throw Error('lost')},getEnrollmentDecision:async(id,k)=>++query<2?{enrollmentId:id,requestKey:k,status:'UNKNOWN'}:{enrollmentId:id,requestKey:k,status:'LINKED',action:'LINK',targetCustomerId:'99',verificationMethod:'IN_PERSON',reason:'已当面核对原客户资料'}});
  await p.openEnrollment('7');choose(p);await p.submitDecision();assert.ok(p.pending.value);p.draft.reason='试图修改';await p.retryPending();
  assert.equal(p.calls.length,2);assert.equal(p.calls[1].body.reason,'已当面核对原客户资料');assert.equal(p.calls[0].body.requestKey,p.calls[1].body.requestKey);assert.equal(p.pending.value,null);assert.equal(p.store.size,0);
});
test('另一个决定或不同内容的最终结果不能被当作本次成功',async()=>{
  const p=page({getEnrollmentDecision:async(id,k)=>({enrollmentId:id,requestKey:k,status:'LINKED',action:'LINK',targetCustomerId:'100',verificationMethod:'IN_PERSON',reason:'别的决定'})});
  await p.openEnrollment('7');choose(p);await p.submitDecision();assert.ok(p.pending.value);assert.equal(p.store.size,1);assert.match(p.message.value,/核对|不一致/);
});
test('本机记录读取或保存失败必须停止写入，不能为了重试换号',async()=>{
  const p=page({localStorage:{getItem(){throw Error('storage')},setItem(){throw Error('storage')},removeItem(){throw Error('storage')}}});
  await p.restorePending();await p.openEnrollment('7');choose(p);await p.submitDecision();assert.equal(p.calls.length,0);assert.ok(p.error.value||p.message.value);
});
test('同号与其他号码候选读取不默认勾选，旧搜索响应不能污染新申请',async()=>{
  let resolve;const p=page({findEnrollmentCandidates:()=>new Promise(r=>resolve=r)});await p.openEnrollment('7');p.candidateKeyword.value='张';await p.searchCandidates();assert.equal(p.draft.targetCustomerId,'');
  p.candidateKeyword.value='张三';const old=p.searchCandidates();await p.openEnrollment('8');resolve([{id:'999',name:'旧词',phoneMasked:'139****0000',status:1,deleted:0}]);await old;assert.equal(p.searchedCandidates.value.length,0);
});
test('明确业务拒绝后可另行拒绝整次申请，持久最终拒绝阻止迟到关联并解除等待',async()=>{
  const second='33333333-3333-4333-8333-333333333333';let n=0,closed=null;
  const p=page({crypto:{randomUUID:()=>n++===0?key:second},submitEnrollmentDecision:async(id,body)=>{p.calls.push({id,body});if(body.action==='LINK')throw Error('target disabled');closed={enrollmentId:id,...body,status:'REJECTED'}},getEnrollmentDecision:async(id,k)=>k===second&&closed?closed:{enrollmentId:id,requestKey:k,status:'UNKNOWN'}});
  await p.openEnrollment('7');choose(p);await p.submitDecision();assert.ok(p.pending.value);await p.closePendingApplication();
  assert.equal(p.calls.length,2);assert.equal(p.calls[1].body.action,'REJECT');assert.equal(p.calls[1].body.requestKey,second);assert.equal(p.pending.value,null);assert.equal(p.store.size,0);
  await p.openEnrollment('8');assert.equal(p.detail.value.request.id,'8');
});
test('结束申请与原关联竞态时，原关联先完成就保留原关联结果，不误报已拒绝',async()=>{
  const second='33333333-3333-4333-8333-333333333333';let n=0,closing=false;
  const p=page({crypto:{randomUUID:()=>n++===0?key:second},submitEnrollmentDecision:async(id,body)=>{p.calls.push({id,body});if(body.action==='REJECT')closing=true;throw Error('lost')},getEnrollmentDecision:async(id,k)=>!closing?{enrollmentId:id,requestKey:k,status:'UNKNOWN'}:k===second?{enrollmentId:id,requestKey:k,status:'CONFLICT'}:{enrollmentId:id,requestKey:k,status:'LINKED',action:'LINK',targetCustomerId:'99',verificationMethod:'IN_PERSON',reason:'已当面核对原客户资料'}});
  await p.openEnrollment('7');choose(p);await p.submitDecision();await p.closePendingApplication();assert.equal(p.pending.value,null);assert.match(p.message.value,/关联/);assert.doesNotMatch(p.message.value,/已拒绝/);
});
test('结束申请丢响应后持久保存A和B，刷新与重试均只沿原B，不再次发送A',async()=>{
  const second='33333333-3333-4333-8333-333333333333';let n=0;
  const api={crypto:{randomUUID:()=>n++===0?key:second},submitEnrollmentDecision:async(id,body)=>{p.calls.push({id,body});throw Error('lost')},getEnrollmentDecision:async(id,k)=>({enrollmentId:id,requestKey:k,status:'UNKNOWN'})};
  const p=page(api);await p.openEnrollment('7');choose(p);await p.submitDecision();await p.closePendingApplication();await p.retryPending();
  assert.equal(p.pending.value.requestKey,key);assert.equal(p.pending.value.closingDecision.requestKey,second);assert.deepEqual(p.calls.map(c=>c.body.requestKey),[key,second,second]);
  const q=page({...api,localStorage:{getItem:k=>p.store.get(k)??null,setItem:(k,v)=>p.store.set(k,v),removeItem:k=>p.store.delete(k)}});await q.restorePending();assert.equal(q.pending.value.closingDecision.requestKey,second);assert.equal(q.pending.value.requestKey,key);assert.equal(n,2);
});
test('结束申请请求保存失败不发送B，原A继续保留且不允许继续提交',async()=>{
  const backing=new Map();let writes=0,n=0;const p=page({crypto:{randomUUID:()=>n++===0?key:'33333333-3333-4333-8333-333333333333'},localStorage:{getItem:k=>backing.get(k)??null,setItem:(k,v)=>{if(++writes===2)throw Error('full');backing.set(k,v)},removeItem:k=>backing.delete(k)}});
  await p.openEnrollment('7');choose(p);await p.submitDecision();await p.closePendingApplication();await p.retryPending();assert.equal(p.calls.length,1);assert.equal(p.pending.value.requestKey,key);assert.equal(p.pending.value.closingDecision,undefined);
});
test('核验API严格核对响应对象/请求号，并在请求配置固定当前会话',async()=>{
  const configs=[],user={token:'original-token'};let response=detail('8');
  const api=load('api/customer-enrollment.ts',['getEnrollment','getEnrollmentDecision','getEnrollmentQueue'],{useUserStore:()=>user,request:async c=>{configs.push(c);return response}});
  await assert.rejects(api.getEnrollment('7'));assert.equal(configs[0].expectedSessionToken,'original-token');
  response={enrollmentId:'7',requestKey:'33333333-3333-4333-8333-333333333333',status:'LINKED',action:'LINK',targetCustomerId:'99',verificationMethod:'IN_PERSON',reason:'其他请求'};await assert.rejects(api.getEnrollmentDecision('7',key));
  response={rows:[{...detail('7').request,id:9007199254740992}],nextBefore:null};await assert.rejects(api.getEnrollmentQueue('REVIEW_REQUIRED'));
});
test('查看第三人决定时A查询迟到，切账号后不得继续用新令牌请求旧B',async()=>{
  const second='33333333-3333-4333-8333-333333333333';let n=0,phase='initial',release;const queries=[];
  const p=page({crypto:{randomUUID:()=>n++===0?key:second},submitEnrollmentDecision:async(id,body)=>{if(body.action==='REJECT')phase='conflict';throw Error('lost')},getEnrollmentDecision:async(id,k)=>{queries.push({k,actor:p.user.userInfo.id});if(phase==='hold'&&k===key)return new Promise(r=>release=r);return {enrollmentId:id,requestKey:k,status:phase==='initial'?'UNKNOWN':'CONFLICT'}}});
  await p.openEnrollment('7');choose(p);await p.submitDecision();await p.closePendingApplication();phase='hold';const before=queries.length,ack=p.acknowledgeConflict();p.user.token='token-b';p.user.userInfo.id=2;release({enrollmentId:'7',requestKey:key,status:'CONFLICT'});await ack;
  assert.deepEqual(queries.slice(before),[{k:key,actor:1}]);assert.equal(p.pending.value,null);assert.equal(p.store.size,1);
});

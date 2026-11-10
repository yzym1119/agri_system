const {test}=require('node:test'),assert=require('node:assert/strict'),load=require('./helpers/knowledge.cjs');
const owner={actorId:'7',origin:'https://example.test'},key='11111111-1111-4111-8111-111111111111',group='22222222-2222-4222-8222-222222222222';
const content=()=>({title:'测试知识',summary:'摘要',crop:'玉米',region:'大庆',growthStage:'苗期',appliesTo:'适用\n第二行',exclusions:'不适用',body:'纯软件测试正文\n第二行',sourceText:'虚构软件来源',audience:'PUBLIC',riskLevel:'LOW',effectiveFrom:'2026-11-10',effectiveUntil:'2026-12-10'});
const revision=(extra={})=>({id:'5',logicalKey:key,revision:1,storeId:'1',content:content(),contentHash:'0'.repeat(64),state:'DRAFT',effectiveState:'DRAFT',version:'1',reviewedAt:null,...extra});
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}};
function setup(){const values=new Map();let creates=0;const storage={get length(){return values.size},key:n=>[...values.keys()][n]??null,getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
 const {createKnowledgeDraftRecovery}=load('utils/knowledge-draft');const recovery=createKnowledgeDraftRecovery(storage,()=>{creates++;return key});return{values,storage,recovery,creates:()=>creates};}
test('草稿先存读回再提交，网络未知沿原号原内容，编辑原表单不改变已存稿',async()=>{
 const s=setup(),c=content(),calls=[];s.recovery.prepare(owner,null,'1',0,c);c.body='之后编辑';
 const api={submit:async(p,token)=>{calls.push([JSON.parse(JSON.stringify(p)),token]);throw Error('lost')},submission:async()=>({requestKey:key,status:'UNKNOWN',revision:null})};
 await assert.rejects(s.recovery.retry(owner,'old-token',()=>true,api));assert.equal(s.recovery.restore(owner).command.content.body,content().body);
 assert.equal((await s.recovery.query(owner,'old-token',()=>true,api)).state,'UNKNOWN');assert.equal(calls.length,1);
 api.submit=async(p,token)=>{calls.push([p,token]);return revision()};assert.equal((await s.recovery.retry(owner,'old-token',()=>true,api)).state,'SAVED');assert.equal(calls[1][0].command.requestKey,key);assert.equal(s.recovery.restore(owner),null);assert.equal(s.creates(),1);
});
test('损坏、被替换或无法可靠读回的本机记录不发POST、不覆盖或删除',async()=>{
 const s=setup();s.recovery.prepare(owner,null,'1',0,content());const slot=[...s.values.keys()][0];s.values.set(slot,'{broken');assert.throws(()=>s.recovery.restore(owner));assert.throws(()=>s.recovery.prepare(owner,null,'1',0,content()));assert.equal(s.creates(),1);
 const other=setup(),d=deferred();other.recovery.prepare(owner,null,'1',0,content());const pending=other.recovery.retry(owner,'token',()=>true,{submit:()=>d.promise});const slot2=[...other.values.keys()][0];other.values.set(slot2,'replacement');d.resolve(revision());await assert.rejects(pending);assert.equal(other.values.get(slot2),'replacement');
 const blocked=setup();blocked.storage.setItem=()=>{};assert.throws(()=>blocked.recovery.prepare(owner,null,'1',0,content()));assert.equal(blocked.recovery.restore(owner),null);
});
test('会话变化后不继续发送后续请求，不清原员工记录，另一源站与员工看不到原稿',async()=>{
 const s=setup(),d=deferred();s.recovery.prepare(owner,null,'1',0,content());let current=true;
 const pending=s.recovery.query(owner,'old-token',()=>current,{submission:()=>d.promise});current=false;d.resolve({requestKey:key,status:'CREATED',revision:revision()});await assert.rejects(pending);assert.ok(s.recovery.restore(owner));
 assert.equal(s.recovery.restore({...owner,actorId:'8'}),null);assert.equal(s.recovery.restore({...owner,origin:'https://other.test'}),null);
});
test('UNKNOWN不等于未保存；仅用户确认且新版本屏障后再次核原号才可结束旧稿等待',async()=>{
 const s=setup();s.recovery.prepare(owner,group,'1',1,content());let reads=0;const api={latest:async()=>revision({logicalKey:group,revision:1}),submission:async()=>{reads++;return{requestKey:key,status:'UNKNOWN',revision:null}}};
 await assert.rejects(s.recovery.discardStale(owner,'t',()=>true,api,true));assert.equal(reads,0);assert.ok(s.recovery.restore(owner));
 api.latest=async()=>revision({logicalKey:group,revision:2});await assert.rejects(s.recovery.discardStale(owner,'t',()=>true,api,false));assert.ok(s.recovery.restore(owner));
 assert.equal((await s.recovery.discardStale(owner,'t',()=>true,api,true)).state,'DISCARDED_STALE');assert.equal(s.recovery.restore(owner),null);
});
test('核对期间原请求已保存则展示原结果，不误结束；异体回复不清记录',async()=>{
 const s=setup();s.recovery.prepare(owner,group,'1',1,content());const saved=revision({logicalKey:group,revision:2});
 const api={latest:async()=>revision({logicalKey:group,revision:3}),submission:async()=>({requestKey:key,status:'CREATED',revision:saved})};
 assert.equal((await s.recovery.discardStale(owner,'t',()=>true,api,true)).state,'SAVED');
 const other=setup();other.recovery.prepare(owner,null,'1',0,content());await assert.rejects(other.recovery.query(owner,'t',()=>true,{submission:async()=>({requestKey:key,status:'CREATED',revision:revision({content:{...content(),body:'不是原正文'}})})}));assert.ok(other.recovery.restore(owner));
});
test('知识白名单保持多行正文并核对安全ID、日期与分页方向',()=>{
 const api=load('api/knowledge-contract');const value=api.decodeKnowledgeRevision({...revision(),createdBy:999});assert.equal(value.content.body,content().body);assert.equal(value.createdBy,undefined);
 for(const bad of [revision({id:'9223372036854775808'}),revision({content:{...content(),effectiveFrom:'2026-02-30'}}),revision({revision:1.5})])assert.throws(()=>api.decodeKnowledgeRevision(bad));
 assert.throws(()=>api.decodeKnowledgePage({rows:[revision({id:'1'}),revision({id:'2'})],nextAfter:null}));
});
test('真实LocalDateTime整分钟可省略零秒，审核时间合法格式都能显示',()=>{
 const api=load('api/knowledge-contract');for(const reviewedAt of ['2026-11-10T10:20','2026-11-10T10:20:00','2026-11-10T10:20:00.123456'])assert.equal(api.decodeKnowledgeRevision(revision({state:'REVIEWED',effectiveState:'REVIEWED',version:'2',reviewedAt})).reviewedAt,reviewedAt);
 for(const reviewedAt of ['2026-02-30T10:20','2026-11-10T24:00','2026-11-10T10:99','2026-11-10T10:20:70'])assert.throws(()=>api.decodeKnowledgeRevision(revision({reviewedAt})));
});
test('多页面首次准备交错时各原稿按UUID保留，完成当前请求只清当前槽',async()=>{
 const s=setup(),otherKey='22222222-2222-4222-8222-222222222222';const {createKnowledgeDraftRecovery}=load('utils/knowledge-draft');const other=createKnowledgeDraftRecovery(s.storage,()=>otherKey),write=s.storage.setItem;let once=true;
 s.storage.setItem=(name,raw)=>{if(once){once=false;other.prepare(owner,null,'1',0,content())}write(name,raw)};
 assert.throws(()=>s.recovery.prepare(owner,null,'1',0,content()));assert.equal(s.values.size,2);assert.equal(s.recovery.restore(owner).command.requestKey,key);assert.equal(other.restore(owner).command.requestKey,otherKey);
 const result=await s.recovery.query(owner,'token',()=>true,{submission:async()=>({requestKey:key,status:'CREATED',revision:revision()})});assert.equal(result.state,'SAVED');assert.equal(s.values.size,1);assert.equal(s.recovery.restore(owner).command.requestKey,otherKey);
});

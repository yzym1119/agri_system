const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),vue=require('vue'),load=require('./helpers/knowledge.cjs');const {parse,compileScript}=require('@vue/compiler-sfc');
const key='11111111-1111-4111-8111-111111111111',draftKey='33333333-3333-4333-8333-333333333333';const d=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}};
const content=()=>({title:'测试知识',summary:'摘要',crop:'玉米',region:'大庆',growthStage:'苗期',appliesTo:'适用',exclusions:'不适用',body:'软件测试正文',sourceText:'虚构来源',audience:'PUBLIC',riskLevel:'LOW',effectiveFrom:'2026-11-10',effectiveUntil:'2026-12-10'});
const row=(revision=1)=>({id:String(revision),logicalKey:key,revision,storeId:'1',content:content(),contentHash:'0'.repeat(64),state:'DRAFT',effectiveState:'DRAFT',version:'1',reviewedAt:null});
function setup(overrides={}){
 const values=new Map(),storage={get length(){return values.size},key:n=>[...values.keys()][n]??null,getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
 const contract=load('api/knowledge-contract'),recovery=load('utils/knowledge-draft',{}, {crypto:{randomUUID:()=>draftKey}});const user=vue.reactive({token:'first-token',userInfo:{id:7,extra:{storeId:1,roleCodes:['SALE']}}}),permissions=vue.reactive({permissions:['agronomy:knowledge:query','agronomy:knowledge:write','agronomy:knowledge:review','agronomy:knowledge:publish']});
 const api={knowledgeOrigin:()=> 'https://example.test',knowledgePage:async()=>({rows:[],nextAfter:null}),knowledgeDetail:async(_key,n)=>row(n),knowledgeLatest:async()=>row(),knowledgeStoreOptions:async()=>[],knowledgeDecision:async()=>{throw Error('no action')},...overrides};api.knowledgeRecoveryApi={submit:async()=>{throw Error('lost')},submission:async()=>({requestKey:draftKey,status:'UNKNOWN',revision:null}),latest:api.knowledgeLatest,...overrides.knowledgeRecoveryApi};
 const hooks={},imports={vue:{...vue,onMounted:cb=>hooks.mount=cb,onUnmounted:cb=>hooks.unmount=cb},'@/store/user':{useUserStore:()=>user},'@/store/permission':{usePermissionStore:()=>permissions},'@/api/knowledge':api,'@/api/knowledge-contract':contract,'@/utils/knowledge-draft':recovery,'element-plus':{ElMessageBox:{confirm:async()=>true}}};
 const {descriptor}=parse(fs.readFileSync(path.join(__dirname,'../src/views/agronomy/knowledge/index.vue'),'utf8')),compiled=compileScript(descriptor,{id:'knowledge'}),module={exports:{}};
 vm.runInNewContext(ts.transpileModule(compiled.content,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{module,exports:module.exports,Error,console,localStorage:storage,crypto:{randomUUID:()=>draftKey},require:p=>{assert.ok(p in imports,p);return imports[p]}});
 const state=module.exports.default.setup({}, {expose(){}});return{state,user,permissions,hooks,values,api};
}
test('连续选择版本时旧详情不能盖新详情，旧员工响应不能污染新会话',async()=>{
 const a=d(),b=d();const v=setup({knowledgeDetail:(_key,n)=>n===1?a.promise:b.promise});const old=v.state.openRow(row(1)),fresh=v.state.openRow(row(2));b.resolve(row(2));await fresh;a.resolve(row(1));await old;assert.equal(v.state.detail.value.revision,2);
 const late=d();v.api.knowledgeDetail=()=>late.promise;const request=v.state.openRow(row(1));v.user.token='second-token';v.user.userInfo.id=8;await vue.nextTick();late.resolve(row(1));await request;assert.equal(v.state.detail.value,null);
});
test('草稿保存未知冻结原稿，不能重复新建，原号核对成功后再恢复',async()=>{
 let writes=0,records=[];const v=setup({knowledgePage:async()=>({rows:records,nextAfter:null}),knowledgeRecoveryApi:{submit:async()=>{writes++;throw Error('lost')}}});v.state.beginNew();Object.assign(v.state.draft,content());await v.state.saveDraft();assert.equal(writes,1);assert.ok(v.state.pending.value);v.state.beginNew();await v.state.saveDraft();assert.equal(writes,1);
 records=[{...row(),logicalKey:draftKey}];v.api.knowledgeRecoveryApi.submission=async()=>({requestKey:draftKey,status:'CREATED',revision:records[0]});await v.state.runPending('query');await new Promise(setImmediate);assert.equal(v.state.pending.value,null);assert.equal(v.state.detail.value.logicalKey,draftKey);assert.equal(v.state.rows.value.length,1);
});
test('审核需要原版本明确确认，未知后不重复提交，重读当前事实不冒称原操作成功',async()=>{
 let writes=0;const v=setup({knowledgeDecision:async()=>{writes++;throw Error('lost')}});await v.state.openRow(row());v.state.chooseDecision('review');v.state.reason.value='已核对';await v.state.submitDecision();assert.equal(writes,0);v.state.confirmed.value=true;await v.state.submitDecision();assert.equal(writes,1);assert.equal(v.state.decisionUnknown.value,true);await v.state.submitDecision();assert.equal(writes,1);v.state.closeDecision(false);v.state.chooseDecision('review');v.state.reason.value='再次提交';v.state.confirmed.value=true;await v.state.submitDecision();assert.equal(writes,1);await v.state.readDecision();assert.equal(v.state.confirmed.value,false);assert.equal(v.state.decisionOpen.value,false);assert.match(v.state.message.value,/当前|先前/);
});
test('只有查询权限不保存或审核，离页后原查询不清本机原稿',async()=>{
 const v=setup();v.permissions.permissions=['agronomy:knowledge:query'];await vue.nextTick();v.state.beginNew();Object.assign(v.state.draft,content());await v.state.saveDraft();assert.equal(v.state.pending.value,null);await v.state.openRow(row());v.state.chooseDecision('review');assert.equal(v.state.decisionOpen.value,false);v.hooks.unmount();
});
test('普通员工即使可查询门店也不切换知识所属门店，管理员降级立即失效旧选项',async()=>{
 let reads=0;const v=setup({knowledgeStoreOptions:async()=>{reads++;return[{id:'2',name:'别店'}]}});v.permissions.permissions.push('store:list');await vue.nextTick();await new Promise(setImmediate);assert.equal(reads,0);assert.equal(v.state.canStoreList.value,false);
 v.user.userInfo.extra.roleCodes=['ADMIN'];await vue.nextTick();await new Promise(setImmediate);assert.equal(v.state.canStoreList.value,true);v.state.selectedStore.value='2';await vue.nextTick();v.user.userInfo.extra.roleCodes=['SALE'];await vue.nextTick();await new Promise(setImmediate);assert.equal(v.state.selectedStore.value,'1');assert.equal(v.state.canStoreList.value,false);
});
test('合法异稿替换本机槽后仍保留原请求并冻结，不能接纳替换稿重试',async()=>{
 const response=d();let writes=0;const v=setup({knowledgeRecoveryApi:{submit:()=>{writes++;return response.promise}}});v.state.beginNew();Object.assign(v.state.draft,content());const saving=v.state.saveDraft();
 const slot=[...v.values.keys()][0],replacement=JSON.parse(v.values.get(slot));replacement.command.requestKey='44444444-4444-4444-8444-444444444444';replacement.command.content.body='另一份结构合法稿';v.values.set(slot,JSON.stringify(replacement));response.resolve({...row(),logicalKey:draftKey});await saving;
 assert.equal(v.state.pending.value.command.requestKey,draftKey);assert.equal(v.state.storageBlocked.value,true);v.state.restoreLocal();await v.state.runPending('retry');assert.equal(writes,1);assert.equal(v.state.pending.value.command.requestKey,draftKey);
});

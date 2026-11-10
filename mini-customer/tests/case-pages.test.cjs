const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');
const {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc'),vue=require('vue');
const {createCustomerAuth}=require('../../packages/mobile-contracts/dist/customer-session.js');
const facts={region:'大庆',crop:'玉米',growthStage:''};
const receipt=(id='7')=>({requestKey:'11111111-1111-4111-8111-111111111111',caseId:id,entryId:'1',version:'1',factsVersion:'1',status:'WAITING_REVIEW'});
const problem=(id='7',version='1')=>({id,storeId:'1',question:'虚构原问题',facts,status:'WAITING_REVIEW',version,factsVersion:'1',createdAt:'2026-11-10T10:20',updatedAt:'2026-11-10T10:20'});
const detail=(id='7',version='1')=>({problem:problem(id,version),entries:[{id:'2',type:'HUMAN_REQUEST',actorType:'CUSTOMER',body:'请帮忙',sourceText:'',facts,version:'1',factsVersion:'1',createdAt:'2026-11-10T10:20'}],nextAfter:null});
const deferred=()=>{let resolve,reject;const promise=new Promise((r,j)=>{resolve=r;reject=j});return{promise,resolve,reject}};
async function authFor(id='7'){let current=id;const auth=createCustomerAuth(async r=>({statusCode:200,data:r.url.endsWith('/wechat')?{state:'LINKED',login:{tokenName:'Authorization',tokenValue:'fictional-'+current,userInfo:{id:current,name:'虚构客户',role:'NORMAL',extra:{bindingState:'LINKED'}}}}:{}}),async()=> 'code');await auth.beginWechatLogin().result;return{auth,login:async id=>{await auth.logout();current=id;await auth.beginWechatLogin().result}}}
function mount(name,auth,api={},submissions={},catalog={stores:async()=>({rows:[{storeId:'1',name:'虚构门店',phone:'19900000000',address:'虚构地址'}],nextAfter:null})},configured=true){
 const file=path.join(__dirname,'../src/pages',name,'index.vue'),{descriptor}=parse(fs.readFileSync(file,'utf8')),script=compileScript(descriptor,{id:name}),module={exports:{}},hooks={},routes=[];
 const runtime={customerAuth:auth,customerCases:api,caseSubmissions:{settled:()=>null,...submissions},publicCatalog:catalog,serviceConfigured:configured,caseErrorText:()=> '原结果尚未核实'};
 const imports={vue,'@dcloudio/uni-app':{onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn,onLoad:fn=>hooks.load=fn},'@agri/mobile-contracts/ids':require('../../packages/mobile-contracts/dist/ids.js'),'@agri/mobile-contracts/customer-cases':require('../../packages/mobile-contracts/dist/customer-cases.js')};
 const config=JSON.parse(fs.readFileSync(path.join(__dirname,'../src/pages.json'),'utf8'));
 const route=o=>{assert.ok(config.pages.some(p=>'/'+p.path===o.url.split('?')[0]),'unregistered route '+o.url);routes.push(o.url)};
 vm.runInNewContext(ts.transpileModule(script.content,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{module,exports:module.exports,Error,console,uni:{navigateTo:route,reLaunch:route},require:key=>{if(key.endsWith('/services/runtime'))return runtime;if(key.endsWith('/services/preferences'))return{largeText:vue.ref(false)};assert.ok(key in imports,key);return imports[key]}});
 const state=module.exports.default.setup({}, {expose(){}});
 return{state,hooks,routes,async render(){const template=compileTemplate({source:descriptor.template.content,id:name,compilerOptions:{bindingMetadata:script.bindings}});assert.deepEqual(template.errors,[]);const output={exports:{}};vm.runInNewContext(ts.transpileModule(template.code,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{module:output,exports:output.exports,require:key=>{assert.equal(key,'vue');return vue}});const app=vue.createSSRApp({setup:()=>state,render:output.exports.render});for(const tag of ['CustomerNavigation','ReadingHeader','CasePending'])app.component(tag,{render:()=>vue.h('div')});return require('@vue/server-renderer').renderToString(app)}};
}
test('问农技游客先登录，正式客户一个动作就发给门店，隐藏后迟到成功不跳转',async()=>{
 let reads=0,posts=[];const guest=createCustomerAuth(async()=>{throw Error('no')},async()=> 'code');const g=mount('ask',guest,{}, {current:()=>{reads++;return null}});await g.hooks.show();assert.equal(reads,0);g.state.openLogin();assert.deepEqual(g.routes,['/pages/login/index']);
 const f=await authFor(),wait=deferred(),view=mount('ask',f.auth,{}, {current:()=>null,submit:async input=>{posts.push(input);return wait.promise}});await view.hooks.show();view.state.question.value='虚构描述';view.state.selectedStoreId.value='1';const send=view.state.submit();await new Promise(r=>setImmediate(r));assert.equal(posts.length,1);assert.equal(posts[0].requestHuman,true);assert.equal(posts[0].question,'虚构描述');view.hooks.hide();wait.resolve(receipt());await send;assert.deepEqual(view.routes,[]);
});
test('原问题恢复为冻结内容，合法异稿替换的读取失败不能接管新稿',async()=>{
 const f=await authFor();let broken=false;const original={schemaVersion:1,customerId:'7',phase:'SENT',operation:'CREATE',caseId:null,expectedFactsVersion:null,command:{requestKey:receipt().requestKey,storeId:'1',question:'原稿A',facts,requestHuman:true}};
 const submissions={current:()=>{if(broken)throw Error('STORAGE_RECORD_CHANGED');return original},query:async()=>{throw Error('STORAGE_RECORD_CHANGED')}};
 const view=mount('ask',f.auth,{},submissions);await view.hooks.show();assert.equal(view.state.question.value,'原稿A');broken=true;await view.state.checkOriginal();assert.equal(view.state.question.value,'原稿A');assert.equal(view.state.pending.value.command.question,'原稿A');assert.equal(view.state.blocked.value,true);
});
test('问题列表切客户与筛选时，旧结果不能覆盖新客户数据',async()=>{
 const f=await authFor(),old=deferred();let count=0;const api={page:async()=>++count===1?old.promise:{rows:[problem('8')],nextAfter:null}};
 const view=mount('cases/list',f.auth,api);const a=view.hooks.show();view.hooks.hide();await f.login('8');await view.hooks.show();old.resolve({rows:[problem('7')],nextAfter:null});await a;assert.equal(view.state.rows.value[0].id,'8');view.state.openProblem('8');assert.deepEqual(view.routes,['/pages/cases/detail/index?id=8']);
});
test('问题详情编号无效不取数，旧对象迟到响应不覆盖重新进入的数据',async()=>{
 const f=await authFor();let calls=0;const invalid=mount('cases/detail',f.auth,{detail:async()=>{calls++;return detail()}});invalid.hooks.load({id:'7.9'});await invalid.hooks.show();assert.equal(calls,0);assert.ok(invalid.state.error.value);
 const old=deferred();let n=0;const view=mount('cases/detail',f.auth,{detail:async()=>++n===1?old.promise:detail('7','2')},{current:()=>null});view.hooks.load({id:'7'});const a=view.hooks.show();view.hooks.hide();await view.hooks.show();old.resolve(detail('7','1'));await a;assert.equal(view.state.record.value.problem.version,'2');
});
test('读更早内容时发现版本变化，不拼接旧新回复，并暂停旧版本提交直到刷新',async()=>{
 const f=await authFor();let n=0,posts=0;const one={...detail(),nextAfter:'2'};const api={detail:async()=>++n===1?one:detail('7','2')};const view=mount('cases/detail',f.auth,api,{current:()=>null,submit:async()=>{posts++;return receipt()}});view.hooks.load({id:'7'});await view.hooks.show();await view.state.load(true);assert.equal(view.state.stale.value,true);view.state.body.value='不能按旧资料发出';await view.state.supplement();assert.equal(posts,0);assert.equal(view.state.record.value.problem.version,'1');
});
test('补充绑定原问题与版本，另一个原请求待核对时不再提交当前问题',async()=>{
 const f=await authFor();let calls=[];const pending={schemaVersion:1,customerId:'7',phase:'SENT',operation:'HUMAN_REQUEST',caseId:'9',expectedFactsVersion:'1',command:{requestKey:receipt().requestKey,expectedVersion:'1',body:'原问题求助'}};
 const view=mount('cases/detail',f.auth,{detail:async()=>detail('7')},{current:()=>pending,submit:async input=>{calls.push(input);return receipt()}});view.hooks.load({id:'7'});await view.hooks.show();view.state.body.value='补充';await view.state.supplement();assert.equal(calls.length,0);view.state.openOriginal();assert.deepEqual(view.routes,['/pages/cases/detail/index?id=9']);
});
test('未配置的提问页不能渲染成门店零结果；实际SFC模板应说明服务尚未连接',async()=>{
 const f=await authFor();let calls=0;const view=mount('ask',f.auth,{}, {current:()=>null},{stores:async()=>{calls++;return{rows:[],nextAfter:null}}},false);await view.hooks.show();const html=await view.render();assert.equal(calls,0);assert.ok(html.includes('门店服务暂未连接'));assert.equal(html.includes('暂时没有可接收问题的门店'),false);
});
test('隐藏期间已确认发送，重进只显示确认结果和查看入口，不预填旧问题再发一遍',async()=>{
 const f=await authFor(),wait=deferred(),view=mount('ask',f.auth,{}, {current:()=>null,submit:async()=>wait.promise});await view.hooks.show();view.state.question.value='原问题';view.state.selectedStoreId.value='1';const send=view.state.submit();view.hooks.hide();wait.resolve(receipt());await send;await view.hooks.show();assert.equal(view.state.question.value,'');assert.equal(view.state.completed.value.caseId,'7');assert.deepEqual(view.routes,[]);view.state.openOriginal();assert.deepEqual(view.routes,['/pages/cases/detail/index?id=7']);
});
async function realSubmissionHarness(auth){
 const {createCaseSubmissions}=require('../../packages/mobile-contracts/dist/customer-case-submission.js');const saved=new Map(),commands=[],wait=deferred();let keys=0,mode='pending';
 const api={create:async command=>{commands.push(command);if(mode==='unknown')throw Error('RESULT_UNKNOWN');if(mode==='pending')return wait.promise;return{...receipt(),requestKey:command.requestKey,caseId:'8'}},change:async()=>{throw Error('no')},submission:async key=>({requestKey:key,status:'UNKNOWN',receipt:null})};
 const run=createCaseSubmissions({auth,api,storage:{read:id=>saved.get(id)??null,write:(id,v)=>saved.set(id,v),remove:id=>saved.delete(id)},createKey:async()=>++keys===1?receipt().requestKey:'22222222-2222-4222-8222-222222222222'});
 return{run,wait,commands,mode:value=>mode=value};
}
test('独审复现：旧问农技实例成功清槽，新实例不能把旧问题解冻后另号再发',async()=>{
 const f=await authFor(),h=await realSubmissionHarness(f.auth);const a=mount('ask',f.auth,{},h.run);await a.hooks.show();a.state.question.value='同一原问题';a.state.selectedStoreId.value='1';const sending=a.state.submit();await new Promise(r=>setImmediate(r));a.hooks.hide();
 const b=mount('ask',f.auth,{},h.run);await b.hooks.show();assert.equal(b.state.question.value,'同一原问题');h.wait.resolve(receipt());await sending;h.mode('success');await b.state.checkOriginal();assert.equal(b.state.question.value,'');assert.equal(b.state.completed.value.caseId,'7');await b.state.submit();assert.equal(h.commands.length,1);
});
test('独审复现：旧未知重试隐藏期间成功，同一实例回前台不重新提交旧稿',async()=>{
 const f=await authFor(),h=await realSubmissionHarness(f.auth);h.mode('unknown');const view=mount('ask',f.auth,{},h.run);await view.hooks.show();view.state.question.value='恢复原问题';view.state.selectedStoreId.value='1';await view.state.submit();assert.ok(view.state.pending.value);h.mode('pending');const retry=view.state.retryOriginal();await new Promise(r=>setImmediate(r));view.hooks.hide();h.wait.resolve(receipt());await retry;await view.hooks.show();assert.equal(view.state.question.value,'');assert.equal(view.state.completed.value.caseId,'7');await view.state.submit();assert.equal(h.commands.length,2);assert.equal(h.commands[0].requestKey,h.commands[1].requestKey);
});
test('最新门店答复独立于20条历史分页，农情改变后明确标出此前答复',async()=>{
 const f=await authFor(),reply={id:'2',type:'HUMAN_REPLY',actorType:'STAFF',body:'虚构上次答复',sourceText:'虚构依据',facts,version:'2',factsVersion:'1',createdAt:'2026-11-10T10:20'};
 const data={problem:{...problem('7','23'),factsVersion:'2'},latestReply:reply,entries:Array.from({length:20},(_,n)=>({...detail().entries[0],id:String(24-n),version:String(23-n),factsVersion:'2'})),nextAfter:'5'};
 const view=mount('cases/detail',f.auth,{detail:async()=>data},{current:()=>null});view.hooks.load({id:'7'});await view.hooks.show();const html=await view.render();assert.ok(html.includes('虚构上次答复'));assert.ok(html.includes('此前情况'));assert.equal(view.state.showHistory.value,false);
});
test('跨实例首次发送已明确拒绝时保留原文供修改，不误称还在等待确认',async()=>{
 const f=await authFor(),h=await realSubmissionHarness(f.auth),a=mount('ask',f.auth,{},h.run);await a.hooks.show();a.state.question.value='待修改原稿';a.state.selectedStoreId.value='1';const sending=a.state.submit();await new Promise(r=>setImmediate(r));a.hooks.hide();const b=mount('ask',f.auth,{},h.run);await b.hooks.show();h.wait.reject(Error('BUSINESS_REJECTED'));await sending;await b.state.checkOriginal();assert.equal(b.state.question.value,'待修改原稿');assert.equal(b.state.pending.value,null);assert.ok(b.state.error.value.includes('没有发送成功'));assert.equal(h.commands.length,1);
});

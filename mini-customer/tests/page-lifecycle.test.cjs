const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');
const {parse,compileScript}=require('@vue/compiler-sfc');const vue=require('vue');
const {createCustomerAuth}=require('../../packages/mobile-contracts/dist/customer-session.js');
const linked=(id=7)=>({state:'LINKED',login:{tokenName:'Authorization',tokenValue:'fictional-'+id,userInfo:{id,name:'验收农户',role:'NORMAL',extra:{bindingState:'LINKED'}}}});
const ok=data=>({statusCode:200,data});const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return{promise,resolve}};
function page(name,auth,enrollment={},configured=true){
 const file=path.join(__dirname,'../src/pages',name,'index.vue'),{descriptor}=parse(fs.readFileSync(file,'utf8')),script=compileScript(descriptor,{id:name}),module={exports:{}},hooks={},routes=[];
 const runtime={customerAuth:auth,customerEnrollment:enrollment,serviceConfigured:configured,errorText:()=> '网络或资料暂未确认'};
 const imports={vue,'@dcloudio/uni-app':{onShow:cb=>hooks.show=cb,onHide:cb=>hooks.hide=cb,onUnload:cb=>hooks.unload=cb},'../../services/runtime':runtime,'../../../services/runtime':runtime,'../../services/preferences':{largeText:vue.ref(false)},'../../../services/preferences':{largeText:vue.ref(false)}};
 const config=JSON.parse(fs.readFileSync(path.join(__dirname,'../src/pages.json'),'utf8'));
 const route=(options,tab=false)=>{assert.ok((tab?config.tabBar?.list||[]:config.pages).some(p=>'/'+(p.pagePath||p.path)===options.url),'unregistered route '+options.url);routes.push(options.url)};
 vm.runInNewContext(ts.transpileModule(script.content,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{module,exports:module.exports,Error,console,uni:{navigateTo:o=>route(o),reLaunch:o=>route(o),switchTab:o=>route(o,true)},require:key=>{assert.ok(key in imports,key);return imports[key]}});
 return {state:module.exports.default.setup({}, {expose(){}}),hooks,routes};
}
test('真实客户登录页隐藏后晚到code零HTTP、零跳转，未配置时不取微信code',async()=>{
 const code=deferred();let requests=0,logins=0;const auth=createCustomerAuth(async()=>{requests++;return ok(linked())},()=>{logins++;return code.promise});const view=page('login',auth);view.hooks.show();const pending=view.state.login();view.hooks.hide();code.resolve('late');await pending;assert.equal(requests,0);assert.deepEqual(view.routes,[]);assert.equal(auth.current().kind,'GUEST');
 const other=page('login',auth,{},false);other.hooks.show();await other.state.login();assert.equal(logins,1);
});
test('确认客户登录后跳本人页，随后隐藏不撤销；页面没有任何客户令牌',async()=>{
 const auth=createCustomerAuth(async()=>ok(linked()),async()=> 'code');const view=page('login',auth);view.hooks.show();await view.state.login();view.hooks.hide();assert.equal(auth.current().kind,'CUSTOMER');assert.deepEqual(view.routes,['/pages/profile/index']);assert.equal(JSON.stringify(view.state.snapshot.value).includes('fictional'),false);
});
test('拒绝手机号授权不请求接口，旧手机号结果隐藏后不进入新页面',async()=>{
 const phone=deferred();let calls=0;const auth=createCustomerAuth(async r=>{calls++;return r.url.endsWith('/phone')?phone.promise:ok({state:'PHONE_REQUIRED',tokenName:'Authorization',tokenValue:'fictional-flow',expiresAt:1900000000,phoneMasked:null})},async()=> 'code');
 const view=page('login',auth);view.hooks.show();await view.state.login();await view.state.authorizePhone({detail:{}});assert.equal(calls,1);
 const pending=view.state.authorizePhone({detail:{code:'phone-code'}});view.hooks.hide();phone.resolve(ok({state:'PHONE_VERIFIED',tokenName:'Authorization',tokenValue:'fictional-proof',expiresAt:1900000000,phoneMasked:'138****0000'}));await pending;assert.equal(auth.current().kind,'GUEST');assert.deepEqual(view.routes,[]);
});
test('本人页隐藏期间的旧资料不能覆盖重新打开后的当前客户',async()=>{
 const a=deferred(),b=deferred();let count=0;const auth=createCustomerAuth(async r=>r.url.endsWith('/wechat')?ok(linked()):++count===1?a.promise:b.promise,async()=> 'wx');await auth.beginWechatLogin().result;
 const view=page('profile',auth);const first=view.hooks.show();view.hooks.hide();const second=view.hooks.show();b.resolve(ok({customerId:'7',name:'最新资料',phoneMasked:null,bindingState:'LINKED'}));await second;a.resolve({statusCode:401});await first;assert.equal(view.state.profile.value.name,'最新资料');assert.equal(auth.current().kind,'CUSTOMER');
});
test('核验页结果未知不换申请，隐藏时旧结果不清除新页面状态',async()=>{
 const auth=createCustomerAuth(async()=>ok(linked()),async()=> 'wx');await auth.beginWechatLogin().result;const delayed=deferred();let submits=0,queries=0;
 const enrollment={current:()=>({requestKey:'original'}),query:()=>{queries++;return delayed.promise},submit:()=>{submits++;return Promise.resolve(null)}};
 const view=page('identity/claim',auth,enrollment);const pending=view.hooks.show();view.hooks.hide();delayed.resolve({epoch:auth.current().epoch,result:{requestKey:'original',status:'UNKNOWN',nextAction:'RETRY_SAME_REQUEST'}});await pending;assert.equal(submits,0);assert.equal(queries,1);assert.equal(view.state.outcome.value,null);assert.deepEqual(view.routes,[]);
});
test('首页游客可进入且不调用登录，停留不会自动建客户或钱包',async()=>{
 const auth=createCustomerAuth(async()=>{throw Error('must not call')},async()=>{throw Error('must not login')});const view=page('home',auth);view.hooks.show();assert.equal(auth.current().kind,'GUEST');view.state.openAccount();assert.deepEqual(view.routes,['/pages/login/index']);
});
test('另一登录完成后，旧登录失败不向当前页面写错误或跳转',async()=>{
 const old=deferred();let calls=0;const auth=createCustomerAuth(async()=>++calls===1?old.promise:ok(linked(8)),async()=> 'wx');
 const view=page('login',auth);view.hooks.show();const pending=view.state.login();await new Promise(setImmediate);await auth.beginWechatLogin().result;old.resolve({statusCode:401});await pending;
 assert.equal(view.state.error.value,'');assert.equal(auth.current().customerId,'8');assert.deepEqual(view.routes,[]);
});
test('另一登录完成后，旧本人资料失败不向当前页面写错误',async()=>{
 const old=deferred();let identity=7;const auth=createCustomerAuth(async r=>r.url.endsWith('/wechat')?ok(linked(identity)):old.promise,async()=> 'wx');await auth.beginWechatLogin().result;
 const view=page('profile',auth);const pending=view.hooks.show();identity=8;await auth.beginWechatLogin().result;old.resolve({statusCode:401});await pending;
 assert.equal(view.state.error.value,'');assert.equal(auth.current().customerId,'8');assert.equal(view.state.profile.value,null);
});

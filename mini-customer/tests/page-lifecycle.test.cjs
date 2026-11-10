const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');
const {parse,compileScript}=require('@vue/compiler-sfc');const vue=require('vue');
const {createCustomerAuth}=require('../../packages/mobile-contracts/dist/customer-session.js');
const linked=(id=7)=>({state:'LINKED',login:{tokenName:'Authorization',tokenValue:'fictional-'+id,userInfo:{id,name:'验收农户',role:'NORMAL',extra:{bindingState:'LINKED'}}}});
const ok=data=>({statusCode:200,data});const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return{promise,resolve}};
function page(name,auth,enrollment={},configured=true,catalog={stores:async()=>({rows:[],nextAfter:null})},platform={},knowledge={scopes:async()=>[]}){
 const file=path.join(__dirname,'../src/pages',name,'index.vue'),{descriptor}=parse(fs.readFileSync(file,'utf8')),script=compileScript(descriptor,{id:name}),module={exports:{}},hooks={},routes=[];
 const runtime={customerAuth:auth,customerEnrollment:enrollment,publicCatalog:catalog,publicKnowledge:knowledge,serviceConfigured:configured,errorText:()=> '网络或资料暂未确认'};
 const imports={vue,'@dcloudio/uni-app':{onShow:cb=>hooks.show=cb,onHide:cb=>hooks.hide=cb,onUnload:cb=>hooks.unload=cb,onLoad:cb=>hooks.load=cb},'@agri/mobile-contracts/ids':require('../../packages/mobile-contracts/dist/ids.js'),'@agri/mobile-contracts/customer-knowledge':require('../../packages/mobile-contracts/dist/customer-knowledge.js'),'../../services/runtime':runtime,'../../../services/runtime':runtime,'../../services/preferences':{largeText:vue.ref(false)},'../../../services/preferences':{largeText:vue.ref(false)}};
 const config=JSON.parse(fs.readFileSync(path.join(__dirname,'../src/pages.json'),'utf8'));
 const route=(options,tab=false)=>{assert.ok((tab?config.tabBar?.list||[]:config.pages).some(p=>'/'+(p.pagePath||p.path)===options.url.split('?')[0]),'unregistered route '+options.url);routes.push(options.url)};
 vm.runInNewContext(ts.transpileModule(script.content,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{module,exports:module.exports,Error,console,uni:{navigateTo:o=>route(o),reLaunch:o=>route(o),switchTab:o=>route(o,true),...platform},require:key=>{assert.ok(key in imports,key);return imports[key]}});
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
test('首页经过真实共享解码保留多行地址，同页两店可见且复制原文，不请求登录',async()=>{
 const {createPublicCatalog}=await import('../../packages/mobile-contracts/dist/customer-catalog.js');
 const auth=createCustomerAuth(async()=>{throw Error('must not call')},async()=>{throw Error('must not login')});
 const address='大庆市测试街道\r\n二楼入口',requests=[],copied=[];
 const catalog=createPublicCatalog(async request=>{requests.push(request);return ok({rows:[{storeId:'1',name:'一号门店',phone:'13800000000',address:'一楼'},{storeId:'2',name:'二号门店',phone:'13800000001',address}],nextAfter:null})});
 const view=page('home',auth,{},true,catalog,{setClipboardData:options=>copied.push(options.data)});await view.hooks.show();
 assert.equal(view.state.storeError.value,'');assert.equal(view.state.stores.value.length,2);assert.equal(view.state.stores.value[1].address,address);
 view.state.copyAddress(view.state.stores.value[1]);assert.deepEqual(copied,[address]);assert.equal(requests.length,1);assert.equal(auth.current().kind,'GUEST');
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
test('商品列表重新进入时旧查询不得覆盖新结果，公开查询不取得客户登录',async()=>{
 const a=deferred(),b=deferred();let reads=0;const auth=createCustomerAuth(async()=>{throw Error('no auth')},async()=>{throw Error('no login')});
 const view=page('catalog/list',auth,{},true,{products:()=>++reads===1?a.promise:b.promise});const first=view.hooks.show();view.hooks.hide();const second=view.hooks.show();b.resolve({rows:[{productId:'8',name:'新商品'}],nextAfter:null});await second;a.resolve({rows:[{productId:'7',name:'旧商品'}],nextAfter:null});await first;assert.equal(view.state.rows.value[0].productId,'8');assert.equal(auth.current().kind,'GUEST');
});
test('切换销售单位的旧参考价不能盖掉当前选择，无效商品ID零请求',async()=>{
 const a=deferred(),b=deferred();let reads=0;const sample=unit=>({product:{productId:'7',name:'商品'},baseUnitName:'瓶',allowFractionalBaseQuantity:false,saleUnits:[{unitName:'瓶',baseQuantityPerUnit:'1'},{unitName:'箱',baseQuantityPerUnit:'12'}],referencePrice:{productId:'7',unitName:unit,unitPrice:unit==='箱'?'120.00':'10.00',state:'AVAILABLE'}});
 const auth=createCustomerAuth(async()=>{throw Error()},async()=> 'wx'),catalog={product:()=>++reads===1?Promise.resolve(sample('瓶')):reads===2?a.promise:b.promise};
 const view=page('catalog/detail',auth,{},true,catalog);view.hooks.load({productId:'7'});await view.hooks.show();const old=view.state.selectUnit('箱');const latest=view.state.selectUnit('瓶');b.resolve(sample('瓶'));await latest;a.resolve(sample('箱'));await old;assert.equal(view.state.detail.value.referencePrice.unitName,'瓶');
 const bad=page('catalog/detail',auth,{},true,catalog);bad.hooks.load({productId:'90071992547409930000'});await bad.hooks.show();assert.equal(reads,3);
});
test('知识必须主动选全范围，切地区后旧结果不得混入，游客不登录',async()=>{
 const a=deferred(),b=deferred();let calls=0;const scopes=[{region:'大庆',crop:'玉米',growthStage:'苗期'},{region:'外地',crop:'水稻',growthStage:'苗期'}];
 const auth=createCustomerAuth(async()=>{throw Error('no login')},async()=>{throw Error('no login')}),api={scopes:async()=>scopes,page:()=>++calls===1?a.promise:b.promise};
 const view=page('knowledge/list',auth,{},true,{}, {},api);await view.hooks.show();assert.equal(calls,0);await view.state.search();assert.equal(calls,0);
 for(const name of ['selectRegion','selectCrop','selectStage'])view.state[name]({detail:{value:'0'}});const first=view.state.search();
 view.state.selectRegion({detail:{value:'1'}});view.state.selectCrop({detail:{value:'0'}});view.state.selectStage({detail:{value:'0'}});const second=view.state.search();
 b.resolve({rows:[{id:'2',logicalKey:'11111111-1111-4111-8111-111111111111',revision:1,title:'当前地区',scope:scopes[1]}],nextAfter:null});await second;a.resolve({rows:[{id:'1',title:'旧地区',scope:scopes[0]}],nextAfter:null});await first;
 assert.equal(view.state.rows.value[0].title,'当前地区');assert.equal(auth.current().kind,'GUEST');view.state.open(view.state.rows.value[0]);assert.match(view.routes[0],/pages\/knowledge\/detail\/index\?/);
});
test('知识重新进入时重读范围，已撤回的旧范围不保留旧文章',async()=>{
 const scope={region:'大庆',crop:'玉米',growthStage:'苗期'};let available=true;const auth=createCustomerAuth(async()=>{throw Error()},async()=> 'unused');
 const view=page('knowledge/list',auth,{},true,{}, {},{scopes:async()=>available?[scope]:[],page:async()=>({rows:[{id:'1',title:'原文章',scope}],nextAfter:null})});await view.hooks.show();for(const name of ['selectRegion','selectCrop','selectStage'])view.state[name]({detail:{value:'0'}});await view.state.search();assert.equal(view.state.rows.value.length,1);view.hooks.hide();available=false;await view.hooks.show();assert.equal(view.state.rows.value.length,0);assert.equal(view.state.region.value,'');
});
test('知识详情固定原版本和完整范围，旧失败不留旧正文，非法参数零请求',async()=>{
 const scope={region:'大庆%20区',crop:'玉米',growthStage:'苗期'},key='11111111-1111-4111-8111-111111111111',auth=createCustomerAuth(async()=>{throw Error()},async()=> 'unused');let calls=0,failed=false;
 const api={detail:async(k,n,s)=>{calls++;assert.equal(k,key);assert.equal(n,1);assert.equal(s.region,scope.region);if(failed)throw Error('withdrawn');return{article:{id:'1',logicalKey:key,revision:1,title:'文章',scope},body:'原正文'}}};
 const view=page('knowledge/detail',auth,{},true,{}, {},api);view.hooks.load({logicalKey:key,revision:'1',scope:encodeURIComponent(JSON.stringify(scope))});await view.hooks.show();assert.equal(view.state.article.value.body,'原正文');view.hooks.hide();failed=true;await view.hooks.show();assert.equal(view.state.article.value,null);assert.match(view.state.error.value,/更新|撤回/);
 const bad=page('knowledge/detail',auth,{},true,{}, {},api);bad.hooks.load({logicalKey:key,revision:'1.5',scope:JSON.stringify(scope)});await bad.hooks.show();assert.equal(calls,2);
});

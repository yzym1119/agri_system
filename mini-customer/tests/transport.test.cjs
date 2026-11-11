const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');
function transport(){const file=path.join(__dirname,'../src/services/transport.ts'),module={exports:{}};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{module,exports:module.exports,Error,Number,RegExp,String,require:name=>{assert.equal(name,'@agri/mobile-contracts/ids');return require('../../packages/mobile-contracts/dist/ids.js')}});return module.exports;}
test('未配置或非法源站不发code，生产拒绝HTTP，开发仅明确内网/回环',async()=>{
 const {createCustomerTransport}=transport();
 for(const [origin,dev,valid] of [['',false,false],['https://example.test/path',false,false],['https://user@example.test',false,false],['https://example.test?token=x',false,false],['https://example.test:99999',false,false],['http://127.0.0.1:9087',false,false],['http://example.test',true,false],['http://192.168.999.1',true,false],['http://192.168.1.2:9087',true,true],['https://example.test:443',false,true]]){
  let calls=0;const source=createCustomerTransport(origin,dev,options=>{calls++;options.success({statusCode:200,data:{}})});assert.equal(source.configured,valid,origin);
  const pending=source.send({url:'/auth/client/onboarding/wechat',method:'POST',data:{code:'fictional'}});if(valid)await pending;else await assert.rejects(pending,/SERVICE_NOT_CONFIGURED/);assert.equal(calls,valid?1:0);
 }
});
test('固定源站与白名单路径，拒绝任意URL、员工入口、查询和越界header',async()=>{
 const {createCustomerTransport}=transport();let calls=[];const source=createCustomerTransport('https://example.test/',false,o=>{calls.push(o);o.success({statusCode:200,data:{state:'PHONE_REQUIRED'}})});
 await source.send({url:'/auth/client/onboarding/wechat',method:'POST',data:{code:'fictional'}});assert.equal(calls[0].url,'https://example.test/auth/client/onboarding/wechat');assert.equal(calls[0].timeout,10000);
 for(const request of [{url:'https://evil.test',method:'POST'},{url:'/auth/staff/login',method:'POST'},{url:'/auth/client/info?customerId=2',method:'GET'},{url:'/auth/client/info',method:'POST'},{url:'/auth/client/info',method:'GET',header:{Cookie:'wrong'}}]) await assert.rejects(source.send(request),/CUSTOMER_REQUEST_INVALID/);
 assert.equal(calls.length,1);
});
test('平台网络错误只回固定错误，不回传原错误或凭据',async()=>{
 const {createCustomerTransport}=transport();const source=createCustomerTransport('https://example.test',false,o=>o.fail({errMsg:'secret URL and credential'}));
 await assert.rejects(source.send({url:'/auth/client/info',method:'GET'}),e=>e.message==='NETWORK_FAILED');
});
test('公开请求独立白名单且不带登录凭据，不能借公开路径访问后台或私有接口',async()=>{
 const {createCustomerTransport}=transport();const calls=[],source=createCustomerTransport('https://example.test',false,o=>{calls.push(o);o.success({statusCode:200,data:{rows:[],nextAfter:null}})});
 await source.sendPublic({path:'/api/public/catalog/products',query:{keyword:'玉米',after:'9'},header:{Authorization:'must-not-send'}});
 assert.equal(calls[0].header.Authorization,undefined);assert.equal(calls[0].method,'GET');assert.match(calls[0].url,/keyword=%E7%8E%89%E7%B1%B3/);
 for(const request of [{path:'/api/admin/stores',query:{}},{path:'/auth/client/info',query:{}},{path:'/api/public/catalog/products',query:{customerId:'7'}},{path:'/api/public/stores?token=x',query:{}}])await assert.rejects(source.sendPublic(request),/PUBLIC_REQUEST_INVALID/);
 assert.equal(calls.length,1);
});
test('知识匿名查询只允许固定只读路径和完整白名单参数，不携带客户凭据',async()=>{
 const {createCustomerTransport}=transport();const requests=[];const source=createCustomerTransport('https://example.test',false,o=>{requests.push(o);o.success({statusCode:200,data:[]})});
 await source.sendPublic({path:'/api/public/agronomy/knowledge/scopes',query:{},header:{Authorization:'must-not-send'}});
 await source.sendPublic({path:'/api/public/agronomy/knowledge',query:{region:'大庆',crop:'玉米',growthStage:'苗期'}});
 await source.sendPublic({path:'/api/public/agronomy/knowledge/11111111-1111-4111-8111-111111111111',query:{region:'大庆',crop:'玉米',growthStage:'苗期',expectedRevision:'1'}});
 assert.equal(requests.every(r=>r.method==='GET'&&!r.header.Authorization),true);
 await assert.rejects(source.sendPublic({path:'/api/public/agronomy/knowledge/scopes',query:{customerId:'1'}}));await assert.rejects(source.sendPublic({path:'/api/public/agronomy/knowledge/11111111-1111-4111-8111-111111111111/publish',query:{}}));assert.equal(requests.length,3);
});
test('本人农技路径仅正式私有端点与固定分页，不能借URL扩展后台、身份或动作',async()=>{
 const {createCustomerTransport}=transport(),requests=[];const source=createCustomerTransport('https://client.example.com',false,r=>{requests.push(r);r.success({statusCode:200,data:{}})});
 for(const url of ['/api/client/agronomy/cases','/api/client/agronomy/cases?after=10&status=WAITING_REVIEW','/api/client/agronomy/cases/7?after=20','/api/client/agronomy/cases/submissions/11111111-1111-4111-8111-111111111111'])await source.send({url,method:'GET',header:{Authorization:'fictional-customer'}});
 for(const url of ['/api/client/agronomy/cases','/api/client/agronomy/cases/7/supplements','/api/client/agronomy/cases/7/human-requests'])await source.send({url,method:'POST',data:{},header:{Authorization:'fictional-customer'}});
 const count=requests.length;for(const url of ['/api/client/agronomy/cases?customerId=999','/api/client/agronomy/cases?after=1&after=2','/api/client/agronomy/cases/7?status=ACTIVE','/api/client/agronomy/cases?status=CLOSED','/api/client/agronomy/cases/1.9','/api/client/agronomy/cases/7/replies','https://evil.example.com/api/client/agronomy/cases'])await assert.rejects(source.send({url,method:'GET',header:{Authorization:'fictional-customer'}}));
 await assert.rejects(source.send({url:'/api/client/agronomy/cases/7/assignment',method:'POST',data:{},header:{Authorization:'fictional-customer'}}));assert.equal(requests.length,count);assert.equal(requests.every(r=>r.url.startsWith('https://client.example.com/api/client/agronomy/cases')),true);
});
test('客户AI只开放三个固定本人端点，提交有足够等待时间但不放宽其他网络入口',async()=>{
 const {createCustomerTransport}=transport();const calls=[],base='/api/client/agronomy/cases/7/assistant',key='11111111-1111-4111-8111-111111111111';const client=createCustomerTransport('https://example.test',false,o=>{calls.push(o);o.success({statusCode:200,data:{}})});
 await client.send({url:base+'/capability',method:'GET'});await client.send({url:base+'/requests',method:'POST',data:{}});await client.send({url:base+'/requests/'+key,method:'GET'});assert.equal(calls[1].timeout,45000);assert.equal(calls[0].timeout,10000);
 for(const [url,method] of [[base+'/requests?customerId=8','POST'],[base+'/requests/'+key,'POST'],[base+'/capability','POST'],[base.replace('/7/','/7.1/')+'/requests','POST']])await assert.rejects(client.send({url,method}),/CUSTOMER_REQUEST_INVALID/);assert.equal(calls.length,3);
});

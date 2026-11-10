const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');
function transport(){const file=path.join(__dirname,'../src/services/transport.ts'),module={exports:{}};vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{module,exports:module.exports,Error,Number,RegExp,String});return module.exports;}
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

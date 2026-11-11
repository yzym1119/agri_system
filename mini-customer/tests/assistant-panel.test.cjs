const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),vue=require('vue');
const {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc');
const key='11111111-1111-4111-8111-111111111111';
function mount(runtime,initial={}){const {descriptor}=parse(fs.readFileSync(path.join(__dirname,'../src/components/CaseAssistant/CaseAssistant.vue'),'utf8')),script=compileScript(descriptor,{id:'assistant'}),module={exports:{}},events=[];const props=vue.reactive({caseId:'7',version:'1',factsVersion:'1',active:true,disabled:false,...initial});
vm.runInNewContext(ts.transpileModule(script.content,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{module,exports:module.exports,Error,console,uni:{setClipboardData(){}},require:k=>k==='vue'?vue:k.endsWith('/services/runtime')?runtime:(()=>{throw Error(k)})()});const scope=vue.effectScope(),state=scope.run(()=>module.exports.default.setup(props,{expose(){},emit:(...a)=>events.push(a)}));
return{state,props,events,scope,async render(){const t=compileTemplate({source:descriptor.template.content,id:'assistant',compilerOptions:{bindingMetadata:script.bindings}});assert.deepEqual(t.errors,[]);const m={exports:{}};vm.runInNewContext(ts.transpileModule(t.code,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{module:m,exports:m.exports,require:k=>vue});return require('@vue/server-renderer').renderToString(vue.createSSRApp({props:['caseId','version','factsVersion','active','disabled'],setup:()=>state,render:m.exports.render},props));}};}
const settle=async()=>{for(let i=0;i<6;i++)await new Promise(r=>setImmediate(r));};
function runtime(){let pending=null,posted=0;return{customerAuth:{current:()=>({kind:'CUSTOMER',epoch:1,customerId:'101'})},customerAssistant:{latest:async()=>({result:null}),capability:async()=>({enabled:false,reason:'DISABLED',version:'1',factsVersion:'1'})},assistantSubmissions:{current:()=>pending,query:async()=>null,submit:async()=>{posted++},retry:async()=>null},assistantErrorText:()=> '暂未核实',posts:()=>posted,setPending:v=>pending=v};}
test('未启用时没有不可用AI按钮，原问题仍引导门店协助',async()=>{const r=runtime(),v=mount(r);await settle();const html=await v.render();assert.match(html,/AI参考暂未开启/);assert.doesNotMatch(html,/请AI查找参考/);assert.equal(r.posts(),0);v.scope.stop();});
test('启用也必须明确同意才发送，隐藏后旧请求不能刷新页面',async()=>{const r=runtime();r.customerAssistant.capability=async()=>({enabled:true,reason:'READY',version:'1',factsVersion:'1'});let resolve;r.assistantSubmissions.submit=()=>{r.setPending({phase:'SENT',command:{requestKey:key}});return new Promise(a=>resolve=a)};const v=mount(r);await settle();await v.state.submit();assert.equal(resolve,undefined);v.state.consent.value=true;const task=v.state.submit();await settle();v.props.active=false;await vue.nextTick();resolve({state:'HUMAN'});await task;assert.equal(v.events.length,0);v.scope.stop();});

test('引用实际渲染短摘录/出处与日期，不把旧农情参考继续展示',async()=>{const r=runtime(),v=mount(r);await settle();v.state.result.value={state:'REFERENCE',currentFactsVersion:'1',citations:[{title:'虚构观察资料',url:'https://www.moa.gov.cn/test-fixture',publishedOn:'2026-11-10',quote:'软件验收短摘录内容'}]};let html=await v.render();assert.match(html,/软件验收短摘录内容/);assert.match(html,/2026-11-10/);v.state.result.value.currentFactsVersion='2';html=await v.render();assert.doesNotMatch(html,/软件验收短摘录内容/);v.scope.stop();});

test('结束确认绑定原号，确认期间原稿变更不能结束另一请求',async()=>{
 const r=runtime(),original={phase:'SENT',command:{requestKey:key,expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}};r.setPending(original);let ended=0;r.assistantSubmissions.end=async()=>{ended++;return{state:'CANCELLED'}};
 const v=mount(r);await settle();v.state.beginEnd();assert.equal(v.state.endingConfirmation.value,true);r.setPending({...original,command:{...original.command,requestKey:'22222222-2222-4222-8222-222222222222'}});await v.state.confirmEnd();assert.equal(ended,0);v.scope.stop();
});
test('新设备从服务器发现原号并采用，只有读取原结果、不新建查询',async()=>{
 const r=runtime(),remote={requestKey:key,caseId:'7',state:'UNKNOWN',queuedVersion:'2',factsVersion:'1',currentFactsVersion:'1',handoffStatus:'',missingFacts:[],citations:[]};let adopted=0;
 r.customerAssistant.capability=async()=>({enabled:false,reason:'PENDING',version:'2',factsVersion:'1'});r.customerAssistant.latest=async()=>({result:remote});
 r.assistantSubmissions.adopt=async(id,value)=>{assert.equal(value.requestKey,key);adopted++;r.setPending({phase:'SENT',command:{requestKey:key}})};r.assistantSubmissions.query=async()=>remote;
 const v=mount(r,{version:'2'});await settle();assert.equal(adopted,1);assert.equal(v.state.result.value.state,'UNKNOWN');assert.equal(r.posts(),0);v.scope.stop();
});
test('结束已发送但失败时不再显示可撤回的确认框，只能核对结束',async()=>{
 const r=runtime(),original={phase:'SENT',command:{requestKey:key,expectedVersion:'1',factsVersion:'1',shareContextConfirmed:true}};r.setPending(original);
 r.assistantSubmissions.end=async()=>{r.setPending({...original,phase:'ENDING'});throw Error('NETWORK_FAILED')};const v=mount(r);await settle();v.state.beginEnd();await v.state.confirmEnd();
 assert.equal(v.state.endingConfirmation.value,false);assert.equal(v.state.pending.value.phase,'ENDING');const html=await v.render();assert.match(html,/重试结束确认/);assert.doesNotMatch(html,/继续等待<\/button>/);v.scope.stop();
});

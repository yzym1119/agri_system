const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');
const {parse,compileScript}=require('@vue/compiler-sfc');
function page(createRequestKey){
 const file=path.join(__dirname,'../src/pages/device-check/index.vue'),hooks={};
 const {descriptor}=parse(fs.readFileSync(file,'utf8'));
 const js=ts.transpileModule(compileScript(descriptor,{id:'device-check'}).content,{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS}}).outputText;
 const mod={exports:{}},imports={vue:require('vue'),'@dcloudio/uni-app':{onShow:f=>hooks.show=f,onHide:f=>hooks.hide=f,onUnload:f=>hooks.unload=f},'../../services/platform':{mobilePlatform:{createRequestKey}},'../../services/device-recorder':{getDeviceRecorder:()=>{throw Error('not used');}}};
 vm.runInNewContext(js,{module:mod,exports:mod.exports,Error,require:name=>{assert.ok(name in imports,name);return imports[name];},uni:{createInnerAudioContext:()=>({onError(){},stop(){},destroy(){}}),offNetworkStatusChange(){},onNetworkStatusChange(){},getNetworkType:o=>o.success({networkType:'wifi'})}},{filename:file});
 return {state:mod.exports.default.setup({}, {expose(){}}),hooks};
}
test('安全请求号失败应区分接口缺失、调用失败与数据异常，且不显示原始错误',async()=>{
 for(const [code,text] of [['RANDOM_UNAVAILABLE','未提供安全随机能力'],['RANDOM_FAILED','安全随机调用未完成'],['RANDOM_INVALID','安全随机返回数据异常'],['OTHER','检查未通过'],['toString','检查未通过'],['constructor','检查未通过'],['__proto__','检查未通过']]){
  const p=page(async()=>{throw Error(code)});p.hooks.show();await p.state.check('random');
  assert.match(p.state.message.value,new RegExp(text));assert.match(p.state.message.value,/暂不开放业务提交/);assert.ok(!p.state.message.value.includes(code));
 }
});
test('设备检查旧失败不能覆盖重新显示后的新视图',async()=>{
 let fail;const p=page(()=>new Promise((_,reject)=>fail=reject));p.hooks.show();const old=p.state.check('random');p.hooks.hide();p.hooks.show();fail(Error('RANDOM_UNAVAILABLE'));await old;assert.match(p.state.message.value,/页面已就绪/);
});

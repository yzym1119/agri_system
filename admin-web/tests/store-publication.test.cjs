const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),vue=require('vue');const {parse,compileScript}=require('@vue/compiler-sfc');
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}};
function setup(api){
 const file=path.join(__dirname,'../src/views/system/store/StorePublicationDialog.vue'),{descriptor}=parse(fs.readFileSync(file,'utf8')),compiled=compileScript(descriptor,{id:'pub'}),module={exports:{}};
 const user=vue.reactive({token:'original-token',userInfo:{id:'7'}}),permissions=vue.reactive({permissions:['store:view','store:publish']}),props=vue.reactive({modelValue:true,storeId:'1'});
 const imports={vue:{...vue,onUnmounted(){}},'@/store/user':{useUserStore:()=>user},'@/store/permission':{usePermissionStore:()=>permissions},'@/api/store-publication':api};
 vm.runInNewContext(ts.transpileModule(compiled.content,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{module,exports:module.exports,Error,require:key=>{assert.ok(key in imports,key);return imports[key]}});
 const state=module.exports.default.setup(props,{expose(){},emit(){}});return{state,props,user,permissions};
}
const preview=(id='1')=>({storeId:id,name:'门店',phone:'13800000000',address:'公开地址',visible:false,version:'1',fingerprint:'0'.repeat(64),canPublish:true});
test('门店/员工切换后旧预览不得覆盖新门店，API固定原会话',async()=>{
 const a=deferred(),b=deferred(),calls=[];const view=setup({getStorePublication:(id,token)=>{calls.push([id,token]);return calls.length===1?a.promise:b.promise},setStorePublication(){throw Error('no write')}});
 await vue.nextTick();view.props.storeId='2';view.user.token='new-token';await vue.nextTick();b.resolve(preview('2'));await new Promise(setImmediate);a.resolve(preview('1'));await new Promise(setImmediate);
 assert.equal(view.state.preview.value.storeId,'2');assert.equal(calls[0][1],'original-token');assert.equal(calls[1][1],'new-token');
});
test('明确确认前不发布，提交未知不重发，重新读取后才恢复操作',async()=>{
 let writes=0;const view=setup({getStorePublication:async()=>preview(),setStorePublication:async()=>{writes++;throw Error('lost')}});await new Promise(setImmediate);
 await view.state.submit();assert.equal(writes,0);view.state.confirmed.value=true;await view.state.submit();assert.equal(writes,1);assert.equal(view.state.uncertain.value,true);await view.state.submit();assert.equal(writes,1);await view.state.load();assert.equal(view.state.uncertain.value,false);assert.equal(view.state.confirmed.value,false);
});
test('缺任一权限不读取公开预览',async()=>{
 let reads=0;const view=setup({getStorePublication:async()=>{reads++;return preview()},setStorePublication(){}});await new Promise(setImmediate);const baseline=reads;view.permissions.permissions=['store:publish'];await vue.nextTick();await new Promise(setImmediate);assert.equal(reads,baseline);assert.equal(view.state.preview.value,null);
});

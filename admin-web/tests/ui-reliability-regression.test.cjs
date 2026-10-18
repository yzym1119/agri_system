const assert=require('node:assert/strict')
const test=require('node:test')
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),vue=require('vue')
function load(relative,context={}) {
 const file=fs.readFileSync(path.join(__dirname,'..',relative),'utf8')
 const source=relative.endsWith('.vue') ? file.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1] : file
 const ast=ts.createSourceFile(relative,source,ts.ScriptTarget.Latest,true)
 const names=ast.statements.filter(ts.isVariableStatement).flatMap(s=>Array.from(s.declarationList.declarations).filter(d=>ts.isIdentifier(d.name)).map(d=>d.name.text))
 const imports=Object.fromEntries(ast.statements.filter(ts.isImportDeclaration).flatMap(s=>[s.importClause?.name?.text,...(s.importClause?.namedBindings?.elements||[]).map(e=>e.name.text)].filter(Boolean).map(n=>[n,{}])))
 const unmounted=[],mounted=[]
 const scope={exports:{},...imports,...vue,onMounted:fn=>mounted.push(fn),onUnmounted:fn=>unmounted.push(fn),use(){},...context}
 vm.createContext(scope)
 const body=ast.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(ast)).join('\n')
 vm.runInContext(ts.transpileModule(body+`\nglobalThis.bindings={${names.join(',')}}`,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText,scope)
 return {...scope.bindings,exports:scope.exports,mounted,unmounted}
}
const tick=async()=>{await vue.nextTick();await new Promise(r=>setImmediate(r))}
function repayment(){
 const props=vue.reactive({modelValue:false,customerId:1,customerName:'客户甲'}),pending=[]
 const p=load('src/views/customer/list/components/RepaymentDialog.vue',{defineProps:()=>props,defineEmits:()=>()=>{},useUserStore:()=>({userInfo:{}}),crypto:require('node:crypto').webcrypto,getCreditStatistics:()=>new Promise((resolve,reject)=>pending.push({resolve,reject}))})
 const source=fs.readFileSync(path.join(__dirname,'../src/views/customer/list/components/RepaymentDialog.vue'),'utf8')
 const tag=source.match(/<el-input-number[\s\S]*?\/>/)[0],maxExpression=tag.match(/:max="([^"]+)"/)[1]
 const max=()=>vm.runInNewContext(maxExpression,Object.fromEntries(Object.entries(p).map(([k,v])=>[k,vue.unref(v)])))
 return {...p,props,pending,max}
}
test('首次还款等待欠款、零欠款及重开均保持输入上下限合法',async()=>{
 const p=repayment();assert.ok(p.max()>=.01,'初始max不能低于min导致弹层渲染异常')
 p.props.modelValue=true;await tick();assert.ok(p.max()>=.01)
 p.pending[0].resolve({totalDebt:0});await tick();assert.ok(p.max()>=.01)
 p.props.modelValue=false;await tick();p.props.modelValue=true;await tick();p.pending[1].resolve({totalDebt:20});await tick();assert.equal(p.max(),20)
})
test('还款切换客户后旧欠款响应不能污染当前客户',async()=>{
 const p=repayment();p.props.modelValue=true;await tick();p.props.customerId=2;await tick()
 p.pending[1].resolve({totalDebt:12});await tick();p.pending[0].resolve({totalDebt:99});await tick();assert.equal(p.currentStoreDebt.value,12)
})
const permissions=['report:dashboard','report:sales','report:finance:query','report:inventory','order:query','afterSales:query','purchase:query','credit:account:view']
function dashboard(owned=permissions){
 const permission=vue.reactive({permissions:owned}),user=vue.reactive({token:'a',userInfo:{id:1,extra:{storeId:1}}}),calls=[],pushes=[]
 const request=name=>()=>new Promise((resolve,reject)=>calls.push({name,resolve,reject}))
 const p=load('src/views/dashboard/index.vue',{usePermissionStore:()=>permission,useUserStore:()=>user,useRouter:()=>({push:v=>pushes.push(v)}),hasPermission:(r,o)=>o.includes(r),getDashboardToday:request('dashboard'),getCreditRisk:request('risk'),getInventoryValue:request('inventory'),getSalesTrend:request('trend'),getSlowMovingInventory:request('slow')})
 return {...p,permission,user,calls,pushes}
}
test('工作台首次失败显示未获取，成功的零值与失败区分',async()=>{
 const p=dashboard();const req=p.loadDashboard();p.calls.filter(r=>r.name==='dashboard').at(-1).reject(new Error('断网'));await req
 assert.equal(p.metrics.value.find(m=>m.title==='今日净销额').value,'—')
 const ok=p.loadDashboard();p.calls.filter(r=>r.name==='dashboard').at(-1).resolve({netSales:0});await ok
 assert.equal(p.metrics.value.find(m=>m.title==='今日净销额').value,'¥0.00')
})
test('刷新失败保留旧值并标注，旧请求不能覆盖新响应',async()=>{
 const p=dashboard(),a=p.loadDashboard(),b=p.loadDashboard(),req=p.calls.filter(r=>r.name==='dashboard')
 req.at(-1).resolve({netSales:20});await b;req.at(-2).resolve({netSales:99});await a
 assert.equal(p.dashboard.value.netSales,20)
 const retry=p.loadDashboard();p.calls.at(-1).reject(new Error('断网'));await retry
 assert.equal(p.dashboard.value.netSales,20);assert.ok(p.sectionState.dashboard.error);assert.ok(p.sectionState.dashboard.updatedAt)
})
test('工作台仅按真实报表权限请求，无额外报表权限不会取敏感统计',async()=>{
 const p=dashboard(['report:dashboard']);await tick();p.mounted.forEach(fn=>fn());await tick()
 assert.ok(p.calls.every(r=>r.name==='dashboard'));assert.ok(p.metrics.value.every(m=>m.section==='dashboard'))
 await p.loadCreditRisk();await p.loadInventoryValue();await p.loadSalesTrend();await p.loadSlowMoving();assert.ok(p.calls.every(r=>r.name==='dashboard'))
})
test('工作台任务分别直达配送、售后、采购并检查权限',()=>{
 const p=dashboard();const t=p.tasks.value
 assert.equal(t.find(t=>t.title==='待派送').route,'/order?deliveryStatus=2')
 assert.equal(t.find(t=>t.title==='售后处理').route,'/after-sales')
 assert.equal(t.find(t=>t.title==='采购收货').route,'/inventory/purchase/order?status=2')
 p.permission.permissions=['report:dashboard'];assert.equal(p.tasks.value.length,0)
 p.go('/credit','credit:account:view');assert.equal(p.pushes.length,0)
})
test('切换会话后旧工作台响应不回填',async()=>{
 const p=dashboard(),r=p.loadDashboard();const old=p.calls.filter(c=>c.name==='dashboard').at(-1)
 p.user.token='b';p.user.userInfo={id:2,extra:{storeId:2}};await tick();old.resolve({netSales:99});await r;assert.notEqual(p.dashboard.value.netSales,99)
})

test('工作台全部任务路径可由真实路由注册表解析',()=>{
 const p=dashboard(), registry=load('src/router/registry.ts'), valid=new Set()
 const visit=(routes,parent='')=>routes.forEach(r=>{const full=r.path.startsWith('/')?r.path:`${parent}/${r.path}`;if(r.component) valid.add(full);if(r.children)visit(r.children,full)})
 visit(registry.exports.buildAppChildRoutes(permissions))
 for(const task of p.tasks.value) assert.ok(valid.has(task.route.split('?')[0]),task.route)
})

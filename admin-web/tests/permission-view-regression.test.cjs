const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const {createPinia, setActivePinia, defineStore} = require('pinia')

function load(relative, context = {}) {
  const filename = path.join(__dirname, '..', relative)
  let source = fs.readFileSync(filename, 'utf8')
  if (relative.endsWith('.vue')) source = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  source = source.replace(/import\.meta\.env\.BASE_URL/g, "'/'")
  const ast = ts.createSourceFile(filename, source, ts.ScriptTarget.Latest, true)
  const names = ast.statements.filter(ts.isVariableStatement).flatMap(s => Array.from(s.declarationList.declarations).filter(d => ts.isIdentifier(d.name)).map(d => d.name.text))
  const imports = Object.fromEntries(ast.statements.filter(ts.isImportDeclaration).flatMap(s => {
    const clause = s.importClause
    return [clause?.name?.text, ...(clause?.namedBindings?.elements || []).map(e => e.name.text)].filter(Boolean).map(name => [name, {}])
  }))
  const mounted = [], unmounted = [], timers = []
  const scope = {exports:{}, ...imports, ...vue, defineStore,
    onMounted: fn => mounted.push(fn), onUnmounted: fn => unmounted.push(fn),
    setInterval: fn => {timers.push(fn);return timers.length}, clearInterval() {},
    localStorage:{}, document:{addEventListener(){},removeEventListener(){}},
    console:{error(){},warn(){},log(){}}, ...context}
  vm.createContext(scope)
  const script = ast.statements.filter(s => !ts.isImportDeclaration(s)).map(s => s.getFullText(ast)).join('\n')
  vm.runInContext(ts.transpileModule(script+`\nglobalThis.bindings={${names.join(',')}}`,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText,scope,{filename})
  return {...scope.bindings, exports:scope.exports, mounted, unmounted, timers}
}
const tick = async () => {await vue.nextTick();await new Promise(resolve => setImmediate(resolve))}
const hasPermission = (required, owned) => !required || (Array.isArray(required)?required:[required]).some(p=>owned.includes(p))

function layout(permissions) {
  const permission = vue.reactive({permissions, routes:[],menuTree:[]}), requests=[], pushes=[]
  const p=load('src/views/layout/index.vue',{
    usePermissionStore:()=>permission,useUserStore:()=>({userInfo:{name:'测试员工'}}),
    useTabsStore:()=>({addTab(){}}),useRoute:()=>({path:'/order'}),useRouter:()=>({push:path=>pushes.push(path)}),
    buildSidebarMenus:()=>[],hasPermission,
    getUnreadCount:async()=>{requests.push('unread');return 3}
  })
  return {...p,permission,requests,pushes}
}
test('无站内信权限的员工挂载和轮询均不请求未读数',async()=>{
  const p=layout([]);p.mounted.forEach(fn=>fn());p.timers.forEach(fn=>fn());await tick()
  assert.equal(p.requests.length,0)
  p.goNotification();assert.equal(p.pushes.length,0)
})
test('站内信授权可读，收紧权限后轮询停读并清除旧计数',async()=>{
  const p=layout(['notification:query']);p.mounted.forEach(fn=>fn());await tick()
  assert.equal(p.unreadCount.value,3)
  const n=p.requests.length;p.permission.permissions=[];await tick();p.timers.forEach(fn=>fn());await tick()
  assert.equal(p.requests.length,n);assert.equal(p.unreadCount.value,0)
})
function ship(permissions=['staff:list']) {
  const permission=vue.reactive({permissions}),props=vue.reactive({modelValue:false,mode:'local',order:{id:1}}),requests=[]
  const p=load('src/views/order/components/ShipDialog.vue',{
    defineProps:()=>props,defineEmits:()=>()=>{},usePermissionStore:()=>permission,hasPermission,
    getStaffList:()=>new Promise(resolve=>requests.push(resolve)),ElMessage:{error(){},success(){}}
  })
  return {...p,props,permission,requests}
}
test('订单页挂载不查询员工，仅打开有权限的同城派单才读取',async()=>{
  const p=ship();p.mounted.forEach(fn=>fn());await tick();assert.equal(p.requests.length,0)
  p.props.modelValue=true;await tick();assert.equal(p.requests.length,1)
})
test('快递发货或无员工读取权限不查询配送员',async()=>{
  for(const noAccess of [false,true]) {
    const p=ship(noAccess?[]:['staff:list']);p.props.mode=noAccess?'local':'express'
    p.mounted.forEach(fn=>fn());p.props.modelValue=true;await tick();assert.equal(p.requests.length,0)
  }
})
test('关闭派单或切换订单后旧配送员响应不能覆盖新弹窗',async()=>{
  const p=ship();p.props.modelValue=true;await tick()
  p.props.order={id:2};await tick()
  assert.equal(p.requests.length,2)
  p.requests[1]({records:[{id:9,name:'当前员工'}]});await tick()
  p.requests[0]({records:[{id:8,name:'旧员工'}]});await tick()
  assert.equal(p.deliveryStaffOptions.value[0].id,9)
  p.handleClose();await tick();assert.equal(p.deliveryStaffOptions.value.length,0)
})
test('权限指令随当前权限更新，空权限不回退旧用户资料',async()=>{
  const permission=vue.reactive({permissions:['staff:create']}),el={style:{display:''}},directives={}
  load('src/directives/permission.ts',{usePermissionStore:()=>permission,
    useUserStore:()=>({userInfo:{permissions:['staff:create']}}),hasPermission
  }).exports.registerPermissionDirective({directive:(name,def)=>{directives[name]=def}})
  const binding={value:'staff:create'};directives.permission.mounted(el,binding)
  assert.equal(el.style.display,'')
  permission.permissions=[];await tick();assert.equal(el.style.display,'none')
  permission.permissions=['staff:create'];await tick();assert.equal(el.style.display,'')
  directives.permission.unmounted?.(el)
})
test('旧登录信息响应不得覆盖新员工权限或清除新会话',async()=>{
  setActivePinia(createPinia())
  const pending=[],permission={setPermissions(p){this.permissions=p},permissions:[],routes:[],reset(){this.permissions=[]}}
  const p=load('src/store/user.ts',{usePermissionStore:()=>permission,resetDynamicRoutes(){},router:{push(){}},
    getStaffInfo:()=>new Promise((resolve,reject)=>pending.push({resolve,reject}))})
  const user=p.useUserStore();user.token='旧token';user.userInfo={id:1}
  const result=user.fetchUserInfo().catch(e=>e)
  user.token='新token';user.userInfo={id:2};permission.permissions=['order:query']
  pending[0].resolve({id:1,permissions:['staff:create']});await result
  assert.equal(user.userInfo.id,2);assert.deepEqual(permission.permissions,['order:query']);assert.equal(user.token,'新token')
})
test('旧会话菜单返回后不覆盖当前员工菜单',async()=>{
  setActivePinia(createPinia())
  let resolve,current=true
  const p=load('src/store/permission.ts',{getCurrentMenuTree:()=>new Promise(r=>{resolve=r})})
  const permission=p.usePermissionStore()
  const pending=permission.loadCurrentMenus(()=>current)
  current=false;permission.setMenuTree([{id:2,path:'/order'}])
  resolve([{id:1,path:'/staff'}]);await pending
  assert.equal(permission.menuTree[0].id,2)
})
test('已有用户资料及权限缓存时进入页面仍读取当前身份并更新动态路由',async()=>{
  let guard,reads=0;const removed=[]
  const permission={permissions:['staff:create'],menuTree:[],routes:[{name:'OldStaff'}],routesLoaded:true,
    setPermissions(p){this.permissions=p},setRoutes(r){this.routes=r},setRoutesLoaded(v){this.routesLoaded=v},
    async loadCurrentMenus(){this.menuTree=[{id:2,path:'/order'}]}}
  const user={token:'当前token',userInfo:{id:1,permissions:['staff:create']},
    async fetchUserInfo(){reads++;permission.permissions=['order:query'];this.userInfo={id:1,permissions:['order:query']}},reset(){}}
  load('src/router/index.ts',{useUserStore:()=>user,usePermissionStore:()=>permission,hasPermission,
    createWebHistory:()=>'',createRouter:()=>({beforeEach:fn=>{guard=fn},hasRoute:()=>true,removeRoute:name=>removed.push(name),addRoute(){}}),
    buildAppChildRoutes:()=>[{name:'Orders',path:'/order'}],findFirstAccessiblePath:()=>'/order',ElMessage:{error(){}}})
  const next=[];await guard({path:'/order',matched:[{}],meta:{}},{},v=>next.push(v))
  assert.equal(reads,1);assert.deepEqual(removed,['OldStaff']);assert.equal(permission.permissions[0],'order:query')
  assert.equal(next[0]?.replace,true)
})
test('当前身份读取失败已清令牌时跳转登录，而不是停留旧业务页',async()=>{
  setActivePinia(createPinia())
  let guard
  const permission={permissions:[],menuTree:[],routes:[],reset(){},setPermissions(){}}
  const user=load('src/store/user.ts',{usePermissionStore:()=>permission,resetDynamicRoutes(){},router:{push(){}},
    getStaffInfo:async()=>{throw Error('网络不可用')}}).useUserStore()
  user.token='当前token';user.userInfo={id:1,permissions:['order:query']}
  load('src/router/index.ts',{useUserStore:()=>user,usePermissionStore:()=>permission,hasPermission,
    createWebHistory:()=>'',createRouter:()=>({beforeEach:fn=>{guard=fn},hasRoute:()=>false,removeRoute(){},addRoute(){}}),
    ElMessage:{error(){}}})
  const next=[];await guard({path:'/order',matched:[{}],meta:{}},{},v=>next.push(v))
  assert.equal(user.token,'');assert.deepEqual(next,['/login'])
})
test('默认落点在可访问菜单中优先工作台/收银/提货，不扩大权限',()=>{
  const p=load('src/utils/permission.ts').exports
  const routes=[{path:'/customer',component:{},meta:{permissions:['customer:query']}},{path:'/pos',component:{},meta:{permissions:['pos:order:create']}},{path:'/dashboard',component:{},meta:{permissions:['report:dashboard']}}]
  assert.equal(p.findFirstAccessiblePath(routes,['customer:query','pos:order:create']),'/pos')
  assert.equal(p.findFirstAccessiblePath(routes,['customer:query']),'/customer')
  assert.equal(p.findFirstAccessiblePath(routes,['report:dashboard','pos:order:create']),'/dashboard')
})
test('主导航默认只展开当前页面所属分组',()=>{
  const p=load('src/views/layout/index.vue',{
    usePermissionStore:()=>({permissions:[],routes:[{}],menuTree:[]}),useUserStore:()=>({userInfo:{name:'员工'}}),useTabsStore:()=>({tabs:[],addTab(){}}),useRoute:()=>({path:'/order'}),useRouter:()=>({}),hasPermission,
    buildSidebarMenus:()=>[{key:'/sales',children:[{path:'/order'}]},{key:'/stock',children:[{path:'/inventory'}]}]
  })
  assert.deepEqual(Array.from(p.defaultOpeneds.value),['/sales'])
})

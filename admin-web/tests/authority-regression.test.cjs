const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const ts = require('typescript')
const vue = require('vue')
const {parse} = require('@vue/compiler-sfc')
const {compile} = require('@vue/compiler-dom')

for (const granted of [[1, 11], [1]]) test(`权限树回显并原样保存不扩权 ${granted.join(',')}`, async () => {
  const {default: TreeStore} = await import('../node_modules/element-plus/es/components/tree/src/model/tree-store.mjs')
  const p = page('role', ['role:assign'])
  const props = nodes(p, 'el-tree')[0].props
  const tree = new TreeStore({key:'id', props:{children:'children',label:'name'},
    checkStrictly: props['check-strictly'] ?? props.checkStrictly ?? false,
    data:[{id:1,name:'商品',children:[{id:11,name:'查询'},{id:12,name:'新增'}]}]})
  tree.initialize()
  tree.setCheckedKeys(granted, false)
  assert.equal(tree.getNode(12).checked, false, '父菜单回显不能自动勾选未授予的新增操作')
  p.currentRoleId.value = 8
  p.menuTreeRef.value = tree
  await p.handleMenuSubmit()
  assert.deepEqual(Array.from(p.calls[0].ids).sort((a,b)=>a-b), granted)
})

// 执行真实页面 setup 和模板；仅网络、组件宿主与提示为边界替身。
function page(kind, permissions = [], overrides = {}) {
  const filename = path.join(__dirname, '..', `src/views/system/${kind}/index.vue`)
  const source = fs.readFileSync(filename, 'utf8')
  const script = parse(source).descriptor.scriptSetup.content
  const ast = ts.createSourceFile(filename, script, ts.ScriptTarget.Latest, true)
  const code = ast.statements.filter(s => !ts.isImportDeclaration(s)).map(s => s.getFullText(ast)).join('\n')
  const names = ast.statements.filter(ts.isVariableStatement).flatMap(s => Array.from(s.declarationList.declarations).filter(d => ts.isIdentifier(d.name)).map(d => d.name.text))
  const calls = [], messages = [], logs = [], permissionStore = vue.reactive({permissions})
  const context = {
    ref: vue.ref, reactive: vue.reactive, computed: vue.computed, onMounted() {}, nextTick: vue.nextTick,
    usePermissionStore: () => permissionStore,
    getRoleList: async () => [{id:2,name:'收银',status:1},{id:3,name:'停用角色',status:0},{id:4,name:'库管',status:1}],
    getAllStores: async () => [], getStaffList: async () => ({records:[],total:0}),
    getStaffProfile: async id => ({id,name:'员工',phone:'13800000000',storeId:1,roleIds:[2,4]}),
    createStaff: async body => calls.push({method:'createStaff',body}),
    updateStaff: async (id,body) => calls.push({method:'updateStaff',id,body}),
    createRole: async body => calls.push({method:'createRole',body}),
    updateRole: async (id,body) => calls.push({method:'updateRole',id,body}),
    assignRoleMenus: async (id,ids) => calls.push({method:'assignRoleMenus',id,ids}),
    ElMessage: Object.fromEntries(['success','error','warning','info'].map(level => [level,text => messages.push({level,text})])),
    console: {error:(...args)=>logs.push(args)}, ...overrides
  }
  vm.createContext(context)
  vm.runInContext(ts.transpileModule(code + `\nglobalThis.bindings={${names.join(',')}}`, {compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText, context)
  const bindings = context.bindings
  bindings.formRef.value = {validate:async callback => callback(true),resetFields() {}}
  return {...bindings,bindings,calls,messages,logs,permissionStore,source}
}
function nodes(page, type) {
  const render = new Function('Vue',compile(parse(page.source).descriptor.template.content,{mode:'function'}).code)({...vue,resolveComponent:name=>({name}),resolveDirective:()=>({})})
  const found = [], oldWarn = console.warn
  console.warn = () => {}
  try {
    const walk = node => {
      if(!node) return
      if(Array.isArray(node)) {node.forEach(walk);return}
      if(node.type?.name === type) found.push(node)
      if(Array.isArray(node.children)) node.children.forEach(walk)
      else if(node.children && typeof node.children === 'object') for(const slot of Object.values(node.children)) if(typeof slot === 'function') walk(slot({row:{id:1,status:1,roles:[]}}))
    }
    walk(render(vue.proxyRefs({Plus:{},Edit:{},Setting:{},Delete:{},Search:{},Refresh:{},Lock:{},Unlock:{},Key:{},...page.bindings}),[]))
  } finally {console.warn=oldWarn}
  return found
}

for(const editing of [false,true]) test(`角色${editing?'编辑':'创建'}仅发送名称和状态，不夹带编码、身份或菜单`,async()=>{
  const p=page('role',['role:save'])
  if(editing) p.handleEdit({id:8,name:'原角色',status:1});else p.handleAdd()
  Object.assign(p.formData,{name:'新名称',status:0,menuIds:[99],code:'ADMIN'})
  await p.handleSubmit()
  assert.deepEqual(Object.keys(p.calls[0].body).sort(),['name','status'])
  assert.equal(p.calls[0].body.name,'新名称');assert.equal(p.calls[0].body.status,0)
  if(editing) assert.equal(p.calls[0].id,8)
})

test('角色权限继续由独立授权入口提交，包含半选父菜单',async()=>{
  const p=page('role',['role:assign']);p.currentRoleId.value=8
  p.menuTreeRef.value={getCheckedKeys:()=>[11],getHalfCheckedKeys:()=>[1]}
  await p.handleMenuSubmit();assert.equal(p.calls[0].method,'assignRoleMenus')
  assert.equal(p.calls[0].id,8);assert.deepEqual(Array.from(p.calls[0].ids),[11,1])
})

for(const permissions of [['staff:update'],['staff:update','role:assign']]) test(`员工资料编辑${permissions.includes('role:assign')?'有':'无'}授权权，未变角色均省略roleId以保留多角色`,async()=>{
  const p=page('staff',permissions);await p.handleEdit({id:8,roles:[{id:2,name:'收银'},{id:4,name:'库管'}]})
  p.formData.name='改姓名';await p.handleSubmit()
  assert.equal(p.calls.length,1);assert.equal(p.calls[0].id,8)
  assert.equal(p.calls[0].body.name,'改姓名');assert.equal(Object.hasOwn(p.calls[0].body,'roleId'),false)
  assert.equal(Object.hasOwn(p.calls[0].body,'status'),false)
})

test('普通资料编辑不加载受授权保护的角色列表，也不显示角色选择',async()=>{
  let roleQueries=0
  const p=page('staff',['staff:update'],{getRoleList:async()=>{roleQueries++;throw Error('无role:query')}})
  await p.handleEdit({id:8,roles:[{id:2,name:'收银'}]})
  assert.equal(roleQueries,0);assert.equal(p.dialogVisible.value,true)
  assert.equal(nodes(p,'el-form-item').some(node=>node.props?.prop==='roleId'),false)
})

test('有授权权才在真正换成新角色时提交roleId，选原多角色之一不缩减关联',async()=>{
  const p=page('staff',['staff:update','role:assign'])
  await p.handleEdit({id:8});p.formData.roleId=4;await p.handleSubmit()
  assert.equal(Object.hasOwn(p.calls[0].body,'roleId'),false)
  await p.handleEdit({id:8});p.roleList.value.push({id:5,name:'采购',status:1});p.formData.roleId=5
  await p.handleSubmit();assert.equal(p.calls[1].body.roleId,5)
})

test('无授权权即使表单角色被改动，普通资料更新也不夹带roleId',async()=>{
  const p=page('staff',['staff:update']);await p.handleEdit({id:8});p.formData.roleId=99
  await p.handleSubmit();assert.equal(Object.hasOwn(p.calls[0].body,'roleId'),false)
})

for(const permissions of [['staff:create'],['role:assign']]) test(`仅${permissions[0]}不展示或打开永远无权提交的新增员工入口`,async()=>{
  const p=page('staff',permissions);await p.handleAdd()
  assert.equal(p.dialogVisible.value,false)
  assert.equal(nodes(p,'el-button').some(node=>node.props.onClick===p.handleAdd),false)
  Object.assign(p.formData,{name:'员工',phone:'13800000000',password:'local-only',storeId:1,roleId:2})
  await p.handleSubmit();assert.equal(p.calls.length,0)
})

test('创建员工具备两项权限，仍携一个有效角色和原门店密码信息',async()=>{
  const p=page('staff',['staff:create','role:assign']);await p.handleAdd()
  Object.assign(p.formData,{name:'员工',phone:'13800000000',password:'local-only',storeId:1,roleId:2})
  await p.handleSubmit();assert.equal(p.calls[0].method,'createStaff')
  assert.equal(p.calls[0].body.roleId,2);assert.equal(p.calls[0].body.storeId,1)
})

for(const editing of [false,true]) test(`员工${editing?'改角色':'创建'}不能把停用角色作为有效新角色`,async()=>{
  const p=page('staff',['staff:create','staff:update','role:assign'])
  if(editing) await p.handleEdit({id:8});else await p.handleAdd()
  Object.assign(p.formData,{name:'员工',phone:'13800000000',password:'local-only',storeId:1,roleId:3})
  assert.equal(nodes(p,'el-option').find(node=>node.props.label==='停用角色')?.props.disabled,true)
  await p.handleSubmit();assert.equal(p.calls.length,0)
})

function requestHarness() {
  const filename=path.join(__dirname,'..','src/utils/request.ts')
  const source=fs.readFileSync(filename,'utf8'), ast=ts.createSourceFile(filename,source,ts.ScriptTarget.Latest,true)
  const body=ast.statements.filter(s=>!ts.isImportDeclaration(s)&&!ts.isExportAssignment(s)).map(s=>s.getFullText(ast)).join('\n').replaceAll('import.meta.env','testEnv')
  const interceptors={},logs=[],messages=[],routes=[],store={token:'sensitive-token',resetCalls:0,logoutCalls:0,reset(){this.resetCalls++;this.token=''},logout(){this.logoutCalls++}}
  const context={testEnv:{DEV:true},axios:{create:()=>({interceptors:{request:{use:(ok,fail)=>{interceptors.request={ok,fail}}},response:{use:(ok,fail)=>{interceptors.response={ok,fail}}}}})},useUserStore:()=>store,router:{push:route=>routes.push(route)},ElMessage:{error:message=>messages.push(message)},console:{log:(...args)=>logs.push(args),error:(...args)=>logs.push(args)}}
  vm.createContext(context);vm.runInContext(ts.transpileModule(body,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText,context)
  return {...interceptors,logs,messages,routes,store}
}

test('登录、钱包请求与响应日志不泄露密码、token、验证码及成功响应正文',()=>{
  const p=requestHarness()
  p.request.ok({method:'post',url:'/api/auth/staff/login',headers:{},data:{password:'sensitive-password',walletAuthCode:'sensitive-wallet'},params:{token:'sensitive-query'}})
  p.response.ok({status:200,config:{url:'/api/auth/staff/login'},data:{tokenValue:'sensitive-token',userInfo:{}}})
  const output=JSON.stringify(p.logs)
  for(const secret of ['sensitive-password','sensitive-wallet','sensitive-token','sensitive-query']) assert.equal(output.includes(secret),false)
})

test('网络和HTTP异常日志不输出Axios原始对象，拒绝仍保留原错误用于退款未知结果冻结',async()=>{
  const p=requestHarness()
  for(const response of [undefined,{status:400,data:{message:'参数有误',code:10001,password:'sensitive-body'}}]) {
    const error=Object.assign(Error('network failed'),{config:{url:'/api/auth/staff/login',method:'post',headers:{Authorization:'sensitive-token'},data:{password:'sensitive-password'}},request:{},response})
    await assert.rejects(p.response.fail(error),got=>got===error)
    await assert.rejects(p.request.fail(error),got=>got===error)
  }
  const output=JSON.stringify(p.logs)
  for(const secret of ['sensitive-password','sensitive-token','sensitive-body']) assert.equal(output.includes(secret),false)
})

for(const code of [401,10003,'HTTP401']) test(`旧会话${code}晚到不清除新员工登录或跳转登录页`,async()=>{
  const p=requestHarness()
  const config=p.request.ok({method:'get',url:'/auth/staff/info',headers:{}})
  p.store.token='new-session-token'
  if(code==='HTTP401') await assert.rejects(p.response.fail({response:{status:401},config}))
  else await assert.rejects(Promise.resolve().then(()=>p.response.ok({status:200,config,data:{code,message:'旧会话失效'}})))
  assert.equal(p.store.token,'new-session-token');assert.equal(p.store.resetCalls,0)
  assert.equal(p.routes.length,0);assert.equal(p.messages.length,0)
})

for(const code of [401,'401',10003,'10003']) test(`fresh身份失效业务码${JSON.stringify(code)}本地清会话，不发递归退出请求`,async()=>{
  const p=requestHarness()
  const config=p.request.ok({method:'get',url:'/api/admin/staff',headers:{}})
  await assert.rejects(Promise.resolve().then(()=>p.response.ok({status:200,config,data:{code,message:'登录失效'}})))
  assert.equal(p.store.resetCalls,1);assert.equal(p.store.logoutCalls,0);assert.deepEqual(p.routes,['/login'])
})

test('HTTP401本地退出；权限拒绝保留业务码和当前会话，不创建新权限缓存',async()=>{
  const p=requestHarness()
  const config=p.request.ok({method:'get',url:'/api/admin/staff',headers:{}})
  await assert.rejects(p.response.fail({response:{status:401},config}))
  assert.equal(p.store.resetCalls,1);assert.equal(p.store.logoutCalls,0)
  await assert.rejects(Promise.resolve().then(()=>p.response.ok({status:200,config:{url:'/api/admin/staff'},data:{code:10004,message:'无角色授予权限'}})),error=>error.apiBusinessCode===10004)
  assert.equal(p.store.resetCalls,1);assert.ok(p.messages.includes('无角色授予权限'))
})

// 断点可控地延迟生产校验回调与网络响应，覆盖同一弹窗的对象/字段切换。
const tick = () => new Promise(resolve => setImmediate(resolve))
async function editable(kind, overrides = {}) {
  const p=page(kind,['staff:create','staff:update','role:assign','role:save'],overrides)
  await p.handleEdit({id:8,name:'原名称',status:1,roles:[{id:2,name:'收银'}]})
  return p
}
function deferValidation(p) {
  let finish
  p.formRef.value={validate:callback=>new Promise(resolve=>{finish=async()=>{await callback(true);resolve(true)}}),resetFields(){}}
  return ()=>finish()
}

for(const kind of ['staff','role']) {
  for(const change of ['换对象','只关窗','改姓名','改角色或状态','新增转编辑']) test(`${kind}延迟校验期间${change}不得提交未经本次确认的新命令`,async()=>{
    const p=await editable(kind)
    if(change==='新增转编辑') {await p.handleAdd();Object.assign(p.formData,{name:'新增确认',phone:'13800000000',password:'确认密码',storeId:1,roleId:2})}
    const finish=deferValidation(p), pending=p.handleSubmit()
    if(change==='只关窗') {p.dialogVisible.value=false;p.handleDialogClose()}
    else if(change==='换对象'||change==='新增转编辑') {p.dialogVisible.value=false;p.handleDialogClose();await p.handleEdit({id:9,name:'另一角色',status:1})}
    else if(change==='改姓名') p.formData.name='尚未确认的新姓名'
    else if(kind==='staff') {p.roleList.value.push({id:5,name:'新角色',status:1});p.formData.roleId=5}
    else p.formData.status=0
    await finish();await pending
    assert.equal(p.calls.length,0)
  })

  test(`${kind}点击即锁定提交，重复点击只校验和提交一次`,async()=>{
    const p=await editable(kind);const finish=deferValidation(p)
    const pending=p.handleSubmit();assert.equal(p.submitLoading.value,true)
    await p.handleSubmit();await finish();await pending
    assert.equal(p.calls.length,1);assert.equal(p.calls[0].id,8)
  })

  for(const result of ['成功','失败']) test(`${kind}旧HTTP${result}不关闭新对象窗口、不清新请求loading或写旧提示`,async()=>{
    const requests=[]
    const p=await editable(kind,{[kind==='staff'?'updateStaff':'updateRole']:(id,body)=>new Promise((resolve,reject)=>requests.push({id,body,resolve,reject}))})
    const old=p.handleSubmit();await tick();assert.equal(requests.length,1)
    p.dialogVisible.value=false;p.handleDialogClose();await p.handleEdit({id:9,name:'新对象',status:1})
    p.formData.name='新对象确认';const latest=p.handleSubmit();await tick();assert.equal(requests.length,2)
    const messagesBefore=p.messages.length
    if(result==='成功') requests[0].resolve();else requests[0].reject(Object.assign(Error('旧请求失败'),{config:{headers:{Authorization:'sensitive-token'}}}))
    await old
    assert.equal(p.dialogVisible.value,true);assert.equal(p.formData.id,9);assert.equal(p.formData.name,'新对象确认')
    assert.equal(p.submitLoading.value,true);assert.equal(p.messages.length,messagesBefore)
    requests[1].resolve();await latest;assert.equal(p.submitLoading.value,false)
  })

  test(`${kind}创建失败页面catch不打印原Axios密码/token，且不修改原错误对象`,async()=>{
    const error=Object.assign(Error('网络失败'),{config:{method:'post',url:'/api/admin/'+kind,headers:{Authorization:'sensitive-token'},data:{password:'sensitive-password'}},response:{status:400,data:{password:'sensitive-response'}}})
    const p=page(kind,['staff:create','role:assign','role:save'],{[kind==='staff'?'createStaff':'createRole']:async()=>{throw error}})
    await p.handleAdd();Object.assign(p.formData,{name:'新增',phone:'13800000000',password:'sensitive-password',storeId:1,roleId:2})
    await p.handleSubmit();assert.ok(p.logs.length)
    const output=JSON.stringify(p.logs)
    for(const secret of ['sensitive-token','sensitive-password','sensitive-response']) assert.equal(output.includes(secret),false)
    assert.equal(error.config.headers.Authorization,'sensitive-token');assert.equal(error.config.data.password,'sensitive-password')
  })
}

test('员工延迟校验期间原角色关联基线改变同样放弃旧提交',async()=>{
  const p=await editable('staff'),finish=deferValidation(p),pending=p.handleSubmit()
  assert.ok(p.originalRoleIds);p.originalRoleIds.value.push(7)
  await finish();await pending;assert.equal(p.calls.length,0)
})

test('员工密码重置失败页面catch不输出新密码及认证头',async()=>{
  const error={config:{headers:{Authorization:'sensitive-token'},data:{newPassword:'sensitive-password'}}}
  const p=page('staff',['staff:resetPassword'],{resetStaffPassword:async()=>{throw error}})
  p.handleResetPassword({id:8});p.passwordForm.newPassword='sensitive-password';p.passwordForm.confirmPassword='sensitive-password'
  p.passwordFormRef.value={validate:async callback=>callback(true),resetFields(){}}
  await p.handlePasswordSubmit()
  assert.equal(JSON.stringify(p.logs).includes('sensitive-token'),false);assert.equal(JSON.stringify(p.logs).includes('sensitive-password'),false)
})

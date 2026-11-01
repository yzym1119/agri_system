const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const ts = require('typescript')
const vue = require('vue')

// 运行生产 Vue setup，网络/组件宿主是边界替身，履约与收银逻辑保持真实。
function loadPage(relativePath, exports, overrides = {}) {
  const filename = path.join(__dirname, '..', relativePath)
  const script = fs.readFileSync(filename, 'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  const source = ts.createSourceFile(filename + '.ts', script, ts.ScriptTarget.Latest, true)
  const code = source.statements.filter(s => !ts.isImportDeclaration(s)).map(s => s.getFullText(source)).join('\n')
  const messages = []
  const context = {
    ref: vue.ref, reactive: vue.reactive, computed: vue.computed, watch: vue.watch,
    onMounted() {}, onUnmounted() {}, nextTick: vue.nextTick,
    defineProps: () => ({}), defineEmits: () => () => {}, defineExpose() {},
    useUserStore: () => ({ userInfo: { id: 8, extra: { storeId: 2 } } }),
    usePermissionStore: () => ({ permissions: ['*'] }),
    getOrderPaymentSummary: () => ({ netReceivable: 100 }), getAllStores: async () => [],
    ElMessage: Object.fromEntries(['error', 'warning', 'success', 'info'].map(level => [level, value => messages.push({level, text: typeof value === 'string' ? value : value?.message})])),
    console: { error() {}, warn() {} }, ...overrides
  }
  vm.createContext(context)
  vm.runInContext(ts.transpileModule(code + `\nglobalThis.page = {${exports.join(',')}};`, {
    compilerOptions: {target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS}
  }).outputText, context, {filename})
  return {...context.page, messages}
}

function pickup(overrides = {}) {
  const page = loadPage('src/views/inventory/pickup/index.vue', ['openFulfillDialog','currentItem','confirmedAllocationIds','scanCode','canSubmit','submitFulfill','fulfillVisible','fulfilling',
    'storeId','statusFilter','workItems','loading','locations','loadWorkItems','loadLocations','handleStoreChange',
    'openCreateDialog','createVisible','creating','createForm','canCreate','submitCreate'], {getInventoryLocations:async()=>[],getPickupWorkItems:async()=>[],...overrides})
  page.storeId.value=1
  return page
}
function openPickup(page,row) {
  page.workItems.value=[row]
  page.openFulfillDialog(page.workItems.value[0])
}
const partiallyReturned = () => ({ id: 601, status: 'PENDING', canFulfill: true,
  confirmationKey: 'a'.repeat(64),
  requestedBaseQuantity: 2, remainingBaseQuantity: 1, allocations: [
    {id: 1, batchId: 10, allocatedBaseQuantity: 1, remainingBaseQuantity: 0},
    {id: 2, batchId: 11, allocatedBaseQuantity: 1, remainingBaseQuantity: 1}
  ]})

test('部分退货后只核对尚未退回的批次并提交该分配', async () => {
  const calls=[]
  const page=pickup({fulfillPickupRequest:async (id,body)=>calls.push({id,body})})
  openPickup(page,partiallyReturned());page.confirmedAllocationIds.value=[2]
  assert.equal(page.canSubmit.value,true)
  await page.submitFulfill()
  assert.equal(calls.length,1);assert.equal(calls[0].id,601)
  assert.deepEqual(Array.from(calls[0].body.allocationIds),[2])
  assert.equal(calls[0].body.confirmationKey, 'a'.repeat(64))
})

test('缺失或损坏的数量核对摘要不能打开确认或提交',async()=>{
  for(const confirmationKey of [undefined,'','a'.repeat(63),'A'.repeat(64)]) {
    const calls=[],page=pickup({fulfillPickupRequest:async (...args)=>calls.push(args)})
    openPickup(page,{...partiallyReturned(),confirmationKey});page.confirmedAllocationIds.value=[2]
    assert.equal(page.fulfillVisible.value,false)
    assert.equal(page.canSubmit.value,false);await page.submitFulfill();assert.equal(calls.length,0)
  }
})

test('后端缺失或拒绝提货能力时不能打开确认或提交',async()=>{
  for(const canFulfill of [false,undefined]) {
    const calls=[], page=pickup({fulfillPickupRequest:async (...args)=>calls.push(args)})
    openPickup(page,{...partiallyReturned(),canFulfill})
    page.confirmedAllocationIds.value=[1,2];page.scanCode.value='实际标签'
    assert.equal(page.fulfillVisible.value,false)
    assert.equal(page.canSubmit.value,false)
    await page.submitFulfill();assert.equal(calls.length,0)
  }
})

test('全退零剩余不能提交，单批剩余扫码不要求已全退批次',async()=>{
  const calls=[],page=pickup({fulfillPickupRequest:async (id,body)=>calls.push({id,body})})
  openPickup(page,partiallyReturned());page.scanCode.value='晚批标签'
  assert.equal(page.canSubmit.value,true);await page.submitFulfill()
  assert.equal(calls[0].body.scannedCode,'晚批标签')
  openPickup(page,{...partiallyReturned(),canFulfill:false,remainingBaseQuantity:0})
  assert.equal(page.canSubmit.value,false)
})

test('提货确认连点只发送一次，旧响应不关闭新请求',async()=>{
  const calls=[],page=pickup({fulfillPickupRequest:(id,body)=>new Promise(resolve=>calls.push({id,body,resolve}))})
  openPickup(page,partiallyReturned());page.confirmedAllocationIds.value=[2]
  const first=page.submitFulfill(),duplicate=page.submitFulfill()
  assert.equal(calls.length,1)
  openPickup(page,{...partiallyReturned(),id:602})
  calls[0].resolve();await first;await duplicate
  assert.equal(page.currentItem.value.id,602);assert.equal(page.fulfillVisible.value,true)
})

const { renderToString } = require('@vue/server-renderer')
async function renderAllocationColumn(row) {
  const source=fs.readFileSync(path.join(__dirname,'../src/views/inventory/pickup/index.vue'),'utf8')
  const column=source.match(/<el-table-column label="已出库批次"[\s\S]*?<template #default="\{ row \}">([\s\S]*?)<\/template>/)[1]
  return renderToString(vue.createSSRApp({setup:()=>({row}),render:vue.compile(column)}))
}
test('真实批次模板始终展示原出库，只有待取货展示当前可交，不改写已完成历史',async()=>{
  const pending=await renderAllocationColumn({status:'PENDING',allocations:[{id:1,batchId:10,locationId:12,allocatedBaseQuantity:2,remainingBaseQuantity:1}]})
  assert.match(pending,/原出库 2（基准）/)
  assert.match(pending,/本次可交 1/)
  const fulfilled=await renderAllocationColumn({status:'FULFILLED',allocations:[{id:1,batchId:10,locationId:12,allocatedBaseQuantity:2,remainingBaseQuantity:null}]})
  assert.match(fulfilled,/原出库 2（基准）/)
  assert.doesNotMatch(fulfilled,/本次可交|本次交付|待核对|null/)
})

test('提货失败日志不输出原始异常请求、headers或响应正文',async()=>{
  const logs=[],page=pickup({console:{warn:(...args)=>logs.push(args)},fulfillPickupRequest:async()=>{throw {config:{headers:{Authorization:'密钥'}},response:{data:{customer:'客户明细'}}}}})
  openPickup(page,partiallyReturned());page.confirmedAllocationIds.value=[2]
  await page.submitFulfill()
  assert.ok(logs.length>0)
  assert.equal(logs.flat().some(value=>value&&typeof value==='object'),false)
  assert.doesNotMatch(JSON.stringify(logs),/密钥|客户明细|headers|Authorization/)
})

const flush = () => new Promise(resolve=>setImmediate(resolve))
function pendingCall(list, args) {return new Promise((resolve,reject)=>list.push({...args,resolve,reject}))}

test('快速切门店立即清旧行和弹窗，旧列表货位成功及finally不得覆盖新查询',async()=>{
  const lists=[],locations=[],calls=[],page=pickup({
    getPickupWorkItems:(store,status)=>pendingCall(lists,{store,status}),
    getInventoryLocations:store=>pendingCall(locations,{store}),
    fulfillPickupRequest:async id=>calls.push(id)
  })
  const old=partiallyReturned();openPickup(page,old);page.openCreateDialog()
  page.createForm.value={orderId:50,orderItemId:90};page.locations.value=[{id:11}]
  const first=page.loadWorkItems();const firstLocations=page.loadLocations()
  page.storeId.value=2;page.handleStoreChange()
  assert.equal(page.workItems.value.length,0);assert.equal(page.locations.value.length,0)
  assert.equal(page.fulfillVisible.value,false);assert.equal(page.currentItem.value,null)
  assert.equal(page.createVisible.value,false);assert.equal(page.createForm.value.orderId,undefined)
  lists[0].resolve([old]);locations[0].resolve([{id:11}]);await first;await firstLocations
  assert.equal(page.workItems.value.length,0);assert.equal(page.locations.value.length,0)
  assert.equal(page.loading.value,true,'旧查询finally不能提前结束新门店loading')
  page.openFulfillDialog(old);page.confirmedAllocationIds.value=[2];await page.submitFulfill()
  assert.equal(calls.length,0,'旧门店行即使保留引用也不能开单确认')
  lists[1].resolve([{...partiallyReturned(),id:602}]);locations[1].resolve([{id:22}]);await flush()
  assert.deepEqual(Array.from(page.workItems.value,item=>item.id),[602]);assert.deepEqual(Array.from(page.locations.value,item=>item.id),[22])
  assert.equal(page.loading.value,false)
})

test('新门店成功后旧列表及货位失败不得清空新结果，清除门店也不能被旧响应复活',async()=>{
  const lists=[],locations=[],page=pickup({getPickupWorkItems:(store,status)=>pendingCall(lists,{store,status}),getInventoryLocations:store=>pendingCall(locations,{store})})
  const old=page.loadWorkItems(),oldLocation=page.loadLocations()
  page.storeId.value=2;page.handleStoreChange()
  lists[1].resolve([{...partiallyReturned(),id:602}]);locations[1].resolve([{id:22}]);await flush()
  lists[0].reject(Error('旧列表'));locations[0].reject(Error('旧货位'));await old;await oldLocation
  assert.deepEqual(Array.from(page.workItems.value,item=>item.id),[602]);assert.deepEqual(Array.from(page.locations.value,item=>item.id),[22])
  page.handleStoreChange();page.storeId.value=undefined;page.handleStoreChange()
  lists[2].resolve([partiallyReturned()]);locations[2].resolve([{id:99}]);await flush()
  assert.equal(page.workItems.value.length,0);assert.equal(page.locations.value.length,0);assert.equal(page.loading.value,false)
})

test('切取货状态同样清旧行和两个弹窗，旧待取成功不能污染已取货或loading',async()=>{
  const lists=[],page=pickup({getPickupWorkItems:(store,status)=>pendingCall(lists,{store,status})})
  openPickup(page,partiallyReturned());page.openCreateDialog();const old=page.loadWorkItems()
  page.statusFilter.value='FULFILLED';page.handleStoreChange()
  assert.equal(page.workItems.value.length,0);assert.equal(page.fulfillVisible.value,false);assert.equal(page.createVisible.value,false)
  lists[0].resolve([partiallyReturned()]);await old
  assert.equal(page.workItems.value.length,0);assert.equal(page.loading.value,true)
  lists[1].resolve([{...partiallyReturned(),id:602,status:'FULFILLED'}]);await flush()
  assert.equal(page.workItems.value[0].status,'FULFILLED');assert.equal(page.loading.value,false)
})

test('旧提货成功或失败不关闭新门店弹窗、不刷新新列表且finally不解锁新提交',async()=>{
  for(const result of ['resolve','reject']) {
    const calls=[],lists=[],page=pickup({fulfillPickupRequest:(id,body)=>pendingCall(calls,{id,body}),getPickupWorkItems:async(store,status)=>{lists.push({store,status});return [{...partiallyReturned(),id:602}]}})
    openPickup(page,partiallyReturned());page.confirmedAllocationIds.value=[2]
    const old=page.submitFulfill();await page.submitFulfill();assert.equal(calls.length,1)
    page.storeId.value=2;page.handleStoreChange();await flush()
    page.openFulfillDialog(page.workItems.value[0]);page.confirmedAllocationIds.value=[2]
    const latest=page.submitFulfill();assert.equal(calls.length,2)
    calls[0][result](result==='reject'?Error('旧请求'):undefined);await old
    assert.equal(page.fulfillVisible.value,true);assert.equal(page.currentItem.value.id,602);assert.equal(page.fulfilling.value,true)
    assert.equal(lists.length,1);await page.submitFulfill();assert.equal(calls.length,2)
    calls[1].resolve();await latest;assert.equal(page.fulfilling.value,false)
  }
})

test('补登记重复点击只提交一次，旧成功或失败及finally不影响新门店补登记',async()=>{
  for(const result of ['resolve','reject']) {
    const calls=[],page=pickup({createPickupRequest:(id,body)=>pendingCall(calls,{id,body})})
    page.openCreateDialog();page.createForm.value={orderId:50,orderItemId:90,sourceLocationId:11}
    const old=page.submitCreate();const duplicate=page.submitCreate();assert.equal(calls.length,1)
    page.storeId.value=2;page.handleStoreChange();await flush();page.openCreateDialog()
    page.createForm.value={orderId:51,orderItemId:91,sourceLocationId:22}
    const latest=page.submitCreate();assert.equal(calls.length,2)
    calls[0][result](result==='reject'?Error('旧登记'):undefined);await old
    assert.equal(page.createVisible.value,true);assert.equal(page.creating.value,true);assert.equal(page.createForm.value.orderId,51)
    const repeated=page.submitCreate();assert.equal(calls.length,2)
    calls[1].resolve();await latest;assert.equal(page.creating.value,false)
  }
})

test('相同门店状态重复刷新也按查询序号隔离，旧成功不提前关loading且旧失败不清新数据',async()=>{
  for(const result of ['resolve','reject']) {
    const lists=[],page=pickup({getPickupWorkItems:(store,status)=>pendingCall(lists,{store,status})})
    const first=page.loadWorkItems(),latest=page.loadWorkItems()
    if(result==='resolve') {
      lists[0].resolve([partiallyReturned()]);await first
      assert.equal(page.workItems.value.length,0);assert.equal(page.loading.value,true)
      lists[1].resolve([{...partiallyReturned(),id:602}]);await latest
    } else {
      lists[1].resolve([{...partiallyReturned(),id:602}]);await latest
      lists[0].reject(Error('旧刷新'));await first
    }
    assert.deepEqual(Array.from(page.workItems.value,item=>item.id),[602]);assert.equal(page.loading.value,false)
  }
})

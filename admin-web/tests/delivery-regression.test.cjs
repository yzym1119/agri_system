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
    useRoute: () => ({query: {}}),
    useRouter: () => ({push() {}}),
    sessionStorage: {getItem:()=>null,setItem(){},removeItem(){}},
    ref: vue.ref, reactive: vue.reactive, computed: vue.computed, watch: vue.watch,
    onMounted() {}, onUnmounted() {}, nextTick: vue.nextTick,
    defineProps: () => ({}), defineEmits: () => () => {}, defineExpose() {},
    useUserStore: () => ({ userInfo: { id: 8, extra: { storeId: 2 } } }),
    usePermissionStore: () => ({ permissions: ['staff:list'] }),
    hasPermission: (required,owned) => !required || (Array.isArray(required)?required:[required]).some(p=>owned.includes(p)),
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
const tick = () => new Promise(resolve => setImmediate(resolve))
const material = () => ({id:'cart-1',itemType:1,productId:10,name:'肥料',quantity:1,currentUnit:'袋',unitPrice:10,suggestedUnitPrice:10,subtotal:10,quoteId:'商品报价',warehousePickup:true})

test('POS按真实数字订单ID显示自动取货请求提示，不需要补登记',async()=>{
  const page=loadPage('src/views/pos/index.vue',['cartItems','pickupVisible','pickupOrderId','handleSubmitOrder'],{
    createPOSOrder:async()=>77
  })
  page.cartItems.value=[material()]
  await page.handleSubmitOrder({deliveryType:1,items:[]})
  assert.equal(page.pickupVisible.value,true)
  assert.equal(page.pickupOrderId.value,77)
})

function checkout(overrides = {}) {
  const props = vue.reactive({modelValue:false,totalAmount:10,discountAmount:0,checkoutData:{paymentType:1},cartItems:[material()],customer:null,submitting:false})
  const emitted = [], requests = []
  const page = loadPage('src/views/pos/components/CheckoutDialog.vue', [
    'handleConfirm', 'canConfirm', 'confirmForm', 'paymentType', 'mixedDetails', 'walletAuthCode',
    'availableCoupons', 'selectedCouponId', 'loadCustomerCoupons',
    "deliveryForm: typeof deliveryForm === 'undefined' ? undefined : deliveryForm"
  ], {
    defineProps:()=>props, defineEmits:()=> (event,data)=>emitted.push({event,data}),
    getCustomerCoupons: id=>new Promise((resolve,reject)=>requests.push({id,resolve,reject})), ...overrides
  })
  return {...page,props,emitted,requests}
}
async function open(page) {page.props.modelValue=true;await vue.nextTick();await tick()}

test('POS默认当场交货，提交保留后端报价与库房提货标记',async()=>{
  const page=checkout();await open(page);page.handleConfirm()
  const body=page.emitted.find(event=>event.event==='submit').data
  assert.equal(body.deliveryType,1)
  assert.equal(body.items[0].quotedUnitPrice,10)
  assert.equal(body.items[0].warehousePickup,true)
  assert.equal(body.receiverAddress,undefined)
})

test('门店配送缺收货人、电话或地址不能发起收款，完整信息随整单提交',async()=>{
  const page=checkout();await open(page)
  assert.ok(page.deliveryForm,'收银结算应提供整单履约表单')
  page.deliveryForm.deliveryType=2
  for(const missing of ['receiverName','receiverPhone','receiverAddress']) {
    Object.assign(page.deliveryForm,{receiverName:'张农户',receiverPhone:'13800000000',receiverAddress:'东村3号'})
    page.deliveryForm[missing]='   '
    assert.equal(page.canConfirm.value,false)
    page.handleConfirm()
    assert.equal(page.emitted.filter(event=>event.event==='submit').length,0)
  }
  Object.assign(page.deliveryForm,{receiverName:' 张农户 ',receiverPhone:' 13800000000 ',receiverAddress:' 东村3号 '})
  assert.equal(page.canConfirm.value,true)
  page.handleConfirm()
  const body=page.emitted.find(event=>event.event==='submit').data
  assert.equal(body.deliveryType,2)
  assert.equal(body.receiverName,'张农户');assert.equal(body.receiverPhone,'13800000000');assert.equal(body.receiverAddress,'东村3号')
  assert.equal(body.items[0].quotedUnitPrice,10)
  assert.equal(body.items[0].warehousePickup,undefined,'人工配送不同时提交自提任务')
})

test('门店配送保留套餐报价指纹、混合赊销金额及失败后收货信息',async()=>{
  const page=checkout();page.props.customer={id:1,name:'农户',phone:'13800000000',balance:0,specialFund:0,creditLimit:100,totalDebt:0}
  page.props.cartItems=[{...material(),itemType:2,productId:undefined,packageId:20,quantity:2,quoteId:'套餐组成价格指纹'}]
  await open(page);assert.ok(page.deliveryForm)
  Object.assign(page.deliveryForm,{deliveryType:2,receiverName:'农户',receiverPhone:'13800000000',receiverAddress:'南村'})
  page.paymentType.value=10;page.mixedDetails.cash=4;page.mixedDetails.credit=6;await vue.nextTick()
  page.handleConfirm();page.handleConfirm()
  const body=page.emitted.find(event=>event.event==='submit').data
  assert.equal(body.items[0].packageQuoteId,'套餐组成价格指纹');assert.equal(body.items[0].quantity,2)
  assert.deepEqual(Array.from(body.payments,entry=>[entry.paymentType,entry.amount]),[[1,4],[6,6]])
  assert.equal(page.deliveryForm.receiverAddress,'南村')
})

test('切换客户后收货信息与钱包授权重置，旧客户优惠券晚到不能覆盖当前客户',async()=>{
  const page=checkout();page.props.customer={id:1,name:'客户一',phone:'13800000001',balance:100,specialFund:0};await open(page)
  const old=page.requests.at(-1)
  assert.ok(page.deliveryForm)
  Object.assign(page.deliveryForm,{deliveryType:2,receiverAddress:'客户一地址'})
  page.selectedCouponId.value=11;page.walletAuthCode.value='123456'
  page.props.customer={id:2,name:'客户二',phone:'13800000002',balance:100,specialFund:0};await vue.nextTick()
  const latest=page.requests.at(-1);assert.equal(latest.id,2)
  latest.resolve({records:[{id:22,name:'客户二券',amount:1}]});await tick()
  old.resolve({records:[{id:11,name:'客户一券',amount:2}]});await tick()
  assert.deepEqual(Array.from(page.availableCoupons.value,item=>item.id),[22])
  assert.equal(page.selectedCouponId.value,null);assert.equal(page.walletAuthCode.value,'')
  assert.equal(page.deliveryForm.receiverName,'客户二');assert.equal(page.deliveryForm.receiverPhone,'13800000002')
  assert.equal(page.deliveryForm.receiverAddress,'')
})

test('收银关闭后旧券失败不能清掉重新打开的新券',async()=>{
  const page=checkout();page.props.customer={id:1,name:'客户',phone:'13800000001',balance:0,specialFund:0};await open(page)
  const old=page.requests.at(-1);page.props.modelValue=false;await vue.nextTick();await open(page)
  page.requests.at(-1).resolve({records:[{id:22,name:'新券',amount:1}]});await tick()
  old.reject(Error('旧请求失败'));await tick()
  assert.deepEqual(Array.from(page.availableCoupons.value,item=>item.id),[22])
})

test('人工配送开单成功显示待配送，不弹第二组库房提货任务',async()=>{
  const submitted=[]
  const page=loadPage('src/views/pos/index.vue',['cartItems','pickupVisible','checkoutVisible','handleSubmitOrder'],{
    createPOSOrder:async body=>{submitted.push(body);return 77}
  })
  page.cartItems.value=[material()];page.checkoutVisible.value=true
  await page.handleSubmitOrder({deliveryType:2,receiverName:'农户',receiverPhone:'13800000000',receiverAddress:'村口',items:[]})
  assert.equal(submitted[0].deliveryType,2);assert.equal(page.pickupVisible.value,false)
  assert.ok(page.messages.some(message=>message.level==='success'&&/待配送/.test(message.text)))
})

test('配送派单与签收由真实配送状态控制，POS和部分退货剩余物料均可操作',()=>{
  const page=loadPage('src/views/order/list/index.vue',['canShipByLocal','canShipByExpress',
    "canConfirmDelivery: typeof canConfirmDelivery === 'undefined' ? undefined : canConfirmDelivery"])
  const pending={id:1,orderType:1,orderStatus:4,deliveryType:2,deliveryStatus:2,canShip:true,canConfirmReceipt:false}
  assert.equal(page.canShipByLocal(pending),true)
  assert.equal(page.canShipByExpress(pending),false)
  assert.equal(page.canShipByLocal({...pending,orderStatus:9}),true)
  assert.ok(page.canConfirmDelivery)
  assert.equal(page.canConfirmDelivery({...pending,orderStatus:9,deliveryStatus:3,canShip:false,canConfirmReceipt:true}),true)
  for(const blocked of [{orderStatus:7},{orderStatus:8},{deliveryStatus:4},{canShip:false}])
    assert.equal(page.canShipByLocal({...pending,...blocked}),false)
  assert.equal(page.canShipByLocal({...pending,deliveryType:null}),false,'无真实配送关系不猜成同城')
})

test('配送详情切换订单时旧响应不能覆盖新订单与签收时间',async()=>{
  const props=vue.reactive({modelValue:true,orderId:1}),requests=[]
  const page=loadPage('src/views/order/components/OrderDetail.vue',['loadOrderDetail','orderDetail'],{
    defineProps:()=>props,getOrderDetail:id=>new Promise(resolve=>requests.push({id,resolve}))
  })
  const old=page.loadOrderDetail();props.orderId=2;await vue.nextTick()
  const latest=page.loadOrderDetail()
  requests.at(-1).resolve({id:2,orderStatus:9,delivery:{deliveryType:2,deliveryStatus:4,receivedAt:'2026-10-14T10:00:00'}});await latest
  requests[0].resolve({id:1,delivery:{deliveryType:2,deliveryStatus:2}});await old
  assert.equal(page.orderDetail.value.id,2)
  assert.equal(page.orderDetail.value.delivery.receivedAt,'2026-10-14T10:00:00')
})

test('待派送及配送中列表发送真实配送状态筛选，不添加小程序来源限制',async()=>{
  const queries=[]
  const page=loadPage('src/views/order/list/index.vue',['queryParams','handleTabChange'],{
    getOrderList:async params=>{queries.push({...params});return {records:[],total:0}}
  })
  page.queryParams.deliveryType=2;page.handleTabChange('delivery-2');await tick()
  page.handleTabChange('delivery-3');await tick()
  assert.equal(queries[0].deliveryStatus,2);assert.equal(queries[1].deliveryStatus,3)
  assert.equal(queries[0].deliveryType,2);assert.equal(queries[0].orderType,undefined)
  assert.equal(queries[0].orderStatus,undefined)
})

test('配送筛选快速切换时旧派送列表响应不能覆盖新的配送中结果',async()=>{
  const requests=[]
  const page=loadPage('src/views/order/list/index.vue',['queryParams','handleTabChange','tableData'],{
    getOrderList:params=>new Promise(resolve=>requests.push({params:{...params},resolve}))
  })
  page.handleTabChange('delivery-2');page.handleTabChange('delivery-3')
  requests[1].resolve({records:[{id:3,deliveryStatus:3}],total:1});await tick()
  requests[0].resolve({records:[{id:2,deliveryStatus:2}],total:1});await tick()
  assert.equal(page.tableData.value[0].id,3)
})

test('派单校验期间切换订单不得把旧配送员提交给新订单',async()=>{
  const props=vue.reactive({modelValue:true,mode:'local',order:{id:1,deliveryType:2,deliveryStatus:2,canShip:true}}),calls=[]
  const page=loadPage('src/views/order/components/ShipDialog.vue',['form','formRef','handleSubmit'],{
    defineProps:()=>props,shipOrder:async(id,body)=>calls.push({id,body})
  })
  page.form.deliveryStaffId=8
  page.formRef.value={validate:async callback=>{props.order={id:2,deliveryType:2,deliveryStatus:2,canShip:true};await vue.nextTick();return callback(true)},clearValidate(){}}
  await page.handleSubmit();assert.equal(calls.length,0)
})

test('派单重复点击只发送一次路径订单命令，旧响应不关闭新订单弹窗',async()=>{
  const props=vue.reactive({modelValue:true,mode:'local',order:{id:1,deliveryType:2,deliveryStatus:2,canShip:true}}),calls=[],emitted=[]
  const page=loadPage('src/views/order/components/ShipDialog.vue',['form','formRef','handleSubmit'],{
    defineProps:()=>props,defineEmits:()=> (event,data)=>emitted.push({event,data}),
    shipOrder:(id,body)=>new Promise(resolve=>calls.push({id,body,resolve}))
  })
  page.form.deliveryStaffId=8;page.formRef.value={validate:async callback=>callback(true),clearValidate(){}}
  const first=page.handleSubmit();await tick();const duplicate=page.handleSubmit();await tick()
  assert.equal(calls.length,1)
  assert.equal(calls[0].id,1);assert.equal(calls[0].body.deliveryStaffId,8);assert.equal(calls[0].body.orderId,undefined)
  props.order={id:2,deliveryType:2,deliveryStatus:2,canShip:true};await vue.nextTick()
  calls[0].resolve();await first;await duplicate
  assert.equal(emitted.some(event=>event.event==='success'),false)
})

test('后端缺失或拒绝交付能力时，前端不能按订单状态猜测派单或签收',()=>{
  const page=loadPage('src/views/order/list/index.vue',['canShipByLocal','canShipByExpress','canConfirmDelivery'])
  const row={id:1,orderType:1,orderStatus:9,deliveryType:2,deliveryStatus:2}
  assert.equal(page.canShipByLocal(row),false)
  assert.equal(page.canShipByExpress({...row,deliveryType:3}),false)
  assert.equal(page.canConfirmDelivery({...row,deliveryStatus:3}),false)
  assert.equal(page.canConfirmDelivery({...row,deliveryStatus:3,canConfirmReceipt:false}),false)
})

test('离开配送页签时清除配送状态，退款页签仍以8和9查原单状态',async()=>{
  const queries=[]
  const page=loadPage('src/views/order/list/index.vue',['queryParams','handleTabChange'],{
    getOrderList:async params=>{queries.push({...params});return {records:[],total:0}}
  })
  for(const tab of ['delivery-2','refund','delivery-3','all','delivery-2','3']) page.handleTabChange(tab)
  await tick()
  assert.equal(queries[1].deliveryStatus,undefined);assert.equal(queries[1].orderStatus,'8,9')
  assert.equal(queries[3].deliveryStatus,undefined);assert.equal(queries[3].orderStatus,undefined)
  assert.equal(queries[5].deliveryStatus,undefined);assert.equal(queries[5].orderStatus,'3')
})

test('派单校验期间关闭弹窗或修改配送员均不得提交点击前的配送命令',async()=>{
  for(const mutation of ['close','staff']) {
    const props=vue.reactive({modelValue:true,mode:'local',order:{id:1}}),calls=[]
    const page=loadPage('src/views/order/components/ShipDialog.vue',['form','formRef','handleSubmit','handleClose'],{
      defineProps:()=>props,shipOrder:async(id,body)=>calls.push({id,body})
    })
    page.form.deliveryStaffId=8
    page.formRef.value={validate:async callback=>{
      if(mutation==='close') page.handleClose();else page.form.deliveryStaffId=9
      return callback(true)
    },clearValidate(){}}
    await page.handleSubmit();assert.equal(calls.length,0,mutation)
  }
})

test('派单明确失败后保留所选员工并可重试，错误显示后端业务原因',async()=>{
  const props=vue.reactive({modelValue:true,mode:'local',order:{id:1}}),calls=[]
  const page=loadPage('src/views/order/components/ShipDialog.vue',['form','formRef','handleSubmit'],{
    defineProps:()=>props,shipOrder:async(id,body)=>{calls.push({id,body});if(calls.length===1)throw Error('有处理中售后，暂缓交付')}
  })
  page.form.deliveryStaffId=8;page.formRef.value={validate:async callback=>callback(true),clearValidate(){}}
  await page.handleSubmit()
  assert.equal(page.form.deliveryStaffId,8)
  assert.ok(page.messages.some(message=>message.level==='error'&&message.text==='有处理中售后，暂缓交付'))
  await page.handleSubmit();assert.equal(calls.length,2)
})

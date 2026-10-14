const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const ts = require('typescript')
const vue = require('vue')
const { randomUUID } = require('node:crypto')
const { parse: parseVue } = require('@vue/compiler-sfc')
const { compile: compileTemplate } = require('@vue/compiler-dom')

// 运行真实生产 Vue setup 脚本，仅替换网络、组件宿主和消息边界。
function loadPage(relativePath, exports, overrides = {}) {
  const filename = path.join(__dirname, '..', relativePath)
  const script = fs.readFileSync(filename, 'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  const source = ts.createSourceFile(filename + '.ts', script, ts.ScriptTarget.Latest, true)
  const code = source.statements.filter(s => !ts.isImportDeclaration(s)).map(s => s.getFullText(source)).join('\n')
  const messages = []
  const context = {
    ref: vue.ref, reactive: vue.reactive, computed: vue.computed, watch: vue.watch,
    onMounted() {}, onUnmounted() {}, nextTick: vue.nextTick,
    defineProps: () => ({}), defineEmits: () => () => {},
    useUserStore: () => ({ userInfo: { id: 8, extra: { storeId: 2, storeName: '测试门店' } } }),
    usePermissionStore: () => ({ permissions: ['*'] }),
    crypto: { randomUUID },
    ElMessage: Object.fromEntries(['error', 'warning', 'success', 'info'].map(level => [level, value => messages.push({level, text: typeof value === 'string' ? value : value?.message})])),
    console: { error() {}, warn() {} },
    getOrderPaymentSummary: () => ({netReceivable: 100}),
    getAllStores: async () => [],
    ...overrides
  }
  vm.createContext(context)
  const names=source.statements.filter(statement=>ts.isVariableStatement(statement)).flatMap(statement=>Array.from(statement.declarationList.declarations).filter(declaration=>ts.isIdentifier(declaration.name)).map(declaration=>declaration.name.text))
  vm.runInContext(ts.transpileModule(code + `\nglobalThis.page = {${exports.join(',')}}; globalThis.bindings={${names.join(',')}};`, { compilerOptions: {target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS} }).outputText, context, {filename})
  return {...context.page, messages, bindings:context.bindings, filename}
}
const tick = () => new Promise(resolve => setImmediate(resolve))
function formValidator(ref) { ref.value = { validate: async callback => { if(callback) return callback(true); return true }, clearValidate() {} } }
function line(overrides = {}) { return {orderItemId: 11, productName: '农资', quantity: 10, refundedQuantity: 0, refundableQuantity: 10, unit: '袋', unitPrice: 10, totalPrice: 100, refundedAmount: 0, refundableAmount: 100, returnQuantity: 3, refundAmount: 0, ...overrides} }
function preview(overrides = {}) { return {quoteId: '资金版本一', orderId: 1, refundAmount: 30, creditReductionAmount: 30, cashRefundAmount: 0, remainingDebt: 100, remainingRefundableAmount: 100, items: [{orderItemId:11, quantity:3, refundAmount:30, refundableQuantity:10, refundableAmount:100}], sources: [], ...overrides} }
function refundPage(overrides = {}) {
  const props = vue.reactive({modelValue: true, order:{id:1,orderStatus:6,paymentMethod:6,payAmount:100}})
  const requests = [], submitted = [], emissions = []
  const page = loadPage('src/views/order/components/RefundDialog.vue', ['form', 'formRef', 'calculateRefundAmount', 'handleSubmit', 'handleClose', 'submitting', 'refundPreview'], {
    defineProps: () => props, defineEmits: () => (...args) => emissions.push(args),
    getRefundPreview: (id, body) => new Promise((resolve, reject) => requests.push({id, body, resolve, reject})),
    createAfterSales: async body => submitted.push(body), refundOrder: async (id, body) => submitted.push(body),
    getOrderDetail: async () => ({id:1,items:[]}), ...overrides
  })
  formValidator(page.formRef)
  page.form.items = [line()]; page.form.reason = '退货'
  return {...page, props, requests, submitted, emissions}
}

test('还款打开时获取当前门店真实可还余额，不沿用客户全局欠款', async () => {
  const props = vue.reactive({modelValue:false,customerId:1,customerName:'测试客户',currentTotalDebt:900})
  const queries=[]
  const page = loadPage('src/views/customer/list/components/RepaymentDialog.vue', ['form','handleSubmit','formRef'], {
    defineProps: () => props, getCreditStatistics: async id => { queries.push(id); return {storeId:2,totalDebt:70,creditLimit:200,remainingCredit:130} }
  })
  props.modelValue=true; await vue.nextTick(); await tick()
  assert.deepEqual(queries,[1])
  assert.equal(page.form.amount,70)
})

test('还款请求失败后重试复用请求标识，成功后新收款使用新标识', async () => {
  const props = vue.reactive({modelValue:false,customerId:1,customerName:'客户',currentTotalDebt:90})
  const payloads=[]; let fail=true
  const page = loadPage('src/views/customer/list/components/RepaymentDialog.vue', ['form','handleSubmit','formRef'], {
    defineProps:()=>props, getCreditStatistics:async()=>({storeId:2,totalDebt:90,remainingCredit:10}),
    repayCredit:async body=>{payloads.push({...body});if(fail) throw Error('暂未收到响应')}
  })
  formValidator(page.formRef)
  props.modelValue=true;await vue.nextTick();await tick();page.form.amount=30
  await page.handleSubmit();assert.equal(typeof payloads[0].requestKey,'string');assert.ok(payloads[0].requestKey)
  fail=false;await page.handleSubmit()
  assert.equal(payloads[1].requestKey,payloads[0].requestKey)
  assert.equal('storeId' in payloads[1],false);assert.equal('createdBy' in payloads[1],false)
  props.modelValue=false;await vue.nextTick();props.modelValue=true;await vue.nextTick();await tick()
  await page.handleSubmit();assert.notEqual(payloads[2].requestKey,payloads[1].requestKey)
})

test('退款数量变化必须取服务端金额，不按前端历史单价分摊', async()=>{
  const page=refundPage();page.form.items[0].unitPrice=999
  const pending=page.calculateRefundAmount()
  assert.equal(page.requests.length,1)
  assert.equal(page.requests[0].body.items[0].refundAmount,undefined)
  page.requests[0].resolve(preview({refundAmount:29.99,items:[{orderItemId:11,quantity:3,refundAmount:29.99,refundableQuantity:10,refundableAmount:100}]}));await pending
  assert.equal(page.form.refundAmount,29.99);assert.equal(page.form.items[0].refundAmount,29.99)
})

test('纯赊销退货提交服务端冲销金额与报价指纹，不能用现金退款0替代30',async()=>{
  const page=refundPage();const pending=page.calculateRefundAmount();assert.equal(page.requests.length,1)
  page.requests[0].resolve(preview());await pending;await page.handleSubmit()
  assert.equal(page.submitted.length,1);assert.equal(page.submitted[0].refundAmount,30)
  assert.equal(page.submitted[0].quoteId,'资金版本一')
})

test('旧退款预览晚到不得覆盖新数量和新报价',async()=>{
  const page=refundPage();const first=page.calculateRefundAmount();assert.equal(page.requests.length,1)
  page.form.items[0].returnQuantity=2;const second=page.calculateRefundAmount();assert.equal(page.requests.length,2)
  page.requests[1].resolve(preview({quoteId:'资金版本二',refundAmount:20,items:[{orderItemId:11,quantity:2,refundAmount:20}]}));await second
  page.requests[0].resolve(preview());await first;assert.equal(page.form.refundAmount,20)
  await page.handleSubmit();assert.equal(page.submitted[0].quoteId,'资金版本二')
})

test('退款预览失败或仍未返回时不能提交退货',async()=>{
  const page=refundPage();const pending=page.calculateRefundAmount();assert.equal(page.requests.length,1)
  await page.handleSubmit();assert.equal(page.submitted.length,0)
  page.requests[0].reject(Error('历史资金需要核对'));await pending
  await page.handleSubmit();assert.equal(page.submitted.length,0)
})

test('零金额套餐物料使用后端预览仍可退货回库',async()=>{
  const page=refundPage();page.form.items=[line({returnQuantity:1,refundableAmount:0,totalPrice:0})]
  const pending=page.calculateRefundAmount();assert.equal(page.requests.length,1)
  page.requests[0].resolve(preview({refundAmount:0,creditReductionAmount:0,items:[{orderItemId:11,quantity:1,refundAmount:0}]}));await pending
  await page.handleSubmit();assert.equal(page.submitted.length,1);assert.equal(page.submitted[0].items[0].quantity,1);assert.equal(page.submitted[0].refundAmount,0)
})

test('关闭退款弹窗后到达的旧预览不得重新填入金额',async()=>{
  const page=refundPage();const pending=page.calculateRefundAmount();assert.equal(page.requests.length,1)
  page.handleClose();page.requests[0].resolve(preview());await pending
  assert.equal(page.form.refundAmount,0);assert.equal(page.form.items.length,0)
})

test('后端明确旧预览拒绝后重新读已退事实，清空选择等待重新确认',async()=>{
  const submitted=[]
  const page=refundPage({createAfterSales:async body=>{submitted.push(body);throw Object.assign(Error('退货资金预览已变化，请重新确认'),{apiBusinessCode:10001})},
    getOrderDetail:async()=>({id:1,afterSalesList:[{id:8,status:50,refundAmount:30}],items:[{id:11,productName:'农资',quantity:10,refundedQuantity:3,refundableQuantity:7,totalPrice:100,refundableAmount:70,refundedAmount:30}]})})
  const quoted=page.calculateRefundAmount();page.requests[0].resolve(preview());await quoted
  const submission=page.handleSubmit();await tick();assert.equal(page.requests.length,1,'拒绝后不能保留数量直接换报价再退')
  await submission
  assert.equal(submitted.length,1);assert.equal(page.form.items[0].returnQuantity,0)
  assert.equal(page.form.items[0].refundableQuantity,7);assert.equal(page.form.reason,'退货')
})

test('待退款售后使用原售后明细预览，确认请求带资金指纹',async()=>{
  const requests=[],submitted=[]
  const page=loadPage('src/views/order/after-sales/index.vue',['openRefundDialog','submitRefund','refundFormRef','refundForm'],{
    getAfterSalesDetail:async()=>({id:8,orderId:1,refundAmount:30,items:[{orderItemId:11,quantity:3,refundAmount:30}]}),
    getRefundPreview:(id,body)=>new Promise((resolve,reject)=>requests.push({id,body,resolve,reject})),
    refundAfterSales:async(id,body)=>submitted.push({id,body}),getAfterSalesPage:async()=>({records:[],total:0})
  })
  formValidator(page.refundFormRef)
  const opening=page.openRefundDialog({id:8,orderId:1,refundAmount:30});await tick();assert.equal(requests.length,1)
  assert.equal(requests[0].body.afterSalesId,8);assert.equal(requests[0].id,1)
  requests[0].resolve(preview());await opening;await page.submitRefund();await tick()
  assert.equal(submitted[0].body.quoteId,'资金版本一');assert.equal(submitted[0].body.refundAmount,30)
})

test('还款弹窗客户变化后必须换请求标识并读新客户本店余额',async()=>{
  const props=vue.reactive({modelValue:false,customerId:1,customerName:'客户一'})
  const payloads=[],queries=[]
  const page=loadPage('src/views/customer/list/components/RepaymentDialog.vue',['form','formRef','handleSubmit'],{
    defineProps:()=>props,getCreditStatistics:async id=>{queries.push(id);return {storeId:2,totalDebt:id===1?90:60}},
    repayCredit:async body=>{payloads.push({...body});throw Error('请求结果暂未确认')}
  })
  formValidator(page.formRef);props.modelValue=true;await vue.nextTick();await tick();page.form.amount=30
  await page.handleSubmit();props.customerId=2;props.customerName='客户二';await vue.nextTick();await tick()
  await page.handleSubmit();assert.deepEqual(queries,[1,2]);assert.equal(payloads[1].customerId,2)
  assert.equal(payloads[1].amount,60);assert.notEqual(payloads[1].requestKey,payloads[0].requestKey)
})

test('已提交还款失败后改变金额不能用原请求再次收款',async()=>{
  const props=vue.reactive({modelValue:false,customerId:1,customerName:'客户'})
  const payloads=[]
  const page=loadPage('src/views/customer/list/components/RepaymentDialog.vue',['form','formRef','handleSubmit'],{
    defineProps:()=>props,getCreditStatistics:async()=>({storeId:2,totalDebt:90}),
    repayCredit:async body=>{payloads.push({...body});throw Error('网络中断')}
  })
  formValidator(page.formRef);props.modelValue=true;await vue.nextTick();await tick();page.form.amount=30
  await page.handleSubmit();page.form.amount=40;await page.handleSubmit()
  assert.equal(payloads.length,1);assert.ok(page.messages.some(message=>message.level==='warning'))
})

test('还款成功响应丢失后本店欠款为0，仍能用原请求取回原收款批次',async()=>{
  const props=vue.reactive({modelValue:false,customerId:1,customerName:'客户'})
  const payloads=[];let serverReceived=false
  const page=loadPage('src/views/customer/list/components/RepaymentDialog.vue',['form','formRef','handleSubmit','rules'],{
    defineProps:()=>props,getCreditStatistics:async()=>({storeId:2,totalDebt:serverReceived?0:30}),
    repayCredit:async body=>{payloads.push({...body});if(!serverReceived){serverReceived=true;throw Error('响应在网络中丢失')}return {repaymentNo:'原收款批次',amount:30}}
  })
  page.formRef.value={validate:async callback=>{
    let error;page.rules.amount[1].validator(null,page.form.amount,value=>{error=value})
    return callback(!error)
  },clearValidate(){}}
  props.modelValue=true;await vue.nextTick();await tick();await page.handleSubmit()
  props.modelValue=false;await vue.nextTick();props.modelValue=true;await vue.nextTick();await tick()
  assert.equal(page.form.amount,30)
  await page.handleSubmit();assert.equal(payloads.length,2);assert.equal(payloads[1].requestKey,payloads[0].requestKey)
  assert.equal(payloads[1].amount,30)
})

test('表单校验期间数量改变不能把旧资金指纹配新数量提交',async()=>{
  const page=refundPage();const pending=page.calculateRefundAmount();page.requests[0].resolve(preview());await pending
  page.formRef.value={validate:async callback=>{page.form.items[0].returnQuantity=2;return callback(true)},clearValidate(){}}
  await page.handleSubmit();assert.equal(page.submitted.length,0)
})

test('旧客户收款响应晚到不能清掉新客户操作的请求标识',async()=>{
  const props=vue.reactive({modelValue:false,customerId:1,customerName:'客户一'})
  const requests=[]
  const page=loadPage('src/views/customer/list/components/RepaymentDialog.vue',['form','formRef','handleSubmit'],{
    defineProps:()=>props,getCreditStatistics:async id=>({storeId:2,totalDebt:id===1?30:60}),
    repayCredit:body=>new Promise(resolve=>requests.push({body:{...body},resolve}))
  })
  formValidator(page.formRef);props.modelValue=true;await vue.nextTick();await tick();const first=page.handleSubmit();await tick()
  props.customerId=2;props.customerName='客户二';await vue.nextTick();await tick()
  requests[0].resolve({repaymentNo:'客户一批次'});await first
  const second=page.handleSubmit();await tick();assert.equal(requests.length,2)
  assert.equal(requests[1].body.customerId,2);assert.notEqual(requests[1].body.requestKey,requests[0].body.requestKey)
  requests[1].resolve({repaymentNo:'客户二批次'});await second
})

test('售后确认校验期间切换售后单不能用旧预览处理新单',async()=>{
  const submitted=[]
  const page=loadPage('src/views/order/after-sales/index.vue',['openRefundDialog','submitRefund','refundFormRef','currentRow'],{
    getAfterSalesDetail:async()=>({id:8,orderId:1,refundAmount:30,items:[{orderItemId:11,quantity:3,refundAmount:30}]}),
    getRefundPreview:async()=>preview(),refundAfterSales:async(id,body)=>submitted.push({id,body}),
    getAfterSalesPage:async()=>({records:[],total:0})
  })
  await page.openRefundDialog({id:8,orderId:1,refundAmount:30})
  page.refundFormRef.value={validate:async callback=>{page.currentRow.value={id:9,orderId:2};return callback(true)},clearValidate(){}}
  await page.submitRefund();assert.equal(submitted.length,0)
})

test('生成对账单展示服务端快照，不能提交前端操作员或用客户余额替换结果',async()=>{
  const requests=[]
  const snapshot={id:1,customerId:1,statementNo:'原单对账',startDate:'2026-10-01',endDate:'2026-10-13',totalCreditAmount:250,totalPaidAmount:120,totalRemainingAmount:90,remark:'期间原单净额及已退来源'}
  const page=loadPage('src/views/customer/list/index.vue',['customerDetail','statementFormRef','statementFormData','statementDialogVisible','handleGenerateStatement',"statementResult: typeof statementResult === 'undefined' ? undefined : statementResult"],{
    generateCreditStatement:async body=>{requests.push(body);return snapshot},ORDER_STATUS_MAP:{},ORDER_STATUS_TAG_MAP:{},PAYMENT_METHOD_MAP:{},TRANSACTION_TYPE_MAP:{},WALLET_TYPE_MAP:{}
  })
  page.customerDetail.value={id:1,totalDebt:999,creditLimit:2000};page.statementDialogVisible.value=true
  page.statementFormData.dateRange=['2026-10-01','2026-10-13'];formValidator(page.statementFormRef)
  await page.handleGenerateStatement();await tick()
  assert.equal('createdBy' in requests[0],false)
  assert.equal(page.statementDialogVisible.value,true,'生成结果应保留在只读对账弹窗')
  assert.equal(page.statementResult.value.totalRemainingAmount,90)
  assert.equal(page.statementResult.value.totalCreditAmount,250)
})

test('退货成功响应丢失后只重放原报价原内容，不取新预览再次退货',async()=>{
  const submitted=[];let lost=true
  const page=refundPage({createAfterSales:async body=>{submitted.push(body);if(lost){lost=false;throw Object.assign(Error('网络中断'),{code:'ECONNABORTED',request:{}})}return 81}})
  const quoted=page.calculateRefundAmount();page.requests[0].resolve(preview());await quoted
  const first=page.handleSubmit();await tick();assert.equal(page.requests.length,1,'未知结果不能换一个新报价')
  await first;page.form.items[0].returnQuantity=2;page.form.reason='被修改的原因'
  await page.handleSubmit();assert.equal(submitted.length,2)
  assert.equal(submitted[1].quoteId,submitted[0].quoteId)
  assert.equal(submitted[1].items[0].quantity,3);assert.equal(submitted[1].reason,'退货')
})

test('已审核售后成功响应丢失只重放原确认，不重新预览资金',async()=>{
  const requests=[],submitted=[];let lost=true
  const page=loadPage('src/views/order/after-sales/index.vue',['openRefundDialog','submitRefund','refundFormRef'],{
    getAfterSalesDetail:async()=>({id:8,orderId:1,refundAmount:30,status:40,items:[{orderItemId:11,quantity:3,refundAmount:30}]}),
    getRefundPreview:async(id,body)=>{requests.push({id,body});return preview()},
    refundAfterSales:async(id,body)=>{submitted.push({id,body});if(lost){lost=false;throw Object.assign(Error('响应丢失'),{request:{}})}},
    getAfterSalesPage:async()=>({records:[],total:0})
  })
  formValidator(page.refundFormRef);await page.openRefundDialog({id:8,orderId:1,refundAmount:30})
  await page.submitRefund();assert.equal(requests.length,1)
  await page.submitRefund();assert.equal(submitted.length,2)
  assert.equal(submitted[1].body.quoteId,submitted[0].body.quoteId)
  assert.equal(submitted[1].body.refundAmount,submitted[0].body.refundAmount)
})


test('未知退货关闭重开同一订单仍保留原报价、原因和数量核对',async()=>{
  let lost=true;const submitted=[]
  const page=refundPage({createAfterSales:async body=>{submitted.push(body);if(lost){lost=false;throw Object.assign(Error('网络中断'),{request:{}})}return 81}})
  const quote=page.calculateRefundAmount();page.requests[0].resolve(preview());await quote;await page.handleSubmit()
  page.props.modelValue=false;await vue.nextTick();page.props.modelValue=true;await vue.nextTick();await tick()
  assert.equal(page.requests.length,1);assert.equal(page.form.items[0].returnQuantity,3)
  assert.equal(page.refundPreview.value.quoteId,'资金版本一');await page.handleSubmit()
  assert.equal(submitted[1].quoteId,submitted[0].quoteId)
})

test('未知售后确认关闭重开同一售后仍保留原报价核对',async()=>{
  let lost=true;const requests=[],submitted=[]
  const page=loadPage('src/views/order/after-sales/index.vue',['openRefundDialog','submitRefund','refundFormRef','refundDialogVisible'],{
    getAfterSalesDetail:async()=>({id:8,orderId:1,refundAmount:30,status:40,items:[{orderItemId:11,quantity:3,refundAmount:30}]}),
    getRefundPreview:async()=>{requests.push(1);return preview()},refundAfterSales:async(id,body)=>{submitted.push({id,body});if(lost){lost=false;throw Object.assign(Error('响应丢失'),{request:{}})}},getAfterSalesPage:async()=>({records:[]})
  })
  formValidator(page.refundFormRef);await page.openRefundDialog({id:8,orderId:1,refundAmount:30});await page.submitRefund()
  page.refundDialogVisible.value=false;await vue.nextTick();await page.openRefundDialog({id:8,orderId:1,refundAmount:30})
  assert.equal(page.refundDialogVisible.value,true);assert.equal(requests.length,1);await page.submitRefund();assert.equal(submitted.length,2)
})

test('退款校验期间修改原因不能悄悄用新原因提交',async()=>{
  const page=refundPage();const quote=page.calculateRefundAmount();page.requests[0].resolve(preview());await quote
  page.formRef.value={validate:async callback=>{page.form.reason='校验后改的原因';return callback(true)},clearValidate(){}}
  await page.handleSubmit();assert.equal(page.submitted.length,0)
})

function customerCreditPage(overrides={}) {
  return loadPage('src/views/customer/list/index.vue',['customerDetail','creditQuery','creditList','creditLoading','creditTotal','loadCredit'],{
    ORDER_STATUS_MAP:{},ORDER_STATUS_TAG_MAP:{},PAYMENT_METHOD_MAP:{},TRANSACTION_TYPE_MAP:{},WALLET_TYPE_MAP:{},...overrides
  })
}
test('客户A的慢欠款响应不能覆盖客户B的真实原单列表',async()=>{
  const requests=[];const page=customerCreditPage({getCreditAccounts:(id,...args)=>new Promise(resolve=>requests.push({id,args,resolve}))})
  page.customerDetail.value={id:1};const old=page.loadCredit();page.customerDetail.value={id:2};const current=page.loadCredit()
  requests[1].resolve({records:[{id:22,customerId:2}],total:1});await current
  requests[0].resolve({records:[{id:11,customerId:1}],total:3});await old
  assert.equal(page.creditList.value[0].customerId,2);assert.equal(page.creditTotal.value,1)
})
test('原单欠款分页旧响应不能覆盖新页',async()=>{
  const requests=[];const page=customerCreditPage({getCreditAccounts:(id,...args)=>new Promise(resolve=>requests.push({id,args,resolve}))})
  page.customerDetail.value={id:1};page.creditQuery.pageNum=1;const old=page.loadCredit();page.creditQuery.pageNum=2;const current=page.loadCredit()
  requests[1].resolve({records:[{id:22}],total:2});await current;requests[0].resolve({records:[{id:11}],total:2});await old
  assert.equal(page.creditList.value[0].id,22)
})

test('还款校验期间切客户不能提交另一客户的新交易',async()=>{
  const props=vue.reactive({modelValue:false,customerId:1,customerName:'客户一'});const submitted=[]
  const page=loadPage('src/views/customer/list/components/RepaymentDialog.vue',['form','formRef','handleSubmit'],{
    defineProps:()=>props,getCreditStatistics:async()=>({storeId:2,totalDebt:30}),repayCredit:async body=>submitted.push(body)
  })
  props.modelValue=true;await vue.nextTick();await tick()
  page.formRef.value={validate:async callback=>{props.customerId=2;await vue.nextTick();await tick();return callback(true)},clearValidate(){}}
  await page.handleSubmit();assert.equal(submitted.length,0)
})
test('还款校验期间关闭弹窗不能在后台发出收款',async()=>{
  const props=vue.reactive({modelValue:false,customerId:1,customerName:'客户'});const submitted=[]
  const page=loadPage('src/views/customer/list/components/RepaymentDialog.vue',['form','formRef','handleSubmit'],{
    defineProps:()=>props,getCreditStatistics:async()=>({storeId:2,totalDebt:30}),repayCredit:async body=>submitted.push(body)
  })
  props.modelValue=true;await vue.nextTick();await tick()
  page.formRef.value={validate:async callback=>{props.modelValue=false;await vue.nextTick();return callback(true)},clearValidate(){}}
  await page.handleSubmit();assert.equal(submitted.length,0)
})

test('旧订单退货响应晚到不能关闭或覆盖当前新订单',async()=>{
  let resolveReturn;const requests=[]
  const page=refundPage({createAfterSales:body=>new Promise(resolve=>{requests.push(body);resolveReturn=resolve}),
    getOrderDetail:async id=>({id,items:[{id:22,productName:'新订单物料',quantity:5,refundableQuantity:5,refundableAmount:50,totalPrice:50}]})})
  const quote=page.calculateRefundAmount();page.requests[0].resolve(preview());await quote
  const first=page.handleSubmit();await tick();page.props.order={id:2,orderStatus:6,paymentMethod:1};await vue.nextTick()
  await page.calculateRefundAmount();assert.equal(page.requests.length,1,'上一操作未确认不能换新单报价')
  await page.handleSubmit();assert.equal(requests.length,1,'不能把原quote拿去新订单')
  resolveReturn(81);await first
  assert.equal(page.form.items[0].orderItemId,22);assert.equal(page.form.items[0].returnQuantity,0)
  assert.equal(page.emissions.some(event=>event[0]==='success'),false,'旧响应不能触发父级关闭新操作')
})

test('未知退货结果重放不能换成另一操作员工',async()=>{
  const user={userInfo:{id:8}};const submitted=[];let lost=true
  const page=refundPage({useUserStore:()=>user,createAfterSales:async body=>{submitted.push(body);if(lost){lost=false;throw Object.assign(Error('网络中断'),{request:{}})}return 81}})
  const quote=page.calculateRefundAmount();page.requests[0].resolve(preview());await quote;await page.handleSubmit()
  user.userInfo.id=9;await page.handleSubmit();assert.equal(submitted.length,1)
  user.userInfo.id=8;await page.handleSubmit();assert.equal(submitted.length,2)
  assert.equal(submitted[1].quoteId,submitted[0].quoteId)
})


test('客户A慢详情不得覆盖后打开客户B身份',async()=>{
  const requests=[]
  const page=loadPage('src/views/customer/list/index.vue',['customerDetail','detailVisible','detailActiveTab','handleViewDetail'],{
    getCustomerDetail:id=>new Promise(resolve=>requests.push({id,resolve})),getWalletTransactions:async()=>({records:[],total:0}),
    ORDER_STATUS_MAP:{},ORDER_STATUS_TAG_MAP:{},PAYMENT_METHOD_MAP:{},TRANSACTION_TYPE_MAP:{},WALLET_TYPE_MAP:{}
  })
  const first=page.handleViewDetail({id:1});const second=page.handleViewDetail({id:2})
  requests[1].resolve({id:2,name:'客户B'});await second;requests[0].resolve({id:1,name:'客户A'});await first
  assert.equal(page.customerDetail.value.id,2)
})

test('对账校验期间客户或账期改变不能悄悄生成另一账单',async()=>{
  const requests=[]
  const page=loadPage('src/views/customer/list/index.vue',['customerDetail','statementFormRef','statementFormData','statementDialogVisible','handleGenerateStatement'],{
    generateCreditStatement:async body=>{requests.push(body);return {}},
    ORDER_STATUS_MAP:{},ORDER_STATUS_TAG_MAP:{},PAYMENT_METHOD_MAP:{},TRANSACTION_TYPE_MAP:{},WALLET_TYPE_MAP:{}
  })
  page.customerDetail.value={id:1};page.statementDialogVisible.value=true;page.statementFormData.dateRange=['2026-10-01','2026-10-13']
  page.statementFormRef.value={validate:async callback=>{page.customerDetail.value={id:2};page.statementFormData.dateRange=['2026-09-01','2026-09-30'];return callback(true)},clearValidate(){}}
  await page.handleGenerateStatement();assert.equal(requests.length,0)
})

test('还款被明确拒绝后刷新真实欠额并可用新操作改小金额，未知结果不套用此流程',async()=>{
  const props=vue.reactive({modelValue:false,customerId:1,customerName:'客户'});const requests=[];let rejected=false
  const page=loadPage('src/views/customer/list/components/RepaymentDialog.vue',['form','formRef','handleSubmit'],{
    defineProps:()=>props,getCreditStatistics:async()=>({storeId:2,totalDebt:rejected?20:30}),
    repayCredit:async body=>{requests.push(body);if(!rejected){rejected=true;throw Object.assign(Error('还款金额超过剩余欠款'),{apiBusinessCode:400})}return {repaymentNo:'新操作'} }
  })
  formValidator(page.formRef);props.modelValue=true;await vue.nextTick();await tick();await page.handleSubmit()
  assert.equal(page.form.amount,20);await page.handleSubmit();assert.equal(requests.length,2)
  assert.equal(requests[1].amount,20);assert.notEqual(requests[1].requestKey,requests[0].requestKey)
})

test('一箱可按半箱退货，实际输入组件保留四位数量精度并提交0.5',async()=>{
  const page=refundPage();page.form.items=[line({quantity:1,refundableQuantity:1,unit:'箱',returnQuantity:0.5})]
  const source=fs.readFileSync(page.filename,'utf8');const compiled=compileTemplate(parseVue(source).descriptor.template.content,{mode:'function'})
  const render=new Function('Vue',compiled.code)({...vue,resolveComponent:name=>({name}),resolveDirective:()=>({})})
  const view=vue.proxyRefs({...page.props,...page.bindings})
  const oldWarn=console.warn;console.warn=()=>{}
  let precision
  try {
    const walk=node=>{
      if(!node) return;if(Array.isArray(node)){node.forEach(walk);return}
      if(node.type?.name==='el-input-number') precision=node.props.precision
      if(Array.isArray(node.children)) node.children.forEach(walk)
      else if(node.children&&typeof node.children==='object') for(const slot of Object.values(node.children)) if(typeof slot==='function') walk(slot({row:page.form.items[0],$index:0}))
    }
    walk(render(view,[]))
  } finally {console.warn=oldWarn}
  assert.equal(precision,4,'实际数量输入精度不能由剩余量是整数决定为0')
  const quote=page.calculateRefundAmount();assert.equal(page.requests[0].body.items[0].quantity,0.5)
  page.requests[0].resolve(preview({items:[{orderItemId:11,quantity:0.5,refundAmount:30}]}));await quote
})

test('退款异步校验期间同原因的新数量和新报价也不能替代点击时的操作',async()=>{
  const page=refundPage();const first=page.calculateRefundAmount();page.requests[0].resolve(preview());await first
  page.formRef.value={validate:async callback=>{
    page.form.items[0].returnQuantity=2;const next=page.calculateRefundAmount()
    page.requests[1].resolve(preview({quoteId:'资金版本二',refundAmount:20,items:[{orderItemId:11,quantity:2,refundAmount:20}]}));await next
    return callback(true)
  },clearValidate(){}}
  await page.handleSubmit();assert.equal(page.submitted.length,0)
  formValidator(page.formRef);await page.handleSubmit();assert.equal(page.submitted.length,1)
  assert.equal(page.submitted[0].quoteId,'资金版本二');assert.equal(page.submitted[0].items[0].quantity,2)
})

test('退款异步校验期间换订单、关会话或换员工均不得提交另一个已就绪报价',async()=>{
  for(const action of ['order','close','actor']) {
    const user={userInfo:{id:8}};const page=refundPage({useUserStore:()=>user,
      getOrderDetail:async id=>({id,items:[{id:11,quantity:10,totalPrice:100,refundableQuantity:10,refundableAmount:100}]})})
    const first=page.calculateRefundAmount();page.requests[0].resolve(preview());await first
    page.formRef.value={validate:async callback=>{
      if(action==='order') {
        page.props.order={id:2,orderStatus:6,paymentMethod:6};await vue.nextTick();await tick()
        page.form.items[0].returnQuantity=3;page.form.reason='退货';const next=page.calculateRefundAmount()
        page.requests[1].resolve(preview({orderId:2,quoteId:'另单报价'}));await next
      } else if(action==='close') page.props.modelValue=false
      else user.userInfo.id=9
      return callback(true)
    },clearValidate(){}}
    await page.handleSubmit();assert.equal(page.submitted.length,0,action)
  }
})

test('售后审核异步校验不能把原售后审批带到新售后或新决定',async()=>{
  for(const action of ['row','decision','close']) {
    const submitted=[];const page=loadPage('src/views/order/after-sales/index.vue',[
      'openAuditDialog','submitAudit','auditFormRef','auditForm','auditDialogVisible'
    ],{auditAfterSales:async(id,body)=>submitted.push({id,body}),getAfterSalesPage:async()=>({records:[]})})
    page.openAuditDialog({id:8},true)
    page.auditFormRef.value={validate:async callback=>{
      if(action==='row') {page.auditDialogVisible.value=false;page.openAuditDialog({id:9},true)}
      else if(action==='decision') {page.auditForm.approved=false;page.auditForm.remark='新驳回决定'}
      else page.auditDialogVisible.value=false
      return callback(true)
    },clearValidate(){}}
    await page.submitAudit();await tick();assert.equal(submitted.length,0,action)
    page.openAuditDialog({id:9},true);formValidator(page.auditFormRef);await page.submitAudit();await tick()
    assert.equal(submitted.length,1);assert.equal(submitted[0].id,9)
  }
})

const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const ts = require('typescript')
const vue = require('vue')

const webRoot = path.resolve(__dirname, '..')

/**
 * 直接运行生产 Vue 脚本。仅替换页面生命周期、消息和网络边界，
 * 不重写报价、挂单、购物车更新或单位选择规则。
 */
function loadPage(relativePath, exports, overrides = {}) {
  const filename = path.join(webRoot, relativePath)
  const file = fs.readFileSync(filename, 'utf8')
  const match = file.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)
  assert.ok(match, `${relativePath} 应有 TypeScript setup 脚本`)
  const source = ts.createSourceFile(filename + '.ts', match[1], ts.ScriptTarget.Latest, true)
  const withoutImports = source.statements
    .filter(statement => !ts.isImportDeclaration(statement))
    .map(statement => statement.getFullText(source)).join('\n')
  const messages = []; const savedRecords = new Map()
  const context = {crypto:require("node:crypto").webcrypto,cancelPOSSubmission:async key=>({requestKey:key,status:"CANCELLED",orderId:null}),
    useRoute: () => ({query: {}}),
    ref: vue.ref,
    reactive: vue.reactive,
    computed: vue.computed,
    watch: () => {},
    onMounted: () => {},
    onUnmounted: () => {},
    nextTick: vue.nextTick,
    defineProps: () => ({ storeId: 1 }),
    defineEmits: () => () => {},
    defineExpose: () => {},
    useUserStore: () => ({ userInfo: { id: 1, extra: { storeId: 1 } } }),
    useRouter: () => ({push() {}}),
    sessionStorage: {getItem:k=>savedRecords.get(k)??null,setItem:(k,v)=>savedRecords.set(k,v),removeItem:k=>savedRecords.delete(k)},
    usePermissionStore: () => ({ permissions: ['*'] }),
    ElMessage: Object.fromEntries(['error', 'warning', 'success', 'info'].map(level => [
      level, message => messages.push({ level, text: typeof message === 'string' ? message : message?.message || '' })
    ])),
    console: { warn() {}, error() {} },
    ...overrides
  }
  vm.createContext(context)
  const output = ts.transpileModule(withoutImports + `\nglobalThis.page = { ${exports.join(', ')} };`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
  }).outputText
  vm.runInContext(output, context, { filename })
  return { ...context.page, messages }
}

function createPOS(overrides = {}) {
  const requests = []
  const page = loadPage('src/views/pos/index.vue', [
    'cartItems', 'selectedCustomer', 'checkoutVisible', 'hasPriceFallback',
    'handleCartUpdate', 'handleClearCart', 'handleRestoreCart', 'handleCheckout',
    'handleCustomerChange', 'handleUnitChange', 'handleAddToCart', 'handleSubmitOrder',
    'handlePackageQuoteChange', 'recalculateCartPrices'
  ], {
    fetchServerPriceQuote(params) {
      return new Promise((resolve, reject) => requests.push({ params, resolve, reject }))
    },
    fetchServerPackageQuote(params) {
      return new Promise((resolve, reject) => requests.push({ params, resolve, reject }))
    },
    resolvePackageUnitPrice: ({ packagePrice }) => packagePrice,
    ...overrides
  })
  return { ...page, requests }
}

function cartLine(overrides = {}) {
  return {
    id: 'line-1', itemType: 1, productId: 1, name: '测试农资', currentUnit: '瓶',
    quantity: 1, unitPrice: 10, subtotal: 10, suggestedUnitPrice: 10,
    quoteId: 'previous-quote', saleUnits: [{ unitName: '瓶' }, { unitName: '箱' }],
    ...overrides
  }
}

function finishQuote(request, price = 10, quoteId = 'latest-quote') {
  assert.ok(request, '应已发起报价请求')
  request.resolve({ quoteId, suggestedUnitPrice: price, referenceFloor: null, belowReference: false })
}

test('重新报价期间删除商品，不得把删除前的购物车恢复回来', async () => {
  const pos = createPOS()
  pos.cartItems.value = [cartLine()]
  const pending = pos.recalculateCartPrices()
  pos.handleCartUpdate([])
  finishQuote(pos.requests.shift())
  await pending
  assert.equal(pos.cartItems.value.length, 0)
})

test('重新报价期间清空购物车，不得重新出现旧商品', async () => {
  const pos = createPOS()
  pos.cartItems.value = [cartLine()]
  const pending = pos.recalculateCartPrices()
  pos.handleClearCart()
  finishQuote(pos.requests.shift())
  await pending
  assert.equal(pos.cartItems.value.length, 0)
  assert.equal(pos.selectedCustomer.value, null)
})

test('添加商品的报价尚未返回时清空购物车，旧添加请求不得把商品放回来', async () => {
  const pos = createPOS()
  const pending = pos.handleAddToCart(cartLine({ id: '' }))
  pos.handleClearCart()
  finishQuote(pos.requests.shift())
  await pending
  assert.equal(pos.cartItems.value.length, 0)
})

test('恢复挂单时标准报价未变，保留人工成交价和议价原因', async () => {
  const pos = createPOS()
  const pending = pos.handleRestoreCart({
    cartItems: [cartLine({ unitPrice: 8, subtotal: 8, priceChangeReason: '老客户优惠' })],
    customer: null, discountAmount: 0
  })
  finishQuote(pos.requests.shift(), 10)
  await pending
  assert.equal(pos.cartItems.value[0].unitPrice, 8)
  assert.equal(pos.cartItems.value[0].subtotal, 8)
  assert.equal(pos.cartItems.value[0].priceChangeReason, '老客户优惠')
  assert.equal(pos.cartItems.value[0].suggestedUnitPrice, 10)
  assert.equal(pos.hasPriceFallback.value, false)
})

test('恢复挂单时标准报价已变，重置旧议价并明确提示收银员', async () => {
  const pos = createPOS()
  const pending = pos.handleRestoreCart({
    cartItems: [cartLine({ unitPrice: 8, subtotal: 8, priceChangeReason: '老客户优惠' })],
    customer: null, discountAmount: 0
  })
  finishQuote(pos.requests.shift(), 12)
  await pending
  assert.equal(pos.cartItems.value[0].unitPrice, 12)
  assert.equal(pos.cartItems.value[0].subtotal, 12)
  assert.equal(pos.cartItems.value[0].priceChangeReason?.trim() || '', '')
  assert.ok(pos.messages.some(message => ['warning', 'info'].includes(message.level) && /报价|价格|议价/.test(message.text)),
    '旧议价被重置时应明确通知收银员')
})

test('商品报价失败必须阻止结算，成功重试后才可以收款', async () => {
  const pos = createPOS()
  pos.cartItems.value = [cartLine()]
  const failed = pos.recalculateCartPrices()
  pos.requests.shift().reject(new Error('报价服务不可用'))
  await failed
  pos.handleCheckout({ paymentType: 1, discountAmount: 0 })
  assert.equal(pos.hasPriceFallback.value, true)
  assert.equal(pos.checkoutVisible.value, false)
  const retry = pos.recalculateCartPrices()
  finishQuote(pos.requests.shift())
  await retry
  pos.handleCheckout({ paymentType: 1, discountAmount: 0 })
  assert.equal(pos.hasPriceFallback.value, false)
  assert.equal(pos.checkoutVisible.value, true)
})

test('快速切换销售单位时，先发后回的旧报价不能覆盖新单位价格', async () => {
  const pos = createPOS()
  pos.cartItems.value = [cartLine()]
  const item = pos.cartItems.value[0]
  item.currentUnit = '箱'
  const older = pos.handleUnitChange({ id: item.id, oldUnit: '瓶', newUnit: '箱' })
  const boxRequest = pos.requests.shift()
  item.currentUnit = '瓶'
  const newer = pos.handleUnitChange({ id: item.id, oldUnit: '箱', newUnit: '瓶' })
  const bottleRequest = pos.requests.shift()
  assert.equal(boxRequest.params.saleUnit, '箱')
  assert.equal(bottleRequest.params.saleUnit, '瓶')
  finishQuote(bottleRequest, 10, 'bottle-quote')
  await newer
  finishQuote(boxRequest, 30, 'box-quote')
  await older
  assert.equal(pos.cartItems.value[0].currentUnit, '瓶')
  assert.equal(pos.cartItems.value[0].unitPrice, 10)
  assert.equal(pos.cartItems.value[0].quoteId, 'bottle-quote')
  assert.equal(pos.hasPriceFallback.value, false)
})

test('快速切换客户时，旧会员报价不能覆盖最新客户报价', async () => {
  const pos = createPOS()
  pos.cartItems.value = [cartLine()]
  const older = pos.handleCustomerChange({ id: 2, name: '会员', type: 2 })
  const memberRequest = pos.requests.shift()
  const newer = pos.handleCustomerChange({ id: 3, name: '普通客户', type: 1 })
  const normalRequest = pos.requests.shift()
  assert.equal(memberRequest.params.customerId, 2)
  assert.equal(normalRequest.params.customerId, 3)
  finishQuote(normalRequest, 10, 'normal-quote')
  await newer
  finishQuote(memberRequest, 8, 'member-quote')
  await older
  assert.equal(pos.selectedCustomer.value.id, 3)
  assert.equal(pos.cartItems.value[0].unitPrice, 10)
  assert.equal(pos.cartItems.value[0].quoteId, 'normal-quote')
  assert.equal(pos.hasPriceFallback.value, false)
})

test('第三单位是唯一可销售单位时，点击商品应选中该单位', () => {
  const emissions = []
  const page = loadPage('src/views/pos/components/ModernGoodsPanel.vue', ['handleCardClick'], {
    defineEmits: () => (event, value) => emissions.push({ event, value })
  })
  page.handleCardClick({
    id: 1, type: 'product', price: 10, stock: 5,
    product: {
      id: 1, name: '测试农资', mainUnit: '箱', auxUnit: '瓶', pricingUnit: '瓶',
      suggestedRetailPrice: 10,
      units: [
        { unitName: '瓶', status: 1, allowSale: 0, isBaseUnit: 1 },
        { unitName: '箱', status: 1, allowSale: 0, isBaseUnit: 0 },
        { unitName: '件', status: 1, allowSale: 1, isBaseUnit: 0 }
      ]
    }
  })
  const added = emissions.find(emission => emission.event === 'addToCart')
  assert.ok(added)
  assert.equal(added.value.currentUnit, '件')
  assert.deepEqual(Array.from(added.value.saleUnits, unit => unit.unitName), ['件'])
})

test('没有可销售单位的商品不得加入购物车，并应提示维护单位', () => {
  const emissions = []
  const page = loadPage('src/views/pos/components/ModernGoodsPanel.vue', ['handleCardClick'], {
    defineEmits: () => (event, value) => emissions.push({ event, value })
  })
  page.handleCardClick({
    id: 1, type: 'product', price: 10, stock: 5,
    product: { id: 1, mainUnit: '箱', units: [{ unitName: '箱', status: 1, allowSale: 0 }] }
  })
  assert.equal(emissions.some(emission => emission.event === 'addToCart'), false)
  assert.ok(page.messages.some(message => /销售单位|商品单位/.test(message.text)))
})

test('没有议价权限的员工不能修改成交单价', () => {
  const item = cartLine()
  const page = loadPage('src/views/pos/components/ModernCartList.vue', ['handlePriceChange'], {
    usePermissionStore: () => ({ permissions: ['order:create'] }),
    defineProps: () => ({ cartItems: [item], storeId: 1 })
  })
  page.handlePriceChange(item, 8)
  assert.equal(item.unitPrice, 10)
  assert.equal(item.subtotal, 10)
})

test('有议价权限的员工可改价，低于最低价时保留提醒', () => {
  const item = cartLine({ quantity: 3, subtotal: 30, referenceFloor: 9 })
  const page = loadPage('src/views/pos/components/ModernCartList.vue', ['handlePriceChange'], {
    usePermissionStore: () => ({ permissions: ['order:price:override'] }),
    defineProps: () => ({ cartItems: [item], storeId: 1 })
  })
  page.handlePriceChange(item, 8)
  assert.equal(item.unitPrice, 8)
  assert.equal(item.subtotal, 24)
  assert.equal(item.belowReference, true)
})

test('人工议价缺少原因不能结算，填写原因后可以打开结算', () => {
  const pos = createPOS()
  pos.cartItems.value = [cartLine({ unitPrice: 8, subtotal: 8 })]
  pos.handleCheckout({ paymentType: 1, discountAmount: 0 })
  assert.equal(pos.checkoutVisible.value, false)
  pos.cartItems.value[0].priceChangeReason = '老客户优惠'
  pos.handleCheckout({ paymentType: 1, discountAmount: 0 })
  assert.equal(pos.checkoutVisible.value, true)
})

test('整单优惠未填写原因仍可以取消结算返回购物车', () => {
  const file = fs.readFileSync(path.join(webRoot, 'src/views/pos/components/CheckoutDialog.vue'), 'utf8')
  const handler = file.match(/const handleClose = \(\) => \{[\s\S]*?\n\}/)
  assert.ok(handler)
  const context = {crypto:require("node:crypto").webcrypto,cancelPOSSubmission:async key=>({requestKey:key,status:"CANCELLED",orderId:null}),
    props: { submitting: false, discountAmount: 9 },
    confirmForm: vue.ref({ remark: '' }), visible: vue.ref(true),
    ElMessage: { warning() {} }
  }
  vm.createContext(context)
  vm.runInContext(handler[0] + '\nhandleClose()', context)
  assert.equal(context.visible.value, false)
})

function packageLine(overrides = {}) {
  return {
    id: 'package-1', itemType: 2, packageId: 10, name: '春耕组合', currentUnit: '套',
    quantity: 1, unitPrice: 80, subtotal: 80, suggestedUnitPrice: 80,
    quoteId: 'package-version-1', packageItems: [], specialFundEligibleAmount: 40,
    ...overrides
  }
}

function finishPackageQuote(request, overrides = {}) {
  assert.ok(request, '套餐必须发起后端报价请求')
  const quantity = request.params.quantity || 1
  const price = request.params.dealUnitPrice ?? 80
  request.resolve({
    quoteId: 'package-version-1', packageId: 10, packageName: '春耕组合', quantity,
    suggestedUnitPrice: 80, referenceFloor: null, belowReference: false,
    dealUnitPrice: price, totalAmount: price * quantity, specialFundEligibleAmount: price * quantity / 2,
    items: [{ productId: 1, productName: '肥料', quantity: 2 * quantity, unit: '袋',
      saleUnitId: 1, unitPrice: price / 4, totalPrice: price * quantity / 2, allowSpecialFund: 1 }],
    ...overrides
  })
}

test('点击套餐只创建一行套餐，提交套数而不由前端拆成商品', () => {
  const emissions = []
  const page = loadPage('src/views/pos/components/ModernGoodsPanel.vue', ['handleCardClick'], {
    defineEmits: () => (event, value) => emissions.push({ event, value })
  })
  page.handleCardClick({ id: 10, type: 'package', price: 80, stock: 5,
    package: { id: 10, name: '春耕组合', suggestedRetailPrice: 80,
      items: [{ productId: 1, productName: '肥料', quantity: 2, unit: '袋' }] } })
  const added = emissions.filter(event => event.event === 'addToCart')
  assert.equal(added.length, 1)
  assert.equal(added[0].value.itemType, 2)
  assert.equal(added[0].value.packageId, 10)
  assert.equal(added[0].value.productId, undefined)
  assert.equal(added[0].value.quantity, 1)
  assert.equal(added[0].value.currentUnit, '套')
})

test('套餐套数变更后，金额、物料和专用金均使用新的后端报价', async () => {
  const pos = createPOS()
  pos.cartItems.value = [packageLine({ quantity: 2 })]
  const pending = pos.recalculateCartPrices(true)
  const request = pos.requests.shift()
  assert.ok(request, '套餐应从后端重新报价')
  assert.equal(request.params.packageId, 10)
  assert.equal(request.params.quantity, 2)
  finishPackageQuote(request)
  await pending
  assert.equal(pos.cartItems.value[0].subtotal, 160)
  assert.equal(pos.cartItems.value[0].packageItems[0].quantity, 4)
  assert.equal(pos.cartItems.value[0].specialFundEligibleAmount, 80)
  pos.handleCheckout({ paymentType: 1, discountAmount: 0 })
  assert.equal(pos.checkoutVisible.value, true)
})

test('套餐报价失败阻止结算，不得采用旧套餐本地价格', async () => {
  const pos = createPOS()
  pos.cartItems.value = [packageLine({ quoteId: null })]
  const pending = pos.recalculateCartPrices(true)
  assert.equal(pos.hasPriceFallback.value, true)
  const request = pos.requests.shift()
  assert.ok(request)
  request.reject(new Error('套餐报价服务不可用'))
  await pending
  pos.handleCheckout({ paymentType: 1, discountAmount: 0 })
  assert.equal(pos.checkoutVisible.value, false)
  assert.equal(pos.cartItems.value[0].priceFallback, true)
})

test('恢复同构成同标准价的套餐挂单，保留每套成交价与原因', async () => {
  const pos = createPOS()
  const pending = pos.handleRestoreCart({
    cartItems: [packageLine({ quantity: 2, unitPrice: 70, subtotal: 140, priceChangeReason: '老客户组合优惠' })],
    customer: null, discountAmount: 0
  })
  const request = pos.requests.shift()
  assert.ok(request)
  assert.equal(request.params.dealUnitPrice, 70)
  finishPackageQuote(request)
  await pending
  assert.equal(pos.cartItems.value[0].unitPrice, 70)
  assert.equal(pos.cartItems.value[0].subtotal, 140)
  assert.equal(pos.cartItems.value[0].priceChangeReason, '老客户组合优惠')
  assert.equal(pos.cartItems.value[0].specialFundEligibleAmount, 70)
})

test('套餐挂单组成改变但标准价未变，也必须清除旧议价并重新取标准金额', async () => {
  const pos = createPOS()
  const pending = pos.handleRestoreCart({
    cartItems: [packageLine({ unitPrice: 70, subtotal: 70, priceChangeReason: '原组合议价' })],
    customer: null, discountAmount: 0
  })
  finishPackageQuote(pos.requests.shift(), { quoteId: 'package-version-2' })
  await new Promise(resolve => setImmediate(resolve))
  const standardRequest = pos.requests.shift()
  assert.ok(standardRequest, '组成改变后应再次查询无议价标准预览')
  assert.equal(standardRequest.params.dealUnitPrice, undefined)
  finishPackageQuote(standardRequest, { quoteId: 'package-version-2' })
  await pending
  assert.equal(pos.cartItems.value[0].unitPrice, 80)
  assert.equal(pos.cartItems.value[0].subtotal, 80)
  assert.equal(pos.cartItems.value[0].priceChangeReason?.trim() || '', '')
  assert.ok(pos.messages.some(message => message.level === 'warning' && /组成|报价|议价/.test(message.text)))
})

test('编辑独立套餐价，加载单品资料不得覆盖已保存每套价格', async () => {
  const page = loadPage('src/views/product/package/index.vue', ['handleEdit', 'formData'], {
    getPackageDetail: async () => ({ id: 10, name: '春耕组合', suggestedRetailPrice: 80,
      items: [{ productId: 1, productName: '肥料', quantity: 2, unit: '袋' }] }),
    getProductDetail: async () => ({ id: 1, name: '肥料', mainUnit: '袋', suggestedRetailPrice: 50,
      units: [{ unitName: '袋', status: 1, allowSale: 1, isBaseUnit: 1 }] })
  })
  await page.handleEdit({ id: 10 })
  assert.equal(page.formData.suggestedRetailPrice, 80)
})

test('套餐物料退完时采用后端含尾差的冲销金额', async () => {
  const page = loadPage('src/views/order/components/RefundDialog.vue', ['form', 'calculateRefundAmount'], {
    defineProps: () => ({ modelValue: true, order: { id: 1, orderStatus: 5 } }),
    getRefundPreview: async (_id, data) => ({quoteId: '服务端尾差预览', orderId: 1, refundAmount: data.items[0].quantity === 3 ? 1 : 0.34,
      items: [{...data.items[0], refundAmount: data.items[0].quantity === 3 ? 1 : 0.34}], creditReductionAmount: 0, cashRefundAmount: data.items[0].quantity === 3 ? 1 : 0.34})
  })
  page.form.items = [{ orderItemId: 11, productName: '套餐物料', quantity: 3,
    refundedQuantity: 0, refundableQuantity: 3, unit: '袋', unitPrice: 0.33,
    refundedAmount: 0, refundableAmount: 1, totalPrice: 1, packageGroupNo: 1,
    returnQuantity: 3, refundAmount: 0 }]
  await page.calculateRefundAmount()
  assert.equal(page.form.items[0].refundAmount, 1)
  assert.equal(page.form.refundAmount, 1)
})

test('套餐物料部分退货采用后端累计份额，保留尾差', async () => {
  const page = loadPage('src/views/order/components/RefundDialog.vue', ['form', 'calculateRefundAmount'], {
    defineProps: () => ({ modelValue: true, order: { id: 1, orderStatus: 5 } }),
    getRefundPreview: async (_id, data) => ({quoteId: '服务端尾差预览', orderId: 1, refundAmount: data.items[0].quantity === 3 ? 1 : 0.34,
      items: [{...data.items[0], refundAmount: data.items[0].quantity === 3 ? 1 : 0.34}], creditReductionAmount: 0, cashRefundAmount: data.items[0].quantity === 3 ? 1 : 0.34})
  })
  page.form.items = [{ orderItemId: 11, productName: '套餐物料', quantity: 3,
    refundedQuantity: 1, refundableQuantity: 2, unit: '袋', unitPrice: 0.33,
    refundedAmount: 0.33, refundableAmount: 0.67, totalPrice: 1, packageGroupNo: 1,
    returnQuantity: 1, refundAmount: 0 }]
  await page.calculateRefundAmount()
  assert.equal(page.form.items[0].refundAmount, 0.34)
})

test('套餐议价预览期间新填写的原因不能被报价响应清掉', async () => {
  const pos = createPOS()
  pos.cartItems.value = [packageLine({ unitPrice: 70, subtotal: 70 })]
  const pending = pos.recalculateCartPrices(true)
  pos.cartItems.value[0].priceChangeReason = '现金购买优惠'
  finishPackageQuote(pos.requests.shift())
  await pending
  assert.equal(pos.cartItems.value[0].unitPrice, 70)
  assert.equal(pos.cartItems.value[0].priceChangeReason, '现金购买优惠')
})

test('同套餐新增标准价购买，不得合并进已经议价的购物车行', async () => {
  const pos = createPOS()
  pos.cartItems.value = [packageLine({ unitPrice: 70, subtotal: 70, priceChangeReason: '老客户优惠' })]
  const pending = pos.handleAddToCart(packageLine({ id: '', quoteId: null, suggestedUnitPrice: undefined }))
  finishPackageQuote(pos.requests.shift())
  await pending
  assert.equal(pos.cartItems.value.length, 2)
  assert.equal(pos.cartItems.value[0].unitPrice, 70)
  assert.equal(pos.cartItems.value[0].quantity, 1)
  assert.equal(pos.cartItems.value[1].unitPrice, 80)
  assert.equal(pos.cartItems.value[1].quantity, 1)
})

test('套餐物料和专用金报价乱序时，只保留最新套数对应的数据', async () => {
  const pos = createPOS()
  pos.cartItems.value = [packageLine({ quantity: 2 })]
  const older = pos.recalculateCartPrices(true)
  const oldRequest = pos.requests.shift()
  pos.cartItems.value[0].quantity = 3
  const newer = pos.recalculateCartPrices(true)
  const newRequest = pos.requests.shift()
  finishPackageQuote(newRequest)
  await newer
  finishPackageQuote(oldRequest)
  await older
  assert.equal(pos.cartItems.value[0].quantity, 3)
  assert.equal(pos.cartItems.value[0].subtotal, 240)
  assert.equal(pos.cartItems.value[0].packageItems[0].quantity, 6)
  assert.equal(pos.cartItems.value[0].specialFundEligibleAmount, 120)
})

test('套餐结算只提交一行套餐和报价版本，不提交物料拆分或标准价改价', () => {
  const submissions = []
  const page = loadPage('src/views/pos/components/CheckoutDialog.vue', ['handleConfirm', 'specialFundEligibleAmount'], {
    defineProps: () => ({ modelValue: true, submitting: false, totalAmount: 160, discountAmount: 0,
      checkoutData: { paymentType: 1 }, customer: { id: 2, balance: 0, specialFund: 100 },
      cartItems: [packageLine({ quantity: 2, subtotal: 160, specialFundEligibleAmount: 60 })] }),
    defineEmits: () => (event, value) => { if (event === 'submit') submissions.push(value) }
  })
  assert.equal(page.specialFundEligibleAmount.value, 60)
  page.handleConfirm()
  assert.equal(submissions.length, 1)
  assert.equal(submissions[0].items.length, 1)
  const item = submissions[0].items[0]
  assert.equal(item.itemType, 2)
  assert.equal(item.packageId, 10)
  assert.equal(item.productId, undefined)
  assert.equal(item.quantity, 2)
  assert.equal(item.unit, '套')
  assert.equal(item.unitPrice, undefined)
  assert.equal(item.quotedUnitPrice, 80)
  assert.equal(item.packageQuoteId, 'package-version-1')
})

test('结算发现套餐价格或组成已变时，返回购物车并刷新报价供重新确认', async () => {
  const pos = createPOS({ createPOSOrder: async () => { throw Object.assign(new Error('套餐价格或组成已变化，请重新报价后结算'), {apiBusinessCode:10001}) } })
  pos.cartItems.value = [packageLine({packageItems:[{productId:1,quantity:2,unit:'袋',totalPrice:80}]})]
  pos.checkoutVisible.value = true
  const pending = pos.handleSubmitOrder({ items: [{ itemType: 2, packageId: 10, quantity: 1 }] })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(pos.checkoutVisible.value, false)
  finishPackageQuote(pos.requests.shift(), { quoteId: 'package-version-2' })
  await pending
  assert.equal(pos.cartItems.value.length, 1)
  assert.equal(pos.cartItems.value[0].quoteId, 'package-version-2')
})

test('套餐数量变化事件必须触发后端预览，预览失败立即阻止收款', async () => {
  const pos = createPOS()
  pos.cartItems.value = [packageLine()]
  const pending = []
  const cart = loadPage('src/views/pos/components/ModernCartList.vue', ['handleQuantityChange'], {
    defineProps: () => ({ cartItems: pos.cartItems.value, storeId: 1 }),
    defineEmits: () => (event, payload) => {
      if (event === 'packageChange') pending.push(pos.handlePackageQuoteChange(payload))
      if (event === 'update') pos.handleCartUpdate(payload)
    }
  })
  cart.handleQuantityChange(pos.cartItems.value[0], 2)
  assert.equal(pos.hasPriceFallback.value, true)
  assert.equal(pos.requests[0].params.quantity, 2)
  pos.requests.shift().reject(new Error('报价服务不可用'))
  await Promise.all(pending)
  pos.handleCheckout({ paymentType: 1, discountAmount: 0 })
  assert.equal(pos.checkoutVisible.value, false)
})

test('套餐含整单优惠时，物料分次退款使用后端净份额0.90元，不退回原成交1元', async () => {
  const page = loadPage('src/views/order/components/RefundDialog.vue', ['form', 'calculateRefundAmount'], {
    defineProps: () => ({ modelValue: true, order: { id: 1, orderStatus: 5 } }),
    getRefundPreview: async (_id, data) => ({quoteId: '净份额预览', orderId: 1, refundAmount: 0.3, creditReductionAmount: 0, cashRefundAmount: 0.3,
      items: [{...data.items[0], refundAmount: 0.3}]})
  })
  let total = 0
  for (let returned = 0; returned < 3; returned++) {
    page.form.items = [{ orderItemId: 11, productName: '优惠套餐物料', quantity: 3,
      refundedQuantity: returned, refundableQuantity: 3 - returned, unit: '袋', unitPrice: 0.333333333333,
      refundedAmount: Math.round(returned * 0.3 * 100) / 100,
      refundableAmount: Math.round((0.9 - returned * 0.3) * 100) / 100,
      totalPrice: 1, packageGroupNo: 1, returnQuantity: 1, refundAmount: 0 }]
    await page.calculateRefundAmount()
    assert.equal(page.form.items[0].refundAmount, 0.3)
    total += page.form.refundAmount
  }
  assert.equal(Math.round(total * 100) / 100, 0.9)
})

test('现金已退完的状态8订单，可选择剩余零金额物料退货，不重复选择已退数量', async () => {
  const submissions = []
  const page = loadPage('src/views/order/components/RefundDialog.vue', [
    'form', 'formRef', 'rules', 'loadOrderDetail', 'calculateRefundAmount', 'handleSubmit'
  ], {
    defineProps: () => ({ modelValue: true, order: { id: 1, orderStatus: 8 } }),
    getRefundPreview: async (_id, data) => ({quoteId: '零金额退货', orderId: 1, refundAmount: 0, creditReductionAmount: 0, cashRefundAmount: 0,
      items: [{...data.items[0], refundAmount: 0}]}),
    getOrderDetail: async () => ({ id: 1, orderStatus: 8, items: [
      { id: 11, productName: '未退赠品', quantity: 1, refundedQuantity: 0, refundableQuantity: 1,
        unit: '袋', unitPrice: 0, totalPrice: 0, refundedAmount: 0, refundableAmount: 0, packageGroupNo: 1 },
      { id: 12, productName: '已退赠品', quantity: 1, refundedQuantity: 1, refundableQuantity: 0,
        unit: '袋', unitPrice: 0, totalPrice: 0, refundedAmount: 0, refundableAmount: 0, packageGroupNo: 1 }
    ] }),
    createAfterSales: async payload => submissions.push(payload)
  })
  await page.loadOrderDetail()
  assert.equal(page.form.items.length, 1)
  assert.equal(page.form.items[0].orderItemId, 11)
  page.form.items[0].returnQuantity = 1
  page.form.reason = '赠品一并退回'
  await page.calculateRefundAmount()
  page.formRef.value = { validate: async callback => callback(true), clearValidate() {} }
  await page.handleSubmit()
  assert.equal(submissions.length, 1)
  assert.equal(submissions[0].refundAmount, 0)
  assert.equal(submissions[0].items.length, 1)
  assert.equal(submissions[0].items[0].orderItemId, 11)
  assert.equal(submissions[0].items[0].quantity, 1)
  assert.equal(submissions[0].items[0].refundAmount, 0)
})

test('状态8退货入口使用整单剩余物料，不从前三条概览或未知数据猜测', () => {
  const page = loadPage('src/views/order/list/index.vue', ['canRefund', 'getRefundActionText'])
  assert.equal(page.canRefund({orderStatus:8,hasReturnableItems:true,items:[{quantity:1,refundableQuantity:0,refundableAmount:0}]}),true)
  assert.equal(page.canRefund({orderStatus:8,hasReturnableItems:false,items:[{quantity:1}]}),false)
  assert.equal(page.canRefund({orderStatus:8,items:[{quantity:1}]}),false)
  assert.equal(page.getRefundActionText({orderStatus:8}), '继续退货')
})

test('套餐物料3件净可退1分，逐件退货允许零现金并累计只退1分', async () => {
  const submissions = []
  const order = { id: 1, orderStatus: 6 }
  let returnedQuantity = 0
  let refundedAmount = 0
  const page = loadPage('src/views/order/components/RefundDialog.vue', [
    'form', 'formRef', 'rules', 'loadOrderDetail', 'calculateRefundAmount', 'handleSubmit'
  ], {
    defineProps: () => ({ modelValue: true, order }),
    getRefundPreview: async (_id, data) => ({quoteId: '分次净份额-' + returnedQuantity, orderId: 1, refundAmount: [0, 0.01, 0][returnedQuantity],
      creditReductionAmount: 0, cashRefundAmount: [0, 0.01, 0][returnedQuantity], items: [{...data.items[0], refundAmount: [0, 0.01, 0][returnedQuantity]}]}),
    getOrderDetail: async () => ({ id: 1, orderStatus: order.orderStatus, items: [
      { id: 11, productName: '套餐低金额物料', quantity: 3, refundedQuantity: returnedQuantity,
        refundableQuantity: 3 - returnedQuantity, unit: '袋', unitPrice: 0.003333333333,
        totalPrice: 0.01, refundedAmount,
        refundableAmount: Math.round((0.01 - refundedAmount) * 100) / 100, packageGroupNo: 1 }
    ] }),
    createAfterSales: async payload => submissions.push(payload)
  })

  for (const expectedRefund of [0, 0.01, 0]) {
    await page.loadOrderDetail()
    assert.equal(page.form.items.length, 1)
    page.form.items[0].returnQuantity = 1
    page.form.reason = '套餐物料逐件退回'
    await page.calculateRefundAmount()
    assert.equal(page.form.refundAmount, expectedRefund)
    page.formRef.value = { validate: async callback => callback(true), clearValidate() {} }
    await page.handleSubmit()
    const submission = submissions.at(-1)
    assert.equal(submission.refundAmount, expectedRefund)
    assert.equal(submission.items[0].orderItemId, 11)
    assert.equal(submission.items[0].quantity, 1)
    assert.equal(submission.items[0].refundAmount, expectedRefund)
    returnedQuantity += 1
    refundedAmount = Math.round((refundedAmount + expectedRefund) * 100) / 100
    if (refundedAmount === 0.01) order.orderStatus = 8
  }

  assert.equal(submissions.length, 3)
  assert.equal(returnedQuantity, 3)
  assert.equal(refundedAmount, 0.01)
  await page.loadOrderDetail()
  assert.equal(page.form.items.length, 0)
})
test('手工四位数量后连续扫码，同商品合并仍为准确四位数量',async()=>{
  const pos=createPOS();pos.cartItems.value=[cartLine({quantity:1.0001,subtotal:10})]
  const pending=pos.handleAddToCart(cartLine({id:''}));finishQuote(pos.requests.shift());await pending
  assert.equal(pos.cartItems.value.length,1);assert.equal(pos.cartItems.value[0].quantity,2.0001)
})

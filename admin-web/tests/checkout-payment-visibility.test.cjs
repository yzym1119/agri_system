const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vue = require('vue')
const { parse } = require('@vue/compiler-sfc')
const { baseParse, compile } = require('@vue/compiler-dom')

// 执行真实模板的相邻条件块，防止钱包预览条件意外控制混合支付区。
const template = parse(fs.readFileSync(path.join(__dirname,
  '../src/views/pos/components/CheckoutDialog.vue'), 'utf8')).descriptor.template.content
let wallet, mixed
function find(node) {
  const css = node.props?.find(p => p.name === 'class')?.value?.content
  if (css === 'wallet-preview-card' && !wallet) wallet = node
  if (css === 'mixed-panel') mixed = node
  node.children?.forEach(find)
}
find(baseParse(template))
const fragment = template.slice(wallet.loc.start.offset, mixed.loc.end.offset)
const render = new Function('Vue', compile(fragment, { mode: 'function' }).code)(
  { ...vue, resolveComponent: name => ({ name }) })

function visibleClasses(paymentType, shouldShowWalletPreview) {
  const found = []
  function walk(node) {
    if (!node) return
    if (Array.isArray(node)) return node.forEach(walk)
    if (node.props?.class) found.push(node.props.class)
    if (Array.isArray(node.children)) node.children.forEach(walk)
  }
  walk(render({ paymentType, shouldShowWalletPreview,
    walletPaymentPreview: { overflow: 0, specialFund: 0, balance: 0 }, walletPayableLimit: 0,
    mixedOptions: [], needsAuthCodeInMixed: false, progressPercentage: 0,
    remainingAmount: 50, allocatedAmount: 0 }, []))
  return found
}

for (const mode of [1, 2, 3, 4, 6, 10]) for (const walletPreview of [false, true]) {
  test(`支付方式${mode}、钱包预览${walletPreview}时仅混合支付显示分配区`, () => {
    const classes = visibleClasses(mode, walletPreview)
    assert.equal(classes.some(c => c.split(' ').includes('mixed-panel')), mode === 10)
  })
}

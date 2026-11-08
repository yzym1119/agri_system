<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { staffAuth } from '../../services/auth';
import { readCatalog, searchCatalog, quoteProduct, submitSale } from '../../services/sale';
import { searchSaleCustomers, type SaleCustomer } from '../../services/customers';
import { posRecovery } from '../../services/pos-recovery';
import { lineCents, type CatalogProduct, type SaleLine } from '@agri/mobile-contracts/sale';

const products = ref<CatalogProduct[]>([]), lines = ref<SaleLine[]>([]), keyword = ref('');
const searchResults = ref<CatalogProduct[]>([]);
const searching = ref(false);
const selected = ref<CatalogProduct | null>(null), quantity = ref('1');
const paymentType = ref<1 | 6>(1), customerId = ref(''), deliveryType = ref<1 | 2>(1);
const receiverName = ref(''), receiverPhone = ref(''), receiverAddress = ref(''), remark = ref('');
const loading = ref(true), busy = ref(false), allowed = ref(false), blocked = ref(false);
const message = ref(''), orderId = ref(''), epoch = ref(-1);
let generation = 0, visible = false;
/** 购物车现有明细的报价所依据的客户编号；客户变更后必须重新拉取全部报价。 */
let quotedUnderCustomer = '';
const requoting = ref(false);
const cents = (value: number) => (value / 100).toFixed(2);
const totalCents = computed(() => lines.value.reduce((sum, line) => sum + lineCents(line), 0));
/** 有关键词时展示服务端搜索结果，无关键词时展示完整目录首页。 */
const filtered = computed(() => (keyword.value.trim() ? searchResults.value : products.value));
const current = (ticket = generation) => visible && allowed.value && ticket === generation && staffAuth.current()?.epoch === epoch.value;

const stage = ref<'goods' | 'settlement'>('goods');
const canQueryCustomers = ref(false), customerKeyword = ref(''), customerBusy = ref(false);
const customerResults = ref<SaleCustomer[]>([]), customerMessage = ref('');
const chosenCustomer = ref<SaleCustomer | null>(null);
let customerSeq = 0;
watch(customerKeyword, () => { customerSeq++; customerResults.value = []; customerBusy.value = false; customerMessage.value = ''; }, { flush:'sync' });
async function findCustomers() {
  if (!current() || busy.value || blocked.value || !canQueryCustomers.value) return;
  const query = customerKeyword.value.trim();
  if (!query || query.length > 64) { customerMessage.value = '请输入姓名或手机号，最多64字。'; return; }
  const seq = ++customerSeq, ticket = generation;
  customerBusy.value = true; customerResults.value = []; customerMessage.value = '';
  try {
    const results = await searchSaleCustomers(query, epoch.value);
    if (!current(ticket) || seq !== customerSeq) return;
    customerResults.value = results;
    customerMessage.value = results.length ? '请核对姓名和手机号后选择；最多显示20人，可补全关键词缩小范围。' : '没有匹配的在用客户，请核对关键词。';
  } catch { if (current(ticket) && seq === customerSeq) customerMessage.value = '客户查询未完成，请重试或联系老板核对权限。'; }
  finally { if (seq === customerSeq) customerBusy.value = false; }
}
function selectCustomer(customer: SaleCustomer) {
  if (!current() || busy.value || blocked.value || !canQueryCustomers.value || !customerResults.value.includes(customer)) return;
  chosenCustomer.value = { ...customer }; customerId.value = customer.id;
  customerSeq++; customerResults.value = []; customerMessage.value = ''; customerBusy.value = false;
}
function clearCustomer() {
  if (!current() || busy.value || blocked.value) return;
  chosenCustomer.value = null; customerId.value = ''; customerSeq++; customerResults.value = []; customerBusy.value = false;
}
function openSettlement() {
  if (!current() || busy.value || requoting.value || blocked.value || !lines.value.length) return;
  stage.value = 'settlement'; selected.value = null;
}
async function reload() {
  if (!current() || busy.value) return;
  const ticket = ++generation;
  loading.value = true; message.value = '';
  try {
    const list = await readCatalog(epoch.value);
    if (!current(ticket)) return;
    products.value = list;
    if (!list.length) message.value = '本店暂无可销售商品，请先在后台建立商品档案。';
  } catch {
    if (current(ticket)) message.value = '商品目录读取失败，请重新进入本页或检查网络。';
  } finally { if (ticket === generation) loading.value = false; }
}

/** 服务端搜索（防抖）；商品超过目录首页 200 条时仍可通过编码/名称/条码找到。 */
let searchSeq = 0, searchTimer: ReturnType<typeof setTimeout> | null = null;
function scheduleSearch() {
  if (searchTimer) clearTimeout(searchTimer);
  const query = keyword.value.trim();
  if (!query) { searchResults.value = []; searchSeq++; searching.value = false;
    if (!products.value.length) message.value = ''; return; }
  searchTimer = setTimeout(() => { void runSearch(query); }, 300);
}
async function runSearch(query: string) {
  if (!current() || busy.value) return;
  const seq = ++searchSeq;
  searching.value = true;
  try {
    const list = await searchCatalog(query, epoch.value);
    if (!current() || seq !== searchSeq) return;
    searchResults.value = list;
    if (!list.length) message.value = '没有匹配的商品，可尝试编码、名称或条码搜索。';
  } catch {
    if (current() && seq === searchSeq) message.value = '商品搜索失败，请检查网络后重试。';
  } finally { if (seq === searchSeq) searching.value = false; }
}
watch(keyword, () => { if (current()) scheduleSearch(); });

onShow(async () => {
  visible = true; const ticket = ++generation;
  stage.value = 'goods'; canQueryCustomers.value = false; chosenCustomer.value = null;
  customerKeyword.value = ''; customerResults.value = []; customerMessage.value = ''; customerBusy.value = false; customerSeq++;
  loading.value = true; busy.value = false; allowed.value = false; blocked.value = false;
  products.value = []; searchResults.value = []; lines.value = []; selected.value = null; keyword.value = '';
  if (searchTimer) { clearTimeout(searchTimer); searchTimer = null; } searchSeq++; searching.value = false;
  paymentType.value = 1; customerId.value = ''; deliveryType.value = 1;
  quotedUnderCustomer = ''; requoting.value = false; requoteSeq++;
  receiverName.value = ''; receiverPhone.value = ''; receiverAddress.value = ''; remark.value = '';
  message.value = ''; orderId.value = '';
  try {
    await staffAuth.refresh(); if (!visible || ticket !== generation) return;
    const actor = staffAuth.current(); if (!actor) throw Error('UNAUTHORIZED');
    epoch.value = actor.epoch;
    canQueryCustomers.value = actor.permissions.includes('*') || actor.permissions.includes('customer:query');
    allowed.value = actor.permissions.includes('*') || actor.permissions.includes('pos:order:create');
    if (!allowed.value) { message.value = '当前账号没有收银开单权限，请联系店长。'; return; }
    // 有未核对的原请求时禁止新开单，避免重复成交。
    try { blocked.value = posRecovery.restore() !== null; } catch { blocked.value = true; }
    if (blocked.value) { message.value = '上次收银结果尚未核对，请先到「核对上次收银」结束后再开单。'; return; }
    await reload();
  } catch { if (visible && ticket === generation) uni.reLaunch({ url: '/pages/login/index' }); }
  finally { if (ticket === generation) loading.value = false; }
});
onHide(() => { customerSeq++; customerResults.value = []; chosenCustomer.value = null; customerBusy.value = false; canQueryCustomers.value = false; visible = false; generation++; allowed.value = false;
  if (searchTimer) { clearTimeout(searchTimer); searchTimer = null; } searchSeq++; searching.value = false; });

function choose(product: CatalogProduct) {
  if (!current() || busy.value || requoting.value) return;
  selected.value = product; quantity.value = '1'; message.value = '';
  void nextTick(() => { if (current() && selected.value?.id === product.id) uni.pageScrollTo({ selector:'#sale-quantity', duration:180 }); });
}
function validQuantity(product: CatalogProduct | null, value: string): boolean {
  if (!product || !/^(0|[1-9]\d*)(\.\d{1,4})?$/.test(value) || value.length > 24 || Number(value) <= 0) return false;
  return product.allowFractionalBaseQuantity || /^\d+$/.test(value);
}
async function addLine() {
  const product = selected.value;
  if (requoting.value) { message.value = '正在更新客户报价，请稍候再调整清单。'; return; }
  if (!current() || busy.value || !product || !validQuantity(product, quantity.value)) {
    message.value = '数量无效：整件单位只能填整数，计量单位最多四位小数。'; return;
  }
  if (lines.value.length && customerId.value.trim() !== quotedUnderCustomer) {
    await requoteAllLines();
    return; // 全单旧报价先核对完成，不能用新商品的一行报价替整单确认。
  }
  busy.value = true; message.value = '';
  try {
    // 单价只取后端报价指纹（含当前客户上下文），前端不推算、不兜底。
    // 请求客户指纹：报价期间客户变更（购物车为空时变更监听不会触发重报价），
    // 返回的零售价不得标成按新客户报价，直接丢弃本次加入。
    const requestCustomer = customerId.value.trim();
    const quote = await quoteProduct(product.id, product.mainUnit, epoch.value, requestCustomer || null);
    if (!current()) return;
    if (customerId.value.trim() !== requestCustomer) {
      message.value = '报价期间客户已变更，商品未加入；请确认客户后重新添加。';
      return;
    }
    if (quote.saleUnit !== product.mainUnit) { message.value = '报价单位与商品主单位不一致，请重新进入本页。'; return; }
    lines.value = [...lines.value, { productId: product.id, name: product.name,
      unit: product.mainUnit, quantity: quantity.value, quotedUnitPrice: quote.suggestedUnitPrice }];
    selected.value = null; quantity.value = '1';
    quotedUnderCustomer = requestCustomer;
  } catch { if (current()) message.value = '后端报价失败，已停止加入；请检查网络后重试。'; }
  finally { busy.value = false; }
}

/** 客户变更时按新客户上下文重新获取购物车全部报价，避免结算时后端重定价冲突。
 *  请求序号防竞争：慢请求的结果在新请求发起后一律丢弃，quotedUnderCustomer 指纹只在成功后提交。 */
let requoteSeq = 0;
async function requoteAllLines() {
  const seq = ++requoteSeq;
  const customer = customerId.value.trim();
  if (!lines.value.length || !current()) return;
  requoting.value = true; message.value = '';
  try {
    const updated: SaleLine[] = [];
    for (const line of lines.value) {
      const quote = await quoteProduct(line.productId, line.unit, epoch.value, customer || null);
      if (seq !== requoteSeq) return; // 期间客户再次变更，本次结果作废
      if (!current()) return;
      if (quote.saleUnit !== line.unit) throw new Error('UNIT_MISMATCH');
      updated.push({ ...line, quotedUnitPrice: quote.suggestedUnitPrice });
    }
    lines.value = updated;
    quotedUnderCustomer = customer; // 全部报价成功后才提交指纹，失败不得伪装成已按新客户报价
    message.value = '客户已变更，购物车单价已按后端最新报价更新；结算金额以后端确认为准。';
  } catch {
    if (current() && seq === requoteSeq) message.value = '客户变更后重新报价失败，请检查网络后重试，或移除商品后再结算。';
  } finally {
    if (seq === requoteSeq) requoting.value = false;
  }
}
watch(customerId, () => { if (current()) void requoteAllLines(); });
function removeLine(index: number) {
  if (!current() || busy.value || requoting.value) return;
  lines.value = lines.value.filter((_, i) => i !== index); message.value = '';
}
function saleOptions() {
  const id = customerId.value.trim();
  return {
    paymentType: paymentType.value,
    customerId: id ? id : null,
    deliveryType: deliveryType.value,
    receiverName: deliveryType.value === 2 ? receiverName.value.trim() || null : null,
    receiverPhone: deliveryType.value === 2 ? receiverPhone.value.trim() || null : null,
    receiverAddress: deliveryType.value === 2 ? receiverAddress.value.trim() || null : null,
    remark: remark.value.trim() || null,
  };
}
function validateOptions(): boolean {
  const options = saleOptions();
  if (options.paymentType === 6 && !options.customerId) {
    message.value = '赊销必须选择客户，请先核对客户姓名和手机号。'; return false;
  }
  if (options.deliveryType === 2 && (!options.receiverName || !options.receiverPhone || !options.receiverAddress)) {
    message.value = '门店配送必须填写收货人、收货电话和完整地址。'; return false;
  }
  return true;
}
async function checkout() {
  if (!current() || busy.value || blocked.value || !lines.value.length) return;
  if (requoting.value) { message.value = '正在按最新客户重新报价，请稍候再结算。'; return; }
  if (customerId.value.trim() !== quotedUnderCustomer) { void requoteAllLines(); return; }
  if (!validateOptions()) return;
  busy.value = true; message.value = ''; orderId.value = '';
  try {
    const result = await submitSale(lines.value, epoch.value, saleOptions());
    if (!current()) return;
    if (result.status === 'COMPLETED' && result.orderId) {
      orderId.value = result.orderId; lines.value = []; products.value = [];
      message.value = '销售已完成，请按约定交货。';
    } else {
      message.value = '开单结果待核对，请勿重复收款。请到「核对上次收银」用原请求核对。';
      uni.navigateTo({ url: '/pages/recovery/index' });
    }
  } catch (failure) {
    if (!current()) return;
    message.value = failure instanceof Error && failure.message === 'RECOVERY_REQUIRED'
      ? '已有未核对的原请求，请先核对上次收银。'
      : '本机核对记录未能确认，已停止提交，请到「核对上次收银」核对。';
  } finally { busy.value = false; }
}
const openRecovery = () => uni.navigateTo({ url: '/pages/recovery/index' });
const back = () => uni.redirectTo({ url: '/pages/workbench/index' });
</script>
<template>
 <view class="staff-page sale-page">
  <view class="page-heading"><text class="eyebrow">门店销售</text><text class="title">{{ orderId ? '开单完成' : stage === 'goods' ? '这次卖什么？' : '核对客户与结算' }}</text><text class="intro">{{ stage === 'goods' ? '找到商品，确认数量，再核对收款与交货。' : '先核对商品和客户，再确认本次收款或挂账。' }}</text></view>
  <text v-if="loading" class="intro">正在读取本店商品…</text>
  <template v-if="!loading && allowed">
   <view v-if="blocked" class="notice"><text>上次收银结果尚未核对，不能新开单。</text><button class="staff-primary" @click="openRecovery">核对上次收银</button></view>
   <view v-else-if="orderId" class="staff-panel success-panel"><text class="subtitle">本次销售已完成</text><text class="intro">订单编号 {{ orderId }}</text><text class="intro">请按约定交货；需要核对时可凭此编号查询。</text></view>
   <template v-else>
    <view class="steps"><text :class="stage==='goods'?'step-active':''">1 选择商品</text><text>›</text><text :class="stage==='settlement'?'step-active':''">2 核对开单</text></view>
    <template v-if="stage==='goods'">
     <view class="search-bar"><input placeholder-style="color:#626f7e" v-model="keyword" :disabled="busy" placeholder="搜索商品名称、编码或条码" /><button class="text-button" :disabled="busy" @click="reload">刷新</button></view>
     <text v-if="searching" class="intro">正在查找商品…</text>
     <view v-for="product in filtered" :key="product.id" class="staff-panel product-card">
      <view class="product-info"><text class="subtitle">{{ product.name }}</text><text class="muted">{{ product.code }} · 按{{ product.mainUnit }}销售</text><text class="catalog-price">目录价 ¥{{ product.suggestedRetailPrice }} <text class="muted">/{{ product.mainUnit }}</text></text></view>
      <button class="add-button" :disabled="busy || requoting" @click="choose(product)">选购</button>
     </view>
     <view v-if="selected" id="sale-quantity" class="staff-panel quantity-panel"><text class="subtitle">{{ selected.name }}</text><label>购买数量（{{ selected.mainUnit }}）<input placeholder-style="color:#626f7e" v-model="quantity" class="field" :disabled="busy" inputmode="decimal" /></label><text class="muted">{{ selected.allowFractionalBaseQuantity ? '支持最多四位小数' : '整件商品，请填写整数' }}</text><view class="inline-actions"><button class="staff-secondary" :disabled="busy" @click="selected=null">取消</button><button class="staff-primary" :disabled="busy || requoting" @click="addLine">加入清单</button></view></view>
     <view class="staff-panel basket-summary"><text class="subtitle">本次已选 {{ lines.length }} 项</text><text class="total">¥{{ cents(totalCents) }}</text><text class="muted">按当前报价核对，实际成交以后端确认为准。</text><button class="staff-primary" :disabled="busy || !lines.length" @click="openSettlement">核对清单与结算 ›</button></view>
    </template>
    <template v-else>
     <view class="section-head"><text class="subtitle">商品清单 · {{ lines.length }} 项</text><button class="text-button" :disabled="busy || requoting" @click="stage='goods'">继续选购</button></view>
     <view class="staff-panel basket-list"><view v-for="(line,index) in lines" :key="line.productId+':'+index" class="basket-row"><view><text class="row-name">{{ line.name }}</text><text class="muted">{{ line.quantity }} {{ line.unit }} × ¥{{ line.quotedUnitPrice }}</text><text class="line-price">¥{{ cents(lineCents(line)) }}</text></view><button class="text-button" :disabled="busy || requoting" @click="removeLine(index)">移除</button></view><text v-if="!lines.length" class="intro">清单已清空，请继续选购商品。</text></view>
     <view class="staff-panel section-panel"><text class="subtitle">销售给谁</text>
      <view v-if="chosenCustomer" class="selected-customer"><text class="row-name">{{ chosenCustomer.name }}</text><text class="intro">{{ chosenCustomer.phone || '未登记手机号' }}</text><button class="text-button" :disabled="busy || requoting" @click="clearCustomer">取消选择，改为散客</button></view>
      <text v-else class="intro">当前：散客（现金销售可不选客户）</text>
      <template v-if="canQueryCustomers"><view class="search-bar"><input placeholder-style="color:#626f7e" v-model="customerKeyword" :disabled="busy" maxlength="64" placeholder="输入客户姓名或手机号" confirm-type="search" @confirm="findCustomers" /><button class="text-button" :disabled="busy || customerBusy" @click="findCustomers">查找</button></view><text v-if="customerBusy" class="muted">正在查询客户…</text><text v-if="customerMessage" class="intro" role="status">{{ customerMessage }}</text><button v-for="customer in customerResults" :key="customer.id" class="customer-option" :disabled="busy" @click="selectCustomer(customer)"><view><text class="row-name">{{ customer.name }}</text><text class="muted">{{ customer.phone || '未登记手机号' }}</text></view><text>选择 ›</text></button></template>
      <text v-else class="intro">当前账号没有客户查询权限，暂不能在此选择赊销客户。请联系老板配置岗位权限。</text><text v-if="requoting" class="intro">正在按所选客户更新商品报价…</text>
     </view>
     <view class="staff-panel section-panel"><text class="subtitle">收款方式</text><view class="choice-row"><button :class="paymentType===1?'choice active':'choice'" :disabled="busy" @click="paymentType=1">现金收款</button><button :class="paymentType===6?'choice active':'choice'" :disabled="busy" @click="paymentType=6">赊销挂账</button></view><text class="muted">{{ paymentType===6 ? '本单计入所选客户欠款，请核对客户后确认。' : '请在实际收到现金后确认开单。' }}</text></view>
     <view class="staff-panel section-panel"><text class="subtitle">怎么交货</text><view class="choice-row"><button :class="deliveryType===1?'choice active':'choice'" :disabled="busy" @click="deliveryType=1">当场交货</button><button :class="deliveryType===2?'choice active':'choice'" :disabled="busy" @click="deliveryType=2">门店配送</button></view><template v-if="deliveryType===2"><label>收货人<input placeholder-style="color:#626f7e" v-model="receiverName" :disabled="busy" class="field" placeholder="填写收货人姓名" /></label><label>收货电话<input placeholder-style="color:#626f7e" v-model="receiverPhone" :disabled="busy" class="field" inputmode="tel" placeholder="填写联系电话" /></label><label>送货地址<input placeholder-style="color:#626f7e" v-model="receiverAddress" :disabled="busy" class="field" placeholder="填写村屯、街道及详细地址" /></label></template><label>备注（选填）<input placeholder-style="color:#626f7e" v-model="remark" :disabled="busy" class="field" maxlength="200" placeholder="如约定送货时间，最多200字" /></label></view>
     <view class="staff-panel settlement-panel"><text class="muted">{{ paymentType===6 ? '本次挂账金额' : '本次应收现金' }}</text><text class="total">¥{{ cents(totalCents) }}</text><text class="muted">请再次核对清单。报价变化时会停止开单，需重新确认。</text><button class="staff-primary" :disabled="busy || requoting || !lines.length || customerBusy" :loading="busy" @click="checkout">{{ paymentType===6 ? '确认赊销并开单' : '确认已收现金并开单' }}</button></view>
    </template>
   </template>
  </template>
  <text v-if="message" class="notice" role="status">{{ message }}</text>
  <view v-if="!loading && allowed && !blocked && !orderId && stage==='goods'" class="quick-bar"><view><text class="muted">已选 {{ lines.length }} 项</text><text class="quick-total">¥{{ cents(totalCents) }}</text></view><button class="staff-primary" :disabled="busy || !lines.length" @click="openSettlement">核对开单 ›</button></view>
  <button class="back-button" :disabled="busy" @click="back">返回工作台</button>
 </view>
</template>
<style scoped>
.sale-page{padding-bottom:calc(120px + env(safe-area-inset-bottom))}.quick-bar{position:fixed;bottom:0;left:0;right:0;max-width:600px;margin:0 auto;padding:12px 16px calc(12px + env(safe-area-inset-bottom));box-sizing:border-box;background:#fff;border-top:1px solid #e5e7eb;display:flex;align-items:center;justify-content:space-between;gap:16px;z-index:10}.quick-bar button{margin:0;font-size:15px}.quick-total{display:block;font-size:22px;font-weight:600}.page-heading{padding:12px 0 20px}.eyebrow{display:block;color:#1d5d73;font-size:13px;font-weight:600;margin-bottom:10px}.title{display:block;font-size:25px;font-weight:650;line-height:1.45}.intro{display:block;font-size:14px;line-height:1.7;color:#4b5563;margin:12px 0}.muted{display:block;font-size:13px;line-height:1.65;color:#4b5563}.subtitle{display:block;font-size:17px;font-weight:600;line-height:1.5;overflow-wrap:anywhere}.steps{display:flex;gap:12px;font-size:14px;color:#626f7e;margin-bottom:20px}.step-active{color:#1d5d73;font-weight:600}.search-bar{display:flex;align-items:center;gap:8px;background:#fff;border:1px solid #a8b2bc;border-radius:10px;padding:4px 12px;margin:12px 0}.search-bar input{flex:1;min-width:0;min-height:44px;font-size:15px}.text-button{background:transparent;color:#1d5d73;font-size:14px;padding:0 8px;margin:0;flex-shrink:0}.product-card{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:10px 0}.product-info{min-width:0}.catalog-price{display:block;color:#1f2937;font-size:17px;font-weight:550;margin-top:10px}.catalog-price .muted{display:inline;font-weight:400}.add-button{background:#eaf1f4;color:#1d5d73;font-size:14px;padding:0 16px;flex-shrink:0;margin:0}.quantity-panel,.basket-summary,.section-panel,.settlement-panel{margin-top:20px}.total{display:block;font-size:30px;font-weight:650;color:#1f2937;line-height:1.4;margin:8px 0;overflow-wrap:anywhere}.basket-summary button,.settlement-panel button{margin-top:20px}.inline-actions,.choice-row{display:flex;gap:10px;margin-top:16px}.inline-actions button,.choice-row button{flex:1;margin:0}label{display:block;margin-top:16px;font-size:14px;font-weight:550}.field{margin-top:8px;min-height:48px;padding:0 12px;border:1px solid #a8b2bc;background:#fff;border-radius:8px;font-size:16px}.section-head{display:flex;justify-content:space-between;align-items:center;gap:8px;margin:20px 0 12px}.basket-list{padding:0 16px}.basket-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px 0;border-bottom:1px solid #e5e7eb}.basket-row:last-child{border:0}.row-name{display:block;font-size:16px;font-weight:550;overflow-wrap:anywhere}.line-price{display:block;font-size:16px;margin-top:6px}.choice{background:#fff;border:1px solid #a8b2bc;color:#4b5563;font-size:15px}.choice.active{background:#eaf1f4;border:2px solid #1d5d73;color:#1d5d73;font-weight:600}.customer-option{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%;background:#f5f6f8;text-align:left;padding:12px;margin-top:8px;font-size:14px;color:#1d5d73}.selected-customer{padding:12px;background:#eaf1f4;border-radius:8px;margin-top:12px}.notice{display:block;padding:16px;border:1px solid #c8ac80;background:#fff;border-radius:10px;font-size:14px;line-height:1.7;margin:16px 0}.back-button{background:transparent;color:#4b5563;font-size:14px;margin:20px 0}.success-panel{border-color:#7ea76d}
</style>

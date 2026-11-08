<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { staffAuth } from '../../services/auth';
import { readCatalog, searchCatalog, readStock, searchCustomers, quoteProduct, submitSale } from '../../services/sale';
import { posRecovery, readPOSResult } from '../../services/pos-recovery';
import { lineCents, type CatalogProduct, type PosCustomer, type SaleLine } from '@agri/mobile-contracts/sale';
import { money, preciseDecimal } from '../../utils/money';

const products = ref<CatalogProduct[]>([]), keyword = ref('');
const searchResults = ref<CatalogProduct[]>([]), searching = ref(false);
/** 本店库存只作参考展示：是否可卖以后端出库时的可用量为准。 */
const stock = ref<Record<string, { quantity: string; unit: string }>>({});
const lines = ref<SaleLine[]>([]);
const selected = ref<CatalogProduct | null>(null), quantity = ref('1');
/** 客户档案是唯一来源：选了客户才带会员价，配送时收货信息也取自这里。 */
const customer = ref<PosCustomer | null>(null);
const customerOpen = ref(false), customerKeyword = ref(''), customerResults = ref<PosCustomer[]>([]);
const customerLoading = ref(false), customerSearchSeq = ref(0), customerError = ref('');
const deliveryType = ref<1 | 2>(1), remark = ref('');
const loading = ref(true), busy = ref(false), allowed = ref(false), blocked = ref(false);
const message = ref(''), orderId = ref(''), epoch = ref(-1);
let generation = 0, visible = false;
/** 购物车现有明细的报价所依据的客户编号；客户变更后必须重新拉取全部报价。 */
let quotedUnderCustomer = '';
const requoting = ref(false);

const cents = (value: number) => (value / 100).toFixed(2);
const totalCents = computed(() => lines.value.reduce((sum, line) => sum + lineCents(line), 0));
const customerId = computed(() => (customer.value ? customer.value.id : ''));
/** 有关键词时展示服务端搜索结果，无关键词时展示完整目录首页。 */
const filtered = computed(() => (keyword.value.trim() ? searchResults.value : products.value));
/** 门店配送的收货信息一律来自客户档案；缺项时就地说明，不让人手抄。 */
const receiverProblem = computed(() => {
  if (deliveryType.value !== 2) return '';
  if (!customer.value) return '门店配送需要先选客户，收货人、电话与地址由客户档案带出。';
  if (!customer.value.phone.trim()) return '该客户档案缺少手机号，请先在后台补全，或改为当场交货。';
  if (!customer.value.address) return '该客户档案缺少地址，请先在后台补全，或改为当场交货。';
  return '';
});
const current = (ticket = generation) => visible && allowed.value && ticket === generation && staffAuth.current()?.epoch === epoch.value;

function stockText(product: CatalogProduct): string {
  const row = stock.value[product.id];
  if (!row) return '无库存记录';
  const text = preciseDecimal(row.quantity);
  return '库存 ' + text + (row.unit ? ' ' + row.unit : '');
}
const outOfStock = (product: CatalogProduct) => {
  const row = stock.value[product.id];
  return !!row && Number(row.quantity) <= 0;
};

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
  // 库存读失败不阻断开单：出库时后端会按实际可用量拒绝，页面只做展示。
  try {
    const rows = await readStock(epoch.value);
    if (current(ticket)) stock.value = rows;
  } catch { /* 库存仅参考 */ }
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
  if (!current() || busy.value || query !== keyword.value.trim()) return;
  const seq = ++searchSeq;
  searching.value = true;
  try {
    const list = await searchCatalog(query, epoch.value);
    if (!current() || seq !== searchSeq || query !== keyword.value.trim()) return;
    searchResults.value = list;
    if (!list.length) message.value = '没有匹配的商品，可尝试编码、名称或条码搜索。';
  } catch {
    if (current() && seq === searchSeq) message.value = '商品搜索失败，请检查网络后重试。';
  } finally { if (seq === searchSeq) searching.value = false; }
}
watch(keyword, () => {
  searchSeq++; searchResults.value = []; searching.value = false;
  if (searchTimer) { clearTimeout(searchTimer); searchTimer = null; }
  if (current()) scheduleSearch();
}, { flush: 'sync' });

/* ---------- 客户选择 ---------- */
async function loadCustomers(query: string) {
  if (!current()) return;
  const seq = ++customerSearchSeq.value;
  customerLoading.value = true; customerError.value = ''; customerResults.value = [];
  try {
    const list = await searchCustomers(query, epoch.value);
    if (!current() || seq !== customerSearchSeq.value) return;
    customerResults.value = list;
  } catch {
    if (current() && seq === customerSearchSeq.value) { customerResults.value = []; customerError.value = '客户查询未完成，请重试；不要把网络失败当成客户未建档。'; }
  } finally { if (seq === customerSearchSeq.value) customerLoading.value = false; }
}
let customerTimer: ReturnType<typeof setTimeout> | null = null;
function openCustomers() {
  if (!current() || busy.value) return;
  customerOpen.value = true;
  if (customerTimer) { clearTimeout(customerTimer); customerTimer = null; }
  customerKeyword.value = '';
  void loadCustomers('');
}
function closeCustomers() {
  if (customerTimer) { clearTimeout(customerTimer); customerTimer = null; }
  customerSearchSeq.value++; customerLoading.value = false; customerResults.value = []; customerError.value = '';
  customerOpen.value = false; customerKeyword.value = '';
}
watch(customerKeyword, () => {
  customerSearchSeq.value++; customerResults.value = []; customerLoading.value = false; customerError.value = '';
  if (customerTimer) { clearTimeout(customerTimer); customerTimer = null; }
}, { flush: 'sync' });
function scheduleCustomerSearch() {
  if (customerTimer) clearTimeout(customerTimer);
  const query = customerKeyword.value;
  customerTimer = setTimeout(() => { customerTimer = null; if (customerOpen.value && query === customerKeyword.value) void loadCustomers(query); }, 300);
}
function pickCustomer(item: PosCustomer) {
  if (!current() || !customerOpen.value || !customerResults.value.includes(item)) return;
  customer.value = item;
  closeCustomers();
  message.value = '';
}
function pickWalkIn() {
  if (!current()) return;
  customer.value = null;
  closeCustomers();
  message.value = '';
}

onShow(async () => {
  visible = true; const ticket = ++generation;
  loading.value = true; busy.value = false; allowed.value = false; blocked.value = false;
  products.value = []; searchResults.value = []; stock.value = {}; lines.value = [];
  selected.value = null; keyword.value = '';
  if (searchTimer) { clearTimeout(searchTimer); searchTimer = null; } searchSeq++; searching.value = false;
  customer.value = null; customerOpen.value = false; customerKeyword.value = ''; customerResults.value = [];
  customerSearchSeq.value++;
  deliveryType.value = 1;
  quotedUnderCustomer = ''; requoting.value = false; requoteSeq++;
  remark.value = '';
  message.value = ''; orderId.value = '';
  try {
    await staffAuth.refresh(); if (!visible || ticket !== generation) return;
    const actor = staffAuth.current(); if (!actor) throw Error('UNAUTHORIZED');
    epoch.value = actor.epoch;
    allowed.value = actor.permissions.includes('*') || actor.permissions.includes('pos:order:create');
    if (!allowed.value) { message.value = '当前账号没有收银开单权限，请联系店长。'; return; }
    // 有未核对的原请求时禁止新开单，避免重复成交。
    try { blocked.value = posRecovery.restore() !== null; } catch { blocked.value = true; }
    if (blocked.value) { message.value = '上次收银结果尚未核对，请先到「核对上次收银」结束后再开单。'; return; }
    await reload();
  } catch { if (visible && ticket === generation) uni.reLaunch({ url: '/pages/login/index' }); }
  finally { if (ticket === generation) loading.value = false; }
});
onHide(() => { visible = false; generation++; allowed.value = false; customerOpen.value = false;
  if (searchTimer) { clearTimeout(searchTimer); searchTimer = null; } searchSeq++; searching.value = false;
  if (customerTimer) { clearTimeout(customerTimer); customerTimer = null; } customerSearchSeq.value++; });

/* ---------- 商品选择与加入 ---------- */
function choose(product: CatalogProduct) {
  if (!current() || busy.value || requoting.value) return;
  selected.value = product; quantity.value = '1'; message.value = '';
}
function closeAdd() { selected.value = null; quantity.value = '1'; }
function validQuantity(product: CatalogProduct | null, value: string): boolean {
  if (!product || !/^(0|[1-9]\d*)(\.\d{1,4})?$/.test(value) || value.length > 24 || Number(value) <= 0) return false;
  return product.allowFractionalBaseQuantity || /^\d+$/.test(value);
}
/** 步进只在整数量级做加减：计量单位仍可手输小数，避免步进把小数位抹掉。 */
function stepQuantity(delta: number) {
  const product = selected.value;
  if (!product) return;
  const step = product.allowFractionalBaseQuantity ? 0.5 : 1;
  const base = Number(quantity.value);
  const next = Number.isFinite(base) ? base + delta * step : 1;
  quantity.value = next > 0 ? String(Math.round(next * 10000) / 10000) : String(step);
}
async function addLine() {
  const product = selected.value;
  if (!current() || busy.value || requoting.value) return;
  if (lines.value.length && customerId.value.trim() !== quotedUnderCustomer) {
    await requoteAllLines();
    return;
  }
  if (!product || !validQuantity(product, quantity.value)) {
    message.value = '数量无效：整件单位只能填整数，计量单位最多四位小数。'; return;
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
  const currentCustomer = customerId.value.trim();
  if (!lines.value.length || !current()) return;
  requoting.value = true; message.value = '';
  try {
    const updated: SaleLine[] = [];
    for (const line of lines.value) {
      const quote = await quoteProduct(line.productId, line.unit, epoch.value, currentCustomer || null);
      if (seq !== requoteSeq) return; // 期间客户再次变更，本次结果作废
      if (!current()) return;
      if (quote.saleUnit !== line.unit) throw new Error('UNIT_MISMATCH');
      updated.push({ ...line, quotedUnitPrice: quote.suggestedUnitPrice });
    }
    lines.value = updated;
    quotedUnderCustomer = currentCustomer; // 全部报价成功后才提交指纹，失败不得伪装成已按新客户报价
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
  return {
    paymentType: 1 as const,
    customerId: customerId.value.trim() || null,
    deliveryType: deliveryType.value,
    remark: remark.value.trim() || null,
  };
}
function validateOptions(): boolean {
  if (receiverProblem.value) { message.value = receiverProblem.value; return false; }
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
      orderId.value = result.orderId; lines.value = [];
      message.value = '开单完成，收款与库存由后端一次写入。';
      // 后端已回执订单号，本机原请求不再有"是否已成交"的疑问：向后端核对该请求确为这笔订单后清掉，
      // 否则下一单会被"上次收银结果尚未核对"挡住，而员工其实刚看到成功。
      try { await posRecovery.finishCompleted(result.orderId, true, readPOSResult); }
      catch { /* 核对失败时保留原请求，下次进入仍按待核对处理 */ }
      await reload();
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
 <view class="page" :class="{ 'page-dock': !loading && allowed && !blocked }">
  <text v-if="loading" class="card t-meta">正在读取本店商品…</text>

  <view v-if="blocked" class="alert alert-danger">
   <AppIcon name="alert" :size="16" class="alert-icon" />
   <view class="grow">
    <text class="t-strong">上次收银结果尚未核对</text>
    <text class="t-meta">核对完成前不能新开单，避免重复成交。</text>
   </view>
  </view>
  <button hover-class="btn-hover" hover-stay-time="60" v-if="blocked" class="btn btn-primary" @click="openRecovery">核对上次收银</button>

  <template v-if="!loading && allowed && !blocked">
   <!-- 客户：开单上下文，决定会员价与配送收货信息 -->
   <view hover-class="tap-hover" hover-stay-time="80" class="card card-tight cust-bar" @click="openCustomers">
    <view class="cust-icon"><AppIcon name="user" :size="18" /></view>
    <view class="grow">
     <view class="row">
      <text class="t-sub">{{ customer ? customer.name : '散客（未选客户）' }}</text>
      <text v-if="customer && customer.vipActive" class="chip chip-primary chip-sm ml8">会员</text>
     </view>
     <text class="t-cap">{{ customer ? customer.phone + ' · ' + (customer.address || '档案无地址') : '按零售价结算；选客户可享会员价，配送也用它带出收货信息' }}</text>
    </view>
    <text class="chip chip-line chip-sm">切换</text>
   </view>

   <view class="search-wrap block">
    <AppIcon name="search" :size="16" class="search-ic" />
    <input v-model="keyword" class="input input-search" placeholder="按商品名称、编码或条码搜索" placeholder-style="color:#9aa6b2" />
   </view>

   <view class="section section-first">
    <view class="section-head"><text class="section-title">选择商品</text><text class="section-note">{{ searching ? '搜索中…' : filtered.length + ' 个 · 来自后台商品档案' }}</text></view>
   </view>

   <view v-if="filtered.length" class="card card-flat">
    <view hover-class="tap-hover" hover-stay-time="80" v-for="(product, index) in filtered" :key="product.id" class="list-item row-between" :class="{ 'list-item-last': index === filtered.length - 1 }" @click="choose(product)">
     <view class="grow">
      <text class="t-sub">{{ product.name }}</text>
      <view class="row mt4">
       <text class="t-cap">{{ product.code }}</text>
       <text class="t-cap ml8" :class="{ 't-warn': outOfStock(product) }">· {{ stockText(product) }}</text>
      </view>
     </view>
     <view class="price-box">
      <text class="t-money t-primary">¥{{ money(product.suggestedRetailPrice) }}</text>
      <text class="t-cap">/{{ product.baseUnitName }}</text>
      <AppIcon name="chevron" :size="12" class="chev" />
     </view>
    </view>
   </view>

   <view class="section">
    <view class="section-head"><text class="section-title">本次商品</text><text class="section-note">{{ lines.length }} 项</text></view>
   </view>

   <view v-if="!lines.length" class="card empty">
    <view class="empty-icon"><AppIcon name="bag" :size="22" /></view>
    <text class="empty-text">还没有商品。点上方商品选数量加入，单价由后端报价。</text>
   </view>

   <view v-for="(line, index) in lines" :key="line.productId + ':' + index" class="card card-tight">
    <view class="row-between">
     <text class="t-sub grow">{{ line.name }}</text>
     <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-danger-quiet btn-sm btn-auto" :disabled="busy || requoting" @click="removeLine(index)">移除</button>
    </view>
    <view class="row-between mt4">
     <text class="t-meta">{{ line.quantity }} {{ line.unit }} · 单价 ¥{{ money(line.quotedUnitPrice) }}</text>
     <text class="t-money">¥{{ cents(lineCents(line)) }}</text>
    </view>
   </view>

   <view v-if="lines.length" class="card card-tight">
    <view class="row-between">
     <text class="t-meta">展示合计 {{ requoting ? '· 重新报价中' : '' }}</text>
     <text class="t-money">¥{{ cents(totalCents) }}</text>
    </view>
    <text class="t-cap mt4">仅作核对；成交金额以后端结算为准，本页不计算价格。</text>
   </view>

   <view class="section"><text class="section-title">结算方式</text></view>
   <view class="card card-tight">
    <view class="row-between">
     <view class="row"><AppIcon name="check" :size="16" class="t-primary mr8" /><text class="t-sub">现金</text></view>
     <text class="chip chip-primary chip-sm">本次可用</text>
    </view>
    <text class="t-cap mt8">移动端只支持现金收款；赊销挂账、会员余额/专用金、优惠券、微信/支付宝与人工改价请在后台收银台办理。</text>
   </view>

   <view class="section"><text class="section-title">交货方式</text></view>
   <view class="card">
    <view class="row">
     <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-sm grow mr8" :class="deliveryType === 1 ? 'btn-primary' : 'btn-ghost'" :disabled="busy" @click="deliveryType = 1">当场交货</button>
     <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-sm grow" :class="deliveryType === 2 ? 'btn-primary' : 'btn-ghost'" :disabled="busy" @click="deliveryType = 2">门店配送</button>
    </view>

    <view v-if="deliveryType === 2" class="mt12">
     <template v-if="customer">
      <view class="row-between">
       <text class="t-cap">收货信息（取自客户档案，不手填）</text>
       <text hover-class="tap-hover" hover-stay-time="80" class="chip chip-line chip-sm" @click="openCustomers">更换客户</text>
      </view>
      <view class="mt8">
       <view class="kv"><text class="kv-key">收货人</text><text class="kv-val">{{ customer.name }}</text></view>
       <view class="kv"><text class="kv-key">电话</text><text class="kv-val">{{ customer.phone }}</text></view>
       <view class="kv"><text class="kv-key">地址</text><text class="kv-val">{{ customer.address || '档案未填' }}</text></view>
      </view>
      <view v-if="receiverProblem" class="alert alert-danger mt8">
       <AppIcon name="alert" :size="16" class="alert-icon" />
       <text>{{ receiverProblem }}</text>
      </view>
     </template>
     <view v-else class="alert alert-danger">
      <AppIcon name="alert" :size="16" class="alert-icon" />
      <view class="grow">
       <text class="t-strong">配送需要先选客户</text>
       <text class="t-meta">收货人、电话与地址从客户档案带出，不再手工填写。</text>
      </view>
     </view>
     <button v-if="!customer" hover-class="btn-hover" hover-stay-time="60" class="btn btn-ghost btn-sm mt8" :disabled="busy" @click="openCustomers">选择客户</button>
    </view>

    <view class="field">
     <text class="field-label">整单备注（可选，最多 200 字）</text>
     <input v-model="remark" class="input" placeholder="可留空" placeholder-style="color:#9aa6b2" />
    </view>
   </view>

   <view v-if="orderId" class="card card-ok">
    <view class="row"><AppIcon name="check" :size="18" class="t-ok mr8" /><text class="t-sub">开单完成</text></view>
    <text class="t-code mt4">订单号 {{ orderId }}</text>
   </view>
  </template>

  <view v-if="message" class="alert alert-info mt8" role="status">
   <AppIcon name="alert" :size="16" class="alert-icon" />
   <text>{{ message }}</text>
  </view>

  <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-quiet mt8" :disabled="busy" @click="reload">重新读取商品</button>
  <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-quiet" :disabled="busy" @click="back">返回工作台</button>

  <view v-if="!loading && allowed && !blocked" class="dock">
   <view class="dock-inner">
    <view class="dock-row">
     <view class="grow">
      <text class="dock-label">{{ deliveryType === 2 ? '配送 · 合计' : '合计' }}</text>
      <text class="dock-amount">¥{{ cents(totalCents) }}</text>
     </view>
     <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-primary dock-btn" :disabled="busy || !lines.length" :loading="busy" @click="checkout">提交开单</button>
    </view>
   </view>
  </view>

  <!-- 选客户面板：后台客户档案检索，选中即成为本次开单上下文 -->
  <template v-if="customerOpen">
   <view class="mask" @click="closeCustomers"></view>
   <view class="sheet">
    <view class="sheet-head">
     <text class="sheet-title">选择客户</text>
     <view hover-class="tap-hover" hover-stay-time="80" class="sheet-close" @click="closeCustomers"><AppIcon name="close" :size="16" /></view>
    </view>
    <view class="search-wrap">
     <AppIcon name="search" :size="16" class="search-ic" />
     <input v-model="customerKeyword" class="input input-search" placeholder="姓名、手机号或客户编码" placeholder-style="color:#9aa6b2" @input="scheduleCustomerSearch" />
    </view>
    <view class="sheet-body">
     <scroll-view scroll-y class="sheet-list">
      <view v-if="customerLoading" class="row mt12"><view class="spinner spinner-primary"></view><text class="t-meta">正在读取客户档案…</text></view>
      <view v-else-if="customerError" class="alert alert-danger" role="status"><text>{{ customerError }}</text><button class="btn btn-ghost btn-sm" @click="loadCustomers(customerKeyword)">重试查询</button></view>
      <view v-else-if="!customerResults.length" class="empty">
       <view class="empty-icon"><AppIcon name="user" :size="22" /></view>
       <text class="empty-text">{{ customerKeyword.trim() ? '没有匹配的客户，换个姓名或手机号再试。' : '后台还没有客户档案，可直接按散客开单。' }}</text>
       <text class="t-cap mt4">客户档案由后台维护；需要门店配送请先让后台建档，散客开单只支持当场交货。</text>
      </view>
      <view v-else>
       <view hover-class="tap-hover" hover-stay-time="80" v-for="(item, index) in customerResults" :key="item.id" class="cust-row" :class="{ 'cust-row-last': index === customerResults.length - 1 }" @click="pickCustomer(item)">
        <view class="grow">
         <view class="row">
          <text class="t-sub">{{ item.name }}</text>
          <text v-if="item.vipActive" class="chip chip-primary chip-sm ml8">会员</text>
         </view>
         <text class="t-cap">{{ item.phone }}{{ item.code ? ' · ' + item.code : '' }}</text>
         <text class="t-cap">{{ item.address || '档案无地址，配送前需在后台补全' }}</text>
        </view>
        <AppIcon name="chevron" :size="12" class="chev" />
       </view>
      </view>
     </scroll-view>
    </view>
    <view class="sheet-foot">
     <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-ghost btn-sm" :disabled="busy" @click="pickWalkIn">不选客户，按散客开单</button>
    </view>
   </view>
  </template>

  <!-- 数量面板：选商品后先确认数量，再加入购物车 -->
  <template v-if="selected">
   <view class="mask" @click="closeAdd"></view>
   <view class="sheet">
    <view class="sheet-head">
     <view class="grow">
      <text class="sheet-title">{{ selected.name }}</text>
      <text class="t-cap">{{ selected.code }} · 参考价 ¥{{ money(selected.suggestedRetailPrice) }}/{{ selected.baseUnitName }}</text>
     </view>
     <view hover-class="tap-hover" hover-stay-time="80" class="sheet-close" @click="closeAdd"><AppIcon name="close" :size="16" /></view>
    </view>
    <view class="qty-line">
     <view hover-class="tap-hover" hover-stay-time="80" class="qty-btn" @click="stepQuantity(-1)">−</view>
     <input v-model="quantity" class="qty-input" inputmode="decimal" />
     <view hover-class="tap-hover" hover-stay-time="80" class="qty-btn" @click="stepQuantity(1)">+</view>
    </view>
    <view class="row-between mt8">
     <text class="t-cap">{{ selected.mainUnit }} · {{ selected.allowFractionalBaseQuantity ? '可填四位小数' : '仅整数' }} · {{ stockText(selected) }}</text>
     <text v-if="outOfStock(selected)" class="chip chip-warn chip-sm">本店无可用库存</text>
    </view>
    <text class="t-cap mt8">加入时按后端实时报价，结算金额以后端为准。</text>
    <view class="sheet-foot">
     <button hover-class="btn-hover" hover-stay-time="60" class="btn btn-primary" :disabled="busy || !validQuantity(selected, quantity)" @click="addLine">加入本次商品</button>
    </view>
   </view>
  </template>
 </view>
</template>

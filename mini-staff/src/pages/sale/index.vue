<script setup lang="ts">
import { ref, computed } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { staffAuth } from '../../services/auth';
import { readCatalog, quoteProduct, submitSale } from '../../services/sale';
import { posRecovery } from '../../services/pos-recovery';
import { lineCents, type CatalogProduct, type SaleLine } from '@agri/mobile-contracts/sale';

const products = ref<CatalogProduct[]>([]), lines = ref<SaleLine[]>([]), keyword = ref('');
const selected = ref<CatalogProduct | null>(null), quantity = ref('1');
const paymentType = ref<1 | 6>(1), customerId = ref(''), deliveryType = ref<1 | 2>(1);
const receiverName = ref(''), receiverPhone = ref(''), receiverAddress = ref(''), remark = ref('');
const loading = ref(true), busy = ref(false), allowed = ref(false), blocked = ref(false);
const message = ref(''), orderId = ref(''), epoch = ref(-1);
let generation = 0, visible = false;
const cents = (value: number) => (value / 100).toFixed(2);
const totalCents = computed(() => lines.value.reduce((sum, line) => sum + lineCents(line), 0));
const filtered = computed(() => {
  const key = keyword.value.trim().toLowerCase();
  return key ? products.value.filter(p => p.name.toLowerCase().includes(key) || p.code.toLowerCase().includes(key)) : products.value;
});
const current = (ticket = generation) => visible && allowed.value && ticket === generation && staffAuth.current()?.epoch === epoch.value;

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

onShow(async () => {
  visible = true; const ticket = ++generation;
  loading.value = true; busy.value = false; allowed.value = false; blocked.value = false;
  products.value = []; lines.value = []; selected.value = null; keyword.value = '';
  paymentType.value = 1; customerId.value = ''; deliveryType.value = 1;
  receiverName.value = ''; receiverPhone.value = ''; receiverAddress.value = ''; remark.value = '';
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
onHide(() => { visible = false; generation++; allowed.value = false; });

function choose(product: CatalogProduct) {
  if (!current() || busy.value) return;
  selected.value = product; quantity.value = '1'; message.value = '';
}
function validQuantity(product: CatalogProduct | null, value: string): boolean {
  if (!product || !/^(0|[1-9]\d*)(\.\d{1,4})?$/.test(value) || value.length > 24 || Number(value) <= 0) return false;
  return product.allowFractionalBaseQuantity || /^\d+$/.test(value);
}
async function addLine() {
  const product = selected.value;
  if (!current() || busy.value || !product || !validQuantity(product, quantity.value)) {
    message.value = '数量无效：整件单位只能填整数，计量单位最多四位小数。'; return;
  }
  busy.value = true; message.value = '';
  try {
    // 单价只取后端报价指纹，前端不推算、不兜底。
    const quote = await quoteProduct(product.id, product.mainUnit, epoch.value);
    if (!current()) return;
    if (quote.saleUnit !== product.mainUnit) { message.value = '报价单位与商品主单位不一致，请重新进入本页。'; return; }
    lines.value = [...lines.value, { productId: product.id, name: product.name,
      unit: product.mainUnit, quantity: quantity.value, quotedUnitPrice: quote.suggestedUnitPrice }];
    selected.value = null; quantity.value = '1';
  } catch { if (current()) message.value = '后端报价失败，已停止加入；请检查网络后重试。'; }
  finally { busy.value = false; }
}
function removeLine(index: number) {
  if (!current() || busy.value) return;
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
    message.value = '赊销必须填写客户编号，请向店长核对后填写。'; return false;
  }
  if (options.deliveryType === 2 && (!options.receiverName || !options.receiverPhone || !options.receiverAddress)) {
    message.value = '门店配送必须填写收货人、收货电话和完整地址。'; return false;
  }
  return true;
}
async function checkout() {
  if (!current() || busy.value || blocked.value || !lines.value.length) return;
  if (!validateOptions()) return;
  busy.value = true; message.value = ''; orderId.value = '';
  try {
    const result = await submitSale(lines.value, epoch.value, saleOptions());
    if (!current()) return;
    if (result.status === 'COMPLETED' && result.orderId) {
      orderId.value = result.orderId; lines.value = []; products.value = [];
      message.value = '开单完成，收款与库存由后端一次写入。';
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
 <view class="page">
  <text class="title">移动开单</text>
  <text v-if="loading" class="intro">正在读取本店商品…</text>
  <template v-if="!loading && allowed">
   <view v-if="blocked" class="notice"><text>上次收银结果尚未核对，不能新开单。</text><button @click="openRecovery">核对上次收银</button></view>
   <template v-else>
    <input v-model="keyword" class="search" placeholder="按商品名称或编码搜索" />
    <text class="subtitle">选择商品</text>
    <view v-for="product in filtered" :key="product.id" class="panel" @click="choose(product)">
     <text class="subtitle">{{ product.name }}</text>
     <text class="intro">{{ product.code }} · 主单位 {{ product.mainUnit }} · 目录价 ¥{{ product.suggestedRetailPrice }}</text>
    </view>
    <view v-if="selected" class="panel">
     <text class="subtitle">{{ selected.name }}</text>
     <text class="intro">数量（{{ selected.mainUnit }}）{{ selected.allowFractionalBaseQuantity ? '，最多四位小数' : '，仅整数' }}</text>
     <input v-model="quantity" class="search" inputmode="decimal" />
     <button :disabled="busy" @click="addLine">按后端报价加入</button>
    </view>
    <text class="subtitle">本次商品</text>
    <view v-for="(line, index) in lines" :key="line.productId + ':' + index" class="panel item">
     <view><text class="subtitle">{{ line.name }}</text><text class="intro">{{ line.quantity }} {{ line.unit }} · 后端单价 ¥{{ line.quotedUnitPrice }} · 展示小计 ¥{{ cents(lineCents(line)) }}</text></view>
     <button :disabled="busy" @click="removeLine(index)">移除</button>
    </view>
    <view class="panel"><text class="intro">展示合计 ¥{{ cents(totalCents) }}，仅作核对；成交金额以后端结算为准，本页不计算价格。</text></view>
    <view class="panel">
     <text class="subtitle">结算方式（本版仅现金 / 赊销）</text>
     <button :class="paymentType===1?'primary':''" :disabled="busy" @click="paymentType=1">现金</button>
     <button :class="paymentType===6?'primary':''" :disabled="busy" @click="paymentType=6">赊销（挂账）</button>
     <input v-model="customerId" class="search" inputmode="numeric" placeholder="客户编号：赊销必填，现金可留空" />
     <text class="intro">会员价与赊销归属由后端按真实客户判断，本页不传会员等级、不计算金额。</text>
    </view>
    <view class="panel">
     <text class="subtitle">交货方式</text>
     <button :class="deliveryType===1?'primary':''" :disabled="busy" @click="deliveryType=1">当场交货</button>
     <button :class="deliveryType===2?'primary':''" :disabled="busy" @click="deliveryType=2">门店配送</button>
     <template v-if="deliveryType===2">
      <input v-model="receiverName" class="search" placeholder="收货人" />
      <input v-model="receiverPhone" class="search" inputmode="tel" placeholder="收货电话" />
      <input v-model="receiverAddress" class="search" placeholder="完整收货地址" />
     </template>
     <input v-model="remark" class="search" placeholder="整单备注（可选，最多200字）" />
    </view>
    <button class="primary" :disabled="busy || !lines.length" :loading="busy" @click="checkout">提交开单</button>
   </template>
  </template>
  <text v-if="message" class="intro" role="status">{{ message }}</text>
  <view v-if="orderId" class="panel"><text class="subtitle">订单号 {{ orderId }}</text></view>
  <button :disabled="busy" @click="reload">重新读取商品</button>
  <button :disabled="busy" @click="back">返回工作台</button>
 </view>
</template>
<style scoped>
.page{padding:24px 20px;max-width:600px;margin:auto}.title{display:block;font-size:24px;font-weight:600}.subtitle{display:block;font-size:20px;font-weight:600;line-height:1.6;word-break:break-all}.intro{display:block;color:#4b5563;line-height:1.7;margin:12px 0}.panel{display:block;background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:20px;margin:20px 0}.notice{background:#fff;border:1px solid #c8ac80;border-radius:10px;padding:20px;margin:20px 0}.search{background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:12px;margin:12px 0}.item{display:flex;align-items:center;justify-content:space-between;gap:12px}button{min-height:48px;margin:16px 0;color:#1d5d73;background:#fff;border:1px solid #1d5d73}.primary{color:#fff;background:#1d5d73}
</style>

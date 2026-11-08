<script setup lang="ts">
import { ref, computed } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { staffAuth } from '../../services/auth';
const name = ref(''), store = ref(''), checking = ref(true), error = ref('');
const canRecover = ref(false),canQueryInventory=ref(false),canReceive=ref(false),canDeliver=ref(false);
let epoch = 0;
const openDeviceCheck = () => uni.navigateTo({ url: '/pages/device-check/index' });
const openRecovery = () => uni.navigateTo({ url:'/pages/recovery/index' });
const openSale = () => uni.navigateTo({ url: '/pages/sale/index' });
const canSale = ref(false);
const openInventory = () => uni.navigateTo({ url:'/pages/scan/index' });
const openReceiving = () => uni.navigateTo({url:'/pages/receipt/list/index'});
const openDelivery = () => uni.navigateTo({url:'/pages/delivery/list/index'});
const canPickup=ref(false);
const openPickup=()=>uni.navigateTo({url:'/pages/pickup/list/index'});
// 个人常用项只改变布局，实际入口始终取本次刷新后的权限交集。
const favoriteIds = ref<string[] | null>(null), draftIds = ref<string[]>([]);
const editing = ref(false), preferenceMessage = ref('');
let preferenceKey = '', active = false, actorEpoch = -1;
const features = computed(() => [
  { id:'sale', name:'销售开单', hint:'找商品、选客户，现金或赊销', mark:'售', allowed:canSale.value, open:openSale },
  { id:'inventory', name:'查库存', hint:'扫码找货，核对批次与货位', mark:'查', allowed:canQueryInventory.value, open:openInventory },
  { id:'receipt', name:'采购收货', hint:'按采购单登记本次到货', mark:'收', allowed:canReceive.value, open:openReceiving },
  { id:'pickup', name:'库房提货', hint:'核对剩余商品，确认交货', mark:'提', allowed:canPickup.value, open:openPickup },
  { id:'delivery', name:'我的配送', hint:'查看指派给我的送货任务', mark:'送', allowed:canDeliver.value, open:openDelivery },
].filter(item => item.allowed));
const favorites = computed(() => features.value.filter(item => favoriteIds.value === null || favoriteIds.value.includes(item.id)));
function configureFavorites() {
  if (!active || checking.value || !name.value) return;
  draftIds.value = favorites.value.map(item => item.id); editing.value = true; preferenceMessage.value = '';
}
function toggleFavorite(id: string) {
  if (!features.value.some(item => item.id === id)) return;
  draftIds.value = draftIds.value.includes(id) ? draftIds.value.filter(item => item !== id) : [...draftIds.value, id];
}
function saveFavorites() {
  const actor = staffAuth.current();
  if (!active || checking.value || !actor || actor.epoch !== actorEpoch || preferenceKey !== `staff-home-v1:${actor.staffId}:${actor.storeId}`) return;
  const ids = draftIds.value.filter(id => features.value.some(item => item.id === id));
  try {
    uni.setStorageSync(preferenceKey, { version:1, ids });
    favoriteIds.value = ids; editing.value = false; preferenceMessage.value = '常用功能已保存到这台手机。';
  } catch { preferenceMessage.value = '这台手机未能保存设置，请重试。'; }
}
onShow(async () => {
  active = true; actorEpoch = -1;
  const current = ++epoch;
  checking.value = true; error.value = ''; name.value = ''; store.value = '';
  editing.value = false; favoriteIds.value = null; preferenceKey = ''; preferenceMessage.value = '';
  canRecover.value = false;canSale.value=false;canQueryInventory.value=false;canReceive.value=false;canDeliver.value=false;
  canPickup.value=false;
  try {
    await staffAuth.refresh();
    if (epoch !== current) return;
    const actor = staffAuth.current();
    if (!actor) throw Error('UNAUTHORIZED');
    actorEpoch = actor.epoch;
    name.value = actor.name; store.value = actor.storeId;
    canRecover.value = actor.permissions.includes('*') || actor.permissions.includes('pos:order:create');
    canSale.value = canRecover.value;
    canQueryInventory.value=actor.permissions.includes('*')||actor.permissions.includes('inventory:view');
    canReceive.value=actor.permissions.includes('*')||actor.permissions.includes('purchase:inbound');
    canDeliver.value=actor.permissions.includes('*')||actor.permissions.includes('order:delivery:assigned');
    preferenceKey = `staff-home-v1:${actor.staffId}:${actor.storeId}`;
    try {
      const saved = uni.getStorageSync(preferenceKey);
      if (saved?.version === 1 && Array.isArray(saved.ids) && saved.ids.every((id: unknown) => typeof id === 'string')) favoriteIds.value = saved.ids;
    } catch { preferenceMessage.value = '暂时无法读取常用设置，已显示全部授权功能。'; }
    canPickup.value=actor.permissions.includes('*')||actor.permissions.includes('order:pickup:verify');
  } catch {
    if (epoch === current) {
      error.value = '当前身份未能确认，请重新登录。';
      uni.reLaunch({ url: '/pages/login/index' });
    }
  } finally { if (epoch === current) checking.value = false; }
});
onHide(() => { active = false; actorEpoch = -1; epoch++; });
async function logout() {
  active = false; actorEpoch = -1; epoch++; checking.value = true; name.value = ''; store.value = '';
  await staffAuth.logout(); uni.reLaunch({ url: '/pages/login/index' });
}
</script>
<template>
  <view class="staff-page">
    <view class="home-header">
      <view><text class="eyebrow">农资门店 · 员工工作台</text><text class="greeting">{{ name ? name + '，开始今天的工作' : '门店工作台' }}</text></view>
      <text v-if="name" class="store-pill">门店 {{ store }}</text>
    </view>
    <view v-if="checking" class="staff-panel loading-state"><text class="section-title">正在确认工作身份</text><text class="staff-muted">核对门店与最新权限后显示功能。</text></view>
    <template v-else-if="name">
      <view class="section-head"><view><text class="section-title">常用功能</text><text class="staff-muted">按你的岗位习惯安排入口</text></view><button class="text-button" @click="configureFavorites">自定义</button></view>
      <view v-if="editing" class="staff-panel preference-panel">
        <text class="section-title">选择首页常用功能</text><text class="staff-muted">仅显示当前已授权功能，设置保存在这台手机。</text>
        <button v-for="item in features" :key="item.id" class="preference-option" :aria-label="item.name + (draftIds.includes(item.id) ? '，已选择' : '，未选择')" @click="toggleFavorite(item.id)"><text>{{ item.name }}</text><text>{{ draftIds.includes(item.id) ? '已选择 ✓' : '选择' }}</text></button>
        <view class="actions"><button class="staff-secondary" @click="editing = false">取消</button><button class="staff-primary" @click="saveFavorites">保存设置</button></view>
      </view>
      <view v-else class="feature-grid">
        <button v-for="item in favorites" :key="item.id" class="feature-card" @click="item.open"><text class="feature-mark">{{ item.mark }}</text><text class="feature-title">{{ item.name }}</text><text class="feature-hint">{{ item.hint }}</text></button>
      </view>
      <view v-if="!editing && !favorites.length" class="staff-panel"><text class="staff-muted">{{ features.length ? '还没有常用功能，可从下方进入，或点击自定义添加。' : '当前账号还没有业务权限，请联系老板分配岗位权限。' }}</text></view>
      <text v-if="preferenceMessage" class="preference-message" role="status">{{ preferenceMessage }}</text>
      <view class="section-head"><text class="section-title">全部业务</text><text class="staff-muted">按当前权限显示</text></view>
      <view v-if="features.length" class="staff-panel link-list"><button v-for="item in features" :key="item.id" class="link-row" @click="item.open"><view><text class="row-title">{{ item.name }}</text><text class="staff-muted">{{ item.hint }}</text></view><text class="chevron">›</text></button></view>
      <view class="section-head"><text class="section-title">工作保障</text></view>
      <view class="staff-panel link-list"><button v-if="canRecover" class="link-row" @click="openRecovery"><view><text class="row-title">核对上次收银</text><text class="staff-muted">收款结果不明确时，先核对再操作</text></view><text class="chevron">›</text></button><button class="link-row" @click="openDeviceCheck"><view><text class="row-title">设备与连接检查</text><text class="staff-muted">检查相机、扫码等手机能力</text></view><text class="chevron">›</text></button></view>
      <button class="logout-button" @click="logout">退出当前账号</button>
    </template>
    <text v-if="error" class="staff-error" role="alert">{{ error }}</text>
  </view>
</template>
<style scoped>
.home-header { padding:16px 0 28px; }
.eyebrow { display:block; color:#1d5d73; font-size:13px; font-weight:600; margin-bottom:12px; }
.greeting { display:block; font-size:24px; line-height:1.45; font-weight:650; letter-spacing:-.3px; }
.store-pill { display:inline-block; margin-top:12px; padding:5px 10px; background:#eaf1f4; color:#1d5d73; border-radius:6px; font-size:13px; max-width:100%; overflow-wrap:anywhere; }
.section-head { display:flex; justify-content:space-between; align-items:center; margin:24px 0 12px; gap:12px; }
.section-title { display:block; font-size:17px; font-weight:600; line-height:1.5; }
.text-button { margin:0; padding:0 4px; min-width:60px; background:transparent; color:#1d5d73; font-size:14px; }
.feature-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; }
.feature-card { display:block; text-align:left; margin:0; padding:18px 16px; background:#fff; border:1px solid #e5e7eb; line-height:1.5; }
.feature-mark { display:flex; width:36px; height:36px; justify-content:center; align-items:center; border-radius:10px; color:#1d5d73; background:#eaf1f4; font-weight:600; margin-bottom:14px; }
.feature-title { display:block; font-size:17px; font-weight:600; color:#1f2937; }
.feature-hint { display:block; color:#4b5563; font-size:13px; margin-top:6px; line-height:1.65; }
.link-list { padding:0 16px; }
.link-row { width:100%; display:flex; justify-content:space-between; align-items:center; padding:16px 0; text-align:left; background:transparent; border-radius:0; border-bottom:1px solid #e5e7eb; margin:0; line-height:1.5; }
.link-row:last-child { border-bottom:0; }
.row-title { font-size:16px; font-weight:550; color:#1f2937; }
.chevron { color:#626f7e; font-size:25px; margin-left:12px; }
.logout-button { background:transparent; color:#4b5563; margin:24px 0 8px; font-size:14px; }
.preference-option { display:flex; justify-content:space-between; width:100%; margin:8px 0; padding:12px; font-size:15px; color:#1d5d73; background:#f5f6f8; }
.actions { display:flex; gap:12px; margin-top:16px; }.actions button { flex:1; margin:0; }
.preference-message { display:block; color:#4b5563; font-size:13px; margin-top:12px; }
.loading-state { padding:24px; }
</style>

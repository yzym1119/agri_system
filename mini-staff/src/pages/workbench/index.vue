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
const canSale = ref(false),canCases=ref(false);
const openCases=()=>uni.navigateTo({url:'/pages/cases/list/index'});
const openInventory = () => uni.navigateTo({ url:'/pages/scan/index' });
const openReceiving = () => uni.navigateTo({url:'/pages/receipt/list/index'});
const openDelivery = () => uni.navigateTo({url:'/pages/delivery/list/index'});
const canPickup=ref(false),canOwnOrders=ref(false);
const openOrders=()=>uni.navigateTo({url:'/pages/orders/list/index'});
const openPickup=()=>uni.navigateTo({url:'/pages/pickup/list/index'});
const featureIcon=(id:string)=>({sale:'bag',inventory:'scan',receipt:'doc',pickup:'box',delivery:'truck',orders:'doc',cases:'doc'} as Record<string,string>)[id]??'box';
// 个人常用项只改变布局，实际入口始终取本次刷新后的权限交集。
const favoriteIds = ref<string[] | null>(null), draftIds = ref<string[]>([]);
const editing = ref(false), preferenceMessage = ref('');
let preferenceKey = '', active = false, actorEpoch = -1;
const features = computed(() => [
  { id:'cases',name:'农技问题',hint:'查看客户问题与门店答复',mark:'问',allowed:canCases.value,open:openCases },
  { id:'orders', name:'本人订单', hint:'查看自己开出的订单和账款', mark:'单', allowed:canOwnOrders.value, open:openOrders },
  { id:'sale', name:'销售开单', hint:'找商品、选客户，核对现金开单', mark:'售', allowed:canSale.value, open:openSale },
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
  canPickup.value=false;canOwnOrders.value=false;canCases.value=false;
  try {
    await staffAuth.refresh();
    if (epoch !== current) return;
    const actor = staffAuth.current();
    if (!actor) throw Error('UNAUTHORIZED');
    actorEpoch = actor.epoch;
    name.value = actor.name; store.value = actor.storeId;
    canRecover.value = actor.permissions.includes('*') || actor.permissions.includes('pos:order:create');
    canSale.value = canRecover.value;
    canCases.value=actor.permissions.includes('agronomy:case:query');
    canOwnOrders.value=actor.permissions.includes('*')||actor.permissions.includes('order:own:query');
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
  <view class="page">
    <view v-if="checking" class="card">
      <view class="row">
        <view class="hero-avatar hero-avatar-quiet"><AppIcon name="user" :size="20" /></view>
        <view class="grow"><text class="t-sub">正在核对员工与门店</text><text class="t-cap">请稍候</text></view>
      </view>
    </view>

    <template v-else-if="name">
      <view class="card-hero">
        <view class="row">
          <view class="hero-avatar"><AppIcon name="store" :size="22" /></view>
          <view class="grow">
            <text class="hero-name">{{ name }}</text>
            <text class="hero-meta">门店 {{ store }}</text>
          </view>
          <button hover-class="btn-hover" hover-stay-time="60" class="hero-exit" @click="logout">退出</button>
        </view>
      </view>

      <view class="section section-first">
        <view class="section-head">
          <text class="section-title">常用业务</text>
          <button class="btn btn-quiet btn-sm btn-auto" @click="configureFavorites">自定义</button>
        </view>
      </view>

      <view v-if="editing" class="card">
        <text class="t-sub">选择这台手机的常用功能</text>
        <text class="t-cap">仅调整入口，不改变岗位权限。</text>
        <button v-for="item in features" :key="item.id" class="btn btn-ghost mt8" :aria-label="item.name+(draftIds.includes(item.id)?'，已选择':'，未选择')" @click="toggleFavorite(item.id)">{{ item.name }} · {{ draftIds.includes(item.id)?'已选择 ✓':'选择' }}</button>
        <view class="row mt12"><button class="btn btn-ghost grow mr8" @click="editing=false">取消</button><button class="btn btn-primary grow" @click="saveFavorites">保存设置</button></view>
      </view>
      <view v-else class="tiles">
        <view v-for="item in favorites" :key="item.id" class="tile"><view hover-class="tile-hover" class="tile-box" @click="item.open"><view class="tile-icon"><AppIcon :name="featureIcon(item.id)" :size="22" /></view><text class="tile-name">{{ item.name }}</text><text class="tile-desc">{{ item.hint }}</text></view></view>
      </view>
      <text v-if="!editing && !favorites.length && features.length" class="t-cap">尚未设置常用项，可从下方全部业务进入。</text>
      <view v-if="preferenceMessage" class="alert alert-info" role="status"><text>{{ preferenceMessage }}</text></view>
      <view v-if="features.length" class="section"><text class="section-title">全部业务</text><text class="section-note">按当前权限显示</text></view>
      <view v-if="features.length" class="card card-flat"><view v-for="item in features" :key="item.id" class="list-item row-between" hover-class="tap-hover" @click="item.open"><view class="grow"><text class="t-sub">{{ item.name }}</text><text class="t-cap">{{ item.hint }}</text></view><AppIcon name="chevron" :size="12" /></view></view>

      <view v-if="!features.length" class="card empty">
        <view class="empty-icon"><AppIcon name="user" :size="20" /></view>
        <text class="empty-text">当前账号还没有业务权限，请联系店长分配后再进入。</text>
      </view>

      <view class="section"><text class="section-title">工作保障</text></view>
      <view class="card card-flat">
        <view v-if="canRecover" class="list-item row-between" hover-class="tap-hover" @click="openRecovery"><view class="row"><AppIcon name="clock" :size="20" class="t-primary" /><view class="ml10"><text class="t-sub">核对上次收银</text><text class="t-cap">结果不明确时先核对原请求</text></view></view><AppIcon name="chevron" :size="14" /></view>
        <view hover-class="tap-hover" hover-stay-time="80" class="list-item list-item-last row-between" @click="openDeviceCheck">
          <view class="row">
            <AppIcon name="device" :size="20" class="t-primary" />
            <view class="ml10">
              <text class="t-sub">检查这台手机</text>
              <text class="t-cap">扫码、录音、存储与网络</text>
            </view>
          </view>
          <AppIcon name="chevron" :size="14" class="chev" />
        </view>
      </view>
    </template>

    <view v-if="error" class="alert alert-danger">
      <AppIcon name="alert" :size="16" class="alert-icon" />
      <text>{{ error }}</text>
    </view>
  </view>
</template>

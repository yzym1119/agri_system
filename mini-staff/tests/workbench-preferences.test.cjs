const { test } = require('node:test');
const assert = require('node:assert/strict');
const { setupPage } = require('./helpers/page.cjs');
const keys = values => Array.from(values, item => item.id);
function fixture(permissions, saved, platform = {}) {
  const actor = { staffId:'11', storeId:'3', name:'验收员工', epoch:1, permissions };
  const writes=[];
  const page=setupPage('workbench', { refresh:async()=>{}, current:()=>actor }, true, {}, {
    getStorageSync:key=>saved?.[key], setStorageSync:(key,value)=>writes.push({key,value}), ...platform,
  });
  return { page, actor, writes };
}
test('常用功能始终与最新权限取交集，存储篡改不能增加入口', async()=>{
  const { page, actor }=fixture(['inventory:view'], {'staff-home-v1:11:3':{version:1,ids:['sale','inventory','receipt']}});
  await page.hooks.show();
  assert.deepEqual(keys(page.state.features.value),['inventory']);
  assert.deepEqual(keys(page.state.favorites.value),['inventory']);
  actor.permissions=['purchase:inbound'];await page.hooks.show();
  assert.deepEqual(keys(page.state.favorites.value),['receipt']);
});
test('个人常用配置按员工门店保存，空选项有效且未授权ID不落盘', async()=>{
  const { page,actor,writes }=fixture(['inventory:view']);await page.hooks.show();
  page.state.configureFavorites();page.state.toggleFavorite('sale');page.state.toggleFavorite('inventory');
  page.state.saveFavorites();
  assert.equal(writes[0].key,'staff-home-v1:11:3');assert.deepEqual(Array.from(writes[0].value.ids),[]);
  assert.equal(page.state.favorites.value.length,0);
  actor.storeId='4';await page.hooks.show();
  assert.deepEqual(keys(page.state.favorites.value),['inventory']);
});
test('切换账号后旧页面不能保存到他人配置，存储失败可重试', async()=>{
  let attempts=0;
  const {page,actor,writes}=fixture(['inventory:view'],null,{setStorageSync:()=>{attempts++;throw Error('storage unavailable');}});
  await page.hooks.show();page.state.configureFavorites();page.state.saveFavorites();
  assert.match(page.state.preferenceMessage.value,/未能保存/);assert.equal(page.state.editing.value,true);
  actor.epoch=2;page.state.saveFavorites();assert.equal(attempts,1);
  actor.epoch=1;page.hooks.hide();page.state.saveFavorites();assert.equal(attempts,1);
  await page.hooks.show();
  actor.staffId='12';page.state.saveFavorites();assert.equal(attempts,1);assert.equal(writes.length,0);
});

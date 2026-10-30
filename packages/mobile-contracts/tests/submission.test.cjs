const { test } = require('node:test');
const assert = require('node:assert/strict');
const KEY = '11111111-1111-4111-8111-111111111111';
const summary = () => ({ totalCents: 5000, netCents: 5000, paymentMethod: 1, customerId: null,
  lines: [{ productId: '1', packageId: null, unit: '袋', quantity: '1', lineCents: 5000 }] });
const complete = (id = 9) => ({ requestKey: KEY, status: 'COMPLETED', orderId: id });
async function fixture(overrides = {}) {
  const { createPOSRecovery } = await import('../dist/submission.js');
  const values = new Map();
  let actor = { staffId: '1', storeId: '1', epoch: 1 };
  const storage = { readStorage: key => values.get(key) ?? null, writeStorage: (key,value) => values.set(key,value), removeStorage: key => values.delete(key), ...overrides };
  const create = (random = async () => KEY) => createPOSRecovery(storage, () => actor, random);
  return { values, storage, create, recovery: create(), switchActor: value => { actor = value; } };
}
test('原摘要先持久化，发送只一次；保存白名单且不落密码验证码', async () => {
  const f = await fixture();
  const input = { ...summary(), password: 'secret', walletAuthCode: '123456' };
  const result = await f.recovery.submitOnce(input, async (key, actor) => {
    assert.equal(key, KEY); assert.equal(actor.staffId, '1');
    assert.equal(f.values.size, 1);
    assert.ok([...f.values.values()][0].includes(KEY));
    assert.doesNotMatch([...f.values.values()][0], /secret|123456|password|AuthCode/);
    return 9;
  });
  assert.equal(result.status, 'COMPLETED');
  assert.equal(f.recovery.restore().requestKey, KEY);
  await assert.rejects(f.recovery.finishCompleted('9', false, async () => complete()), /CONFIRMATION_REQUIRED/);
  await f.recovery.finishCompleted('9', true, async () => complete());
  assert.equal(f.recovery.restore(), null);
});
test('双击与重启不生成第二请求；超时后只能核原请求', async () => {
  const f = await fixture(); let finish, calls = 0, keys = 0;
  const recovery = f.create(async () => { keys++; return KEY; });
  const pending = recovery.submitOnce(summary(), () => { calls++; return new Promise(resolve => finish = resolve); });
  await Promise.resolve(); await Promise.resolve();
  await assert.rejects(recovery.submitOnce(summary(), async () => { calls++; return 10; }), /RECOVERY_REQUIRED|BUSY/);
  finish(undefined); assert.equal((await pending).status, 'VERIFYING');
  const restarted = f.create(async () => { keys++; return KEY; });
  await assert.rejects(restarted.submitOnce(summary(), async () => 10), /RECOVERY_REQUIRED/);
  assert.equal(keys, 1); assert.equal(calls, 1);
  assert.equal((await restarted.lookupOriginal(async key => { assert.equal(key, KEY); return complete(); })).orderId, '9');
});
test('读取失败、写入失败和回读丢失均在发送之前拒绝', async () => {
  for (const storage of [
    { readStorage: () => { throw Error(); } },
    { writeStorage: () => { throw Error(); } },
    { writeStorage: () => {} },
  ]) {
    const f = await fixture(storage); let calls = 0;
    await assert.rejects(f.recovery.submitOnce(summary(), async () => { calls++; return 9; }), /STORAGE_BLOCKED/);
    assert.equal(calls, 0);
  }
});
test('UNKNOWN和取消失败不解冻；取消返回已成交也必须保留原单', async () => {
  const f = await fixture();
  await f.recovery.submitOnce(summary(), async () => { throw Error('timeout'); });
  assert.equal((await f.recovery.lookupOriginal(async () => ({requestKey:KEY,status:'UNKNOWN',orderId:null}))).status, 'UNKNOWN');
  await assert.rejects(f.recovery.cancelPending(true, async () => { throw Error(); }), /RESULT_UNKNOWN/);
  assert.equal((await f.recovery.cancelPending(true, async () => complete())).status, 'COMPLETED');
  assert.ok(f.recovery.restore());
  await f.recovery.cancelPending(true, async () => ({requestKey:KEY,status:'CANCELLED',orderId:null}));
  assert.equal(f.recovery.restore(), null);
});
test('删除失败或已观察记录被替换必须持续冻结，不能用不存在证明结束', async () => {
  const f = await fixture({ removeStorage: () => {} });
  await f.recovery.submitOnce(summary(), async () => undefined);
  await assert.rejects(f.recovery.finishCompleted('9', true, async () => complete()), /STORAGE_BLOCKED/);
  const key = [...f.values.keys()][0]; f.values.delete(key);
  await assert.rejects(f.recovery.submitOnce(summary(), async () => 10), /STORAGE_BLOCKED/);
  const other = await fixture(); await other.recovery.submitOnce(summary(), async () => undefined);
  const k = [...other.values.keys()][0];
  const changed = JSON.parse(other.values.get(k)); changed.summary.netCents = 1;
  other.values.set(k, JSON.stringify(changed));
  assert.throws(() => other.recovery.restore(), /STORAGE_BLOCKED/);
});
test('原请求核对必须匹配UUID与原订单，再次读取变化不能结束', async () => {
  const f = await fixture(); await f.recovery.submitOnce(summary(), async () => undefined);
  await assert.rejects(f.recovery.lookupOriginal(async () => ({...complete(),requestKey:'22222222-2222-4222-8222-222222222222'})), /RESPONSE_INVALID/);
  await assert.rejects(f.recovery.finishCompleted('9', true, async () => complete(10)), /RESULT_CHANGED/);
  await assert.rejects(f.recovery.lookupOriginal(async () => complete(9007199254740992)), /RESPONSE_INVALID/);
  assert.ok(f.recovery.restore());
});
test('同员工重登恢复原记录，换店换人隔离；旧结果不能污染新会话', async () => {
  const f = await fixture(); let resolve;
  const pending = f.recovery.submitOnce(summary(), () => new Promise(done => resolve = done));
  await Promise.resolve(); await Promise.resolve();
  f.switchActor({staffId:'2',storeId:'2',epoch:2}); resolve(9);
  await assert.rejects(pending, /STALE_SESSION/);
  assert.equal(f.recovery.restore(), null);
  f.switchActor({staffId:'1',storeId:'1',epoch:3});
  assert.equal(f.recovery.restore().requestKey, KEY);
  let resolveRead;
  const read = f.recovery.lookupOriginal(() => new Promise(done => resolveRead = done));
  f.switchActor({staffId:'2',storeId:'2',epoch:4}); resolveRead(complete());
  await assert.rejects(read, /STALE_SESSION/);
  assert.equal(f.recovery.restore(), null);
});
test('安全UUID生成过程中换员工不保存不发送，摘要在等待前固定', async () => {
  const f = await fixture(); let resolve;
  const recovery = f.create(() => new Promise(done => resolve = done));
  const input = summary(); let submitted = 0;
  const pending = recovery.submitOnce(input, async () => {submitted++; return 9;});
  f.switchActor({staffId:'2',storeId:'2',epoch:2}); resolve(KEY);
  await assert.rejects(pending, /STALE_SESSION/); assert.equal(f.values.size, 0); assert.equal(submitted, 0);
  const other = await fixture(); let ready;
  const r = other.create(() => new Promise(done => ready = done));
  const original = summary(); const work = r.submitOnce(original, async () => 9);
  original.lines[0].quantity='99'; ready(KEY); await work;
  assert.equal(r.restore().summary.lines[0].quantity, '1');
});
test('非法摘要或超精度数量拒绝；合法0元物料仍可保留数量', async () => {
  for (const invalid of [ { ...summary(), netCents: NaN }, { ...summary(), netCents: 5001 },
    { ...summary(), lines: [{...summary().lines[0],quantity:'0.00001'}] },
    { ...summary(), lines: [] } ]) {
    const f = await fixture(); await assert.rejects(f.recovery.submitOnce(invalid, async () => 9), /SUMMARY_INVALID/);
    assert.equal(f.values.size,0);
  }
  const f = await fixture(); const free = summary(); free.netCents=0; free.lines[0].lineCents=0;
  await f.recovery.submitOnce(free, async () => 9); assert.equal(f.recovery.restore().summary.lines[0].quantity,'1');
});
test('无法恢复的超长ID摘要必须在写入和发送之前拒绝', async () => {
  const f = await fixture(); let sends=0;
  const value=summary();value.lines[0].productId='1'.repeat(200001);
  await assert.rejects(f.recovery.submitOnce(value,async()=>{sends++;return 9;}),/SUMMARY_INVALID/);
  assert.equal(sends,0);assert.equal(f.values.size,0);
});
test('发送回调不能改写捕获的员工作用域以接收另一会话的旧结果', async()=>{
  const f=await fixture();let captured,resolve;
  const pending=f.recovery.submitOnce(summary(),(_,actor)=>{captured=actor;return new Promise(done=>resolve=done);});
  await Promise.resolve();await Promise.resolve();
  const next={staffId:'2',storeId:'2',epoch:2};f.switchActor(next);
  try { Object.assign(captured,next); } catch {}
  resolve(9);await assert.rejects(pending,/STALE_SESSION/);
});
test('结束A请求时即使外部回调改写参数，也不能删除B的待核对记录', async()=>{
  const f=await fixture();await f.recovery.submitOnce(summary(),async()=>undefined);
  const a={staffId:'1',storeId:'1',epoch:1}, b={staffId:'2',storeId:'2',epoch:2};
  f.switchActor(b);
  const other=f.create(async()=>'22222222-2222-4222-8222-222222222222');
  await other.submitOnce(summary(),async()=>undefined);
  f.switchActor(a);
  const ending=f.recovery.finishCompleted('9',true,async(_,captured)=>{
    f.switchActor(b);try {Object.assign(captured,b);} catch {}
    return complete();
  });
  await assert.rejects(ending,/STALE_SESSION/);
  assert.equal(f.values.size,2);
});

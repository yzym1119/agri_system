const { test } = require('node:test');
const assert = require('node:assert/strict');
const load = import('../dist/platform.js');
function port(overrides = {}) {
  const values = new Map();
  return {
    scanCode: ({ success }) => success({ result: '000123', scanType: 'EAN_13' }),
    getStorageInfoSync: () => ({ keys: [...values.keys()] }),
    getStorageSync: k => values.get(k),
    setStorageSync: (k, v) => values.set(k, v),
    removeStorageSync: k => values.delete(k),
    openLocation: ({ success }) => success(),
    ...overrides,
  };
}
test('扫描保留前导零，取消与拒绝相机分别返回', async () => {
  const { createMobilePlatform } = await load;
  assert.deepEqual(await createMobilePlatform(port()).scan(), { status: 'SCANNED', code: '000123', scanType: 'EAN_13' });
  for (const [errMsg, status] of [['scanCode:fail cancel', 'CANCELLED'], ['scanCode:fail auth deny', 'DENIED']]) {
    const p = createMobilePlatform(port({ scanCode: ({ fail }) => fail({ errMsg }) }));
    assert.equal((await p.scan()).status, status);
  }
});
test('异常和空扫描不能误当识别成功，重复点击不能启动第二个扫码', async () => {
  const { createMobilePlatform } = await load;
  await assert.rejects(createMobilePlatform(port({ scanCode: ({ success }) => success({ result: '' }) })).scan(), /SCAN_INVALID/);
  await assert.rejects(createMobilePlatform(port({ scanCode: ({ fail }) => fail({ errMsg: 'device failure' }) })).scan(), /SCAN_FAILED/);
  let done, calls = 0;
  const p = createMobilePlatform(port({ scanCode: o => { calls++; done = o.success; } }));
  const first = p.scan();
  await assert.rejects(p.scan(), /SCAN_BUSY/);
  done({ result: 'BATCH-1', scanType: 'QR_CODE' });
  await first;
  assert.equal(calls, 1);
  const next = p.scan();
  done({ result: 'BATCH-2', scanType: 'QR_CODE' });
  assert.equal((await next).code, 'BATCH-2');
  assert.equal(calls, 2);
});
test('存储缺失返回null，写入后必须回读相同数据，删除也核实', async () => {
  const { createMobilePlatform } = await load;
  const p = createMobilePlatform(port());
  assert.equal(p.readStorage('pending'), null);
  p.writeStorage('pending', '原请求');
  assert.equal(p.readStorage('pending'), '原请求');
  p.removeStorage('pending');
  assert.equal(p.readStorage('pending'), null);
  assert.throws(() => createMobilePlatform(port({ setStorageSync: () => {} })).writeStorage('pending', 'x'), /STORAGE/);
  assert.throws(() => createMobilePlatform(port({ getStorageInfoSync: () => { throw Error('denied'); } })).readStorage('pending'), /STORAGE/);
});
test('无安全随机源/错误长度拒绝，不以Math.random兜底；合法结果生成UUIDv4', async () => {
  const { createMobilePlatform } = await load;
  await assert.rejects(createMobilePlatform(port()).secureRandom(16), /RANDOM_UNAVAILABLE/);
  const invalid = createMobilePlatform(port(), { getRandomValues: o => o.success({ randomValues: new ArrayBuffer(2) }) });
  await assert.rejects(invalid.secureRandom(16), /RANDOM_INVALID/);
  const p = createMobilePlatform(port(), { getRandomValues: o => o.success({ randomValues: new Uint8Array(o.length).fill(255).buffer }) });
  assert.equal(await p.createRequestKey(), 'ffffffff-ffff-4fff-bfff-ffffffffffff');
  await assert.rejects(p.secureRandom(0), /RANDOM_SIZE/);
});
test('导航只使用明确GCJ02坐标，未知坐标保留地址供人工使用', async () => {
  const { createMobilePlatform } = await load;
  let calls = 0;
  const p = createMobilePlatform(port({ openLocation: o => { calls++; assert.equal(o.latitude, 46.5); o.success(); } }));
  assert.deepEqual(await p.navigateToAddress('测试地址'), { status: 'ADDRESS_ONLY', address: '测试地址' });
  await assert.rejects(p.navigateToAddress('测试地址', { latitude: 46.5, longitude: 125, system: 'WGS84' }), /COORDINATE_INVALID/);
  assert.equal(calls, 0);
  assert.equal((await p.navigateToAddress('测试地址', { latitude: 46.5, longitude: 125, system: 'GCJ02' })).status, 'OPENED');
});

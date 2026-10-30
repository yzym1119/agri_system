const { test } = require('node:test');
const assert = require('node:assert/strict');
async function fixture(overrides = {}) {
  const { createRecorderProbe } = await import('../dist/recorder.js');
  const handlers = {};
  let starts = 0, stops = 0;
  const probe = createRecorderProbe({
    onStart: cb => handlers.start = cb,
    onStop: cb => handlers.stop = cb,
    onError: cb => handlers.error = cb,
    start: options => { starts++; assert.equal(options.duration, 10000); },
    stop: () => { stops++; },
    ...overrides,
  });
  return { probe, handlers, counts: () => ({ starts, stops }) };
}
test('录音仅用户启动，双击不重复启动，停止回调才显示可试听', async () => {
  const { probe, handlers, counts } = await fixture();
  probe.show();
  assert.equal(counts().starts, 0);
  probe.start(); probe.start();
  assert.equal(counts().starts, 1);
  assert.equal(probe.snapshot().phase, 'STARTING');
  handlers.start();
  assert.equal(probe.snapshot().phase, 'RECORDING');
  probe.stop();
  assert.equal(probe.snapshot().phase, 'STOPPING');
  handlers.stop({ tempFilePath: 'wxfile://recording', duration: 1000 });
  assert.equal(probe.snapshot().clip, 'wxfile://recording');
});
test('停止调用失败不能推断麦克风已停，禁止启动第二段录音', async () => {
  const { probe, handlers, counts } = await fixture({ stop: () => { throw Error('unknown'); } });
  probe.show(); probe.start(); handlers.start(); probe.stop(); probe.start();
  assert.equal(probe.snapshot().phase, 'STOPPING');
  assert.equal(counts().starts, 1);
});
test('隐藏时终止录音并清空，返回页面也不接收旧文件或重复开录', async () => {
  const { probe, handlers, counts } = await fixture();
  probe.show(); probe.start(); handlers.start(); probe.hide(); probe.show();
  probe.start();
  assert.equal(counts().starts, 1);
  handlers.stop({ tempFilePath: 'wxfile://old', duration: 2000 });
  assert.equal(probe.snapshot().clip, '');
  assert.equal(probe.snapshot().phase, 'IDLE');
  probe.start(); assert.equal(counts().starts, 2);
});
test('授权过程中隐藏，迟到onStart必须再次停止且不恢复录音状态', async () => {
  const { probe, handlers, counts } = await fixture();
  probe.show(); probe.start(); probe.hide(); handlers.start();
  assert.equal(probe.snapshot().phase, 'STOPPING');
  assert.equal(counts().stops, 2);
});
test('拒绝录音/无效文件是错误，失败后可重试；清除后不能试听', async () => {
  const { probe, handlers } = await fixture();
  probe.show(); probe.start(); handlers.error({ errMsg: 'auth deny' });
  assert.equal(probe.snapshot().phase, 'ERROR');
  assert.equal(probe.snapshot().clip, '');
  probe.start(); handlers.start();
  handlers.stop({ tempFilePath: '', duration: 1000 });
  assert.equal(probe.snapshot().phase, 'ERROR');
  probe.start(); handlers.start(); handlers.stop({ tempFilePath: 'wxfile://ok', duration: 1000 });
  probe.clear(); assert.equal(probe.snapshot().clip, '');
});
test('页面取消订阅后不再收到事件，重新订阅只收到当前状态', async () => {
  const { probe, handlers } = await fixture();
  let calls = 0;
  const unsubscribe = probe.subscribe(() => calls++);
  unsubscribe(); const previous = calls;
  probe.show(); probe.start(); handlers.start(); handlers.stop({ tempFilePath: 'wxfile://a', duration: 1000 });
  assert.equal(calls, previous);
  let state;
  probe.subscribe(value => state = value);
  assert.equal(state.clip, 'wxfile://a');
});

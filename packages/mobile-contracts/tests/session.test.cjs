const { test } = require('node:test');
const assert = require('node:assert/strict');
const load = () => import('../dist/session.js');
const login = (id = 1, storeId = 1) => ({ tokenName: 'Authorization', tokenValue: 'test-token-' + id,
  userInfo: { id, name: '虚构员工', permissions: ['inventory:view'], extra: { storeId } } });
const isNumber = v => typeof v === 'number';
test('身份ID保留字符串，不能接收丢精度数字、空门店或空权限格式', async () => {
  const { decodeLogin } = await load();
  assert.equal(decodeLogin(login('9007199254740993')).staffId, '9007199254740993');
  assert.throws(() => decodeLogin(login(9007199254740992)), /IDENTITY_INVALID/);
  assert.throws(() => decodeLogin(login(1, null)), /IDENTITY_INVALID/);
  const payload = login(); payload.userInfo.permissions = null;
  assert.throws(() => decodeLogin(payload), /IDENTITY_INVALID/);
});
test('新登录开始后旧登录成功/失效都不能改新员工，权限副本不可外部篡改', async () => {
  const { createActorSession } = await load();
  const session = createActorSession();
  const a = session.beginLogin(), b = session.beginLogin();
  assert.equal(session.acceptLogin(b, login(2)), true);
  assert.equal(session.acceptLogin(a, login(1)), false);
  assert.equal(session.revoke(a), false);
  const current = session.current();
  current.permissions.push('role:assign');
  assert.deepEqual(session.current().permissions, ['inventory:view']);
});
test('同一登录票据只能接收一次，重复成功不能替换当前员工', async () => {
  const { createActorSession } = await load();
  const session = createActorSession(), ticket = session.beginLogin();
  assert.equal(session.acceptLogin(ticket, login(1)), true);
  assert.equal(session.acceptLogin(ticket, login(2)), false);
  assert.equal(session.current().staffId, '1');
});
test('按端点显式解包，0不能被当空值；空成功结果拒绝', async () => {
  const { decodeResponse } = await load();
  assert.equal(decodeResponse('wrapped', { statusCode: 200, data: { code: 0, data: 0 } }, isNumber), 0);
  assert.throws(() => decodeResponse('wrapped', { statusCode: 200, data: 7 }, isNumber), /RESPONSE_INVALID/);
  assert.throws(() => decodeResponse('raw', { statusCode: 200 }, isNumber), /RESPONSE_INVALID/);
  const raw = { code: 'SKU001' };
  assert.equal(decodeResponse('raw', { statusCode: 200, data: raw }, v => v === raw), raw);
});
test('旧会话成功和401都丢弃，不登出新会话；当前失效码10003应清会话', async () => {
  const { createActorSession, requestInSession } = await load();
  for (const response of [{ statusCode: 200, data: 1 }, { statusCode: 401 }]) {
    const session = createActorSession(); session.acceptLogin(session.beginLogin(), login(1));
    let resolve;
    const pending = requestInSession(session, () => new Promise(done => resolve = done), 'raw', isNumber);
    session.acceptLogin(session.beginLogin(), login(2)); resolve(response);
    await assert.rejects(pending, /STALE_SESSION/);
    assert.equal(session.current().staffId, '2');
  }
  const session = createActorSession(); session.acceptLogin(session.beginLogin(), login());
  await assert.rejects(requestInSession(session, async () => ({ statusCode: 200, data: { code: 10003, message: '过期', data: null } }), 'raw', isNumber), /UNAUTHORIZED/);
  assert.equal(session.current(), null);
});
test('网络和5xx不自动重试；旧网络错误不污染新会话', async () => {
  const { createActorSession, requestInSession } = await load();
  const session = createActorSession(); session.acceptLogin(session.beginLogin(), login());
  let calls = 0;
  await assert.rejects(requestInSession(session, async () => { calls++; throw Error('timeout'); }, 'raw', isNumber), /RESULT_UNKNOWN/);
  assert.equal(calls, 1);
  await assert.rejects(requestInSession(session, async () => ({ statusCode: 503, data: { code: 0, data: 3 } }), 'wrapped', isNumber), /RESULT_UNKNOWN/);
  let reject;
  const old = requestInSession(session, () => new Promise((_, fail) => reject = fail), 'raw', isNumber);
  session.acceptLogin(session.beginLogin(), login(2)); reject(Error('timeout'));
  await assert.rejects(old, /STALE_SESSION/);
});

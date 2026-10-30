const { test } = require('node:test');
const assert = require('node:assert/strict');
const moduleUnderTest = () => import('../dist/staff-auth.js');
const user = (permissions = ['inventory:view'], storeId = 1) => ({ id: 1, name: '验收员工', permissions, extra: { storeId } });
const loginBody = () => ({ tokenName: 'Authorization', tokenValue: 'fictional-token', userInfo: user() });
test('登录沿现有account接口，密码不进入状态；刷新用当前token且接受空权限', async () => {
  const { createStaffAuth } = await moduleUnderTest();
  const calls = [];
  const auth = createStaffAuth(async req => {
    calls.push(req);
    return { statusCode: 200, data: req.url.endsWith('/login') ? loginBody() : user([]) };
  });
  await auth.login('  tester  ', 'password');
  assert.deepEqual(calls[0].data, { account: 'tester', password: 'password', loginType: 'PASSWORD' });
  await auth.refresh();
  assert.equal(calls[1].header.Authorization, 'fictional-token');
  assert.deepEqual(auth.current().permissions, []);
  assert.equal('password' in auth.current(), false);
});
test('门店变更废弃旧作用域；刷新失败清身份，不能使用旧菜单', async () => {
  const { createStaffAuth } = await moduleUnderTest();
  let response = { statusCode: 200, data: user([], 2) };
  const auth = createStaffAuth(async req => req.url.endsWith('/login') ? { statusCode: 200, data: loginBody() } : response);
  await auth.login('tester','password'); const epoch = auth.current().epoch;
  await auth.refresh();
  assert.equal(auth.current().storeId, '2');
  assert.notEqual(auth.current().epoch, epoch);
  response = { statusCode: 503 };
  await assert.rejects(auth.refresh());
  assert.equal(auth.current(), null);
});
test('旧登录失败不能清除新登录；旧身份刷新失败也不能踢掉新登录', async () => {
  const { createStaffAuth } = await moduleUnderTest();
  let firstReject, count = 0;
  const auth = createStaffAuth(async () => {
    if (++count === 1) return new Promise((_, fail) => firstReject = fail);
    return { statusCode: 200, data: loginBody() };
  });
  const oldLogin = auth.login('first', 'password');
  await auth.login('second', 'password'); firstReject(Error('old network'));
  await assert.rejects(oldLogin, /STALE_SESSION/);
  assert.equal(auth.current().staffId, '1');
  let rejectRefresh;
  const other = createStaffAuth(async req => req.url.endsWith('/login')
    ? { statusCode: 200, data: loginBody() } : new Promise((_, fail) => rejectRefresh = fail));
  await other.login('first', 'password'); const oldRefresh = other.refresh();
  await other.login('second', 'password'); rejectRefresh(Error('timeout'));
  await assert.rejects(oldRefresh, /STALE_SESSION/);
  assert.ok(other.current());
});
test('当前身份读取换人或缺门店必须拒绝并清理，退出先本地失效再远端', async () => {
  const { createStaffAuth } = await moduleUnderTest();
  for (const payload of [{ ...user(), id: 2 }, user([], null)]) {
    const auth = createStaffAuth(async req => ({ statusCode: 200, data: req.url.endsWith('/login') ? loginBody() : payload }));
    await auth.login('test', 'password'); await assert.rejects(auth.refresh()); assert.equal(auth.current(), null);
  }
  let auth;
  auth = createStaffAuth(async req => {
    if (req.url.endsWith('/logout')) { assert.equal(auth.current(), null); throw Error('offline'); }
    return { statusCode: 200, data: loginBody() };
  });
  await auth.login('test', 'password'); await auth.logout(); assert.equal(auth.current(), null);
});

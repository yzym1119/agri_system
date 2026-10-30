const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const { parse, compileScript } = require('@vue/compiler-sfc');
const { createStaffAuth } = require('../../packages/mobile-contracts/dist/staff-auth.js');
const user = { id: 1, name: '虚构验收员工', permissions: [], extra: { storeId: 1 } };
const loginBody = { tokenName: 'Authorization', tokenValue: 'test-token', userInfo: user };
function deferred() { let resolve, reject; const promise = new Promise((ok, fail) => { resolve=ok; reject=fail; }); return {promise,resolve,reject}; }
function setupPage(page, auth, configured = true) {
  const file = path.join(__dirname, '../src/pages',page,'index.vue');
  const { descriptor } = parse(fs.readFileSync(file, 'utf8'));
  const compiled = compileScript(descriptor, { id: page });
  const js = ts.transpileModule(compiled.content, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS } }).outputText;
  const hooks = {}, routes = [], mod = { exports: {} };
  const imports = { vue: require('vue'), '@dcloudio/uni-app': {
    onShow: cb => hooks.show = cb, onHide: cb => hooks.hide = cb,
  }, '../../services/auth': { staffAuth: auth, serviceConfigured: configured } };
  vm.runInNewContext(js, { module: mod, exports: mod.exports, Error, uni: {
    reLaunch: options => routes.push(options.url), navigateTo: options => routes.push(options.url),
  }, require: name => { assert.ok(name in imports, name); return imports[name]; } }, { filename: file });
  return { state: mod.exports.default.setup({}, { expose() {} }), hooks, routes };
}
test('真实登录页面隐藏后晚到成功不得建立身份或跳转', async () => {
  const delayed = deferred(), auth = createStaffAuth(() => delayed.promise);
  const page = setupPage('login', auth);
  page.state.account.value = 'test'; page.state.password.value = 'password';
  const pending = page.state.login(); page.hooks.hide();
  delayed.resolve({ statusCode: 200, data: loginBody }); await pending;
  assert.equal(auth.current(), null); assert.deepEqual(page.routes, []);
  assert.equal(page.state.password.value, '');
});
test('成功跳转触发隐藏时必须保留已确认身份', async () => {
  const auth = createStaffAuth(async () => ({ statusCode: 200, data: loginBody }));
  const page = setupPage('login', auth);
  page.state.account.value = 'test'; page.state.password.value = 'password';
  await page.state.login(); page.hooks.hide();
  assert.equal(auth.current().staffId, '1');
  assert.deepEqual(page.routes, ['/pages/workbench/index']);
});
test('工作台前后台重叠刷新，旧A先回不能使新B过期', async () => {
  const a = deferred(), b = deferred(); let reads = 0;
  const auth = createStaffAuth(req => req.url.endsWith('/login') ? Promise.resolve({statusCode:200,data:loginBody}) : (++reads === 1 ? a.promise : b.promise));
  await auth.login('test', 'password');
  const page = setupPage('workbench', auth);
  const first = page.hooks.show(); page.hooks.hide(); const second = page.hooks.show();
  a.resolve({ statusCode:200, data:user }); await first;
  b.resolve({ statusCode:200, data:user }); await second;
  assert.deepEqual(page.routes, []);
  assert.equal(page.state.name.value, user.name);
  assert.equal(auth.current().staffId, '1');
});
test('旧工作台刷新返回401也不能撤销更新中的新刷新', async () => {
  const a = deferred(), b = deferred(); let reads = 0;
  const auth = createStaffAuth(req => req.url.endsWith('/login') ? Promise.resolve({statusCode:200,data:loginBody}) : (++reads === 1 ? a.promise : b.promise));
  await auth.login('test', 'password');
  const page = setupPage('workbench', auth);
  const first = page.hooks.show(); page.hooks.hide(); const second = page.hooks.show();
  a.resolve({ statusCode:401 }); await first;
  b.resolve({ statusCode:200, data:user }); await second;
  assert.deepEqual(page.routes, []); assert.ok(auth.current());
});
test('真实登录页面断网应提示网络，不误报账号密码', async () => {
  const auth = createStaffAuth(async () => { throw Error('NETWORK_FAILED'); });
  const page = setupPage('login', auth);
  page.state.account.value = 'test'; page.state.password.value = 'password';
  await page.state.login();
  assert.match(page.state.error.value, /网络/);
  assert.doesNotMatch(page.state.error.value, /核对账号密码/);
});
test('未配置服务时页面不会发送登录请求', async () => {
  let calls = 0;
  const page = setupPage('login', createStaffAuth(async () => { calls++; throw Error(); }), false);
  page.state.account.value = 'test'; page.state.password.value = 'password';
  await page.state.login(); assert.equal(calls, 0);
});

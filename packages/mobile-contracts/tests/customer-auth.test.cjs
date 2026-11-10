const { test } = require('node:test');
const assert = require('node:assert/strict');
const load = () => import('../dist/customer-auth.js');
const key = '11111111-1111-4111-8111-111111111111';
const other = '22222222-2222-4222-8222-222222222222';
const now = 1800000000;
const login = () => ({ tokenName: 'Authorization', tokenValue: 'fictional-customer-token', userInfo: {
  id: '9223372036854775807', name: '验收农户', role: 'NORMAL', permissions: null,
  extra: { bindingState: 'LINKED', phone: '13800000000', vipLevel: 0, code: 'fictional' },
} });
const flow = (state = 'PHONE_REQUIRED') => ({ state, login: null, tokenName: 'Authorization', tokenValue: 'fictional-short-token',
  expiresAt: now + 600, phoneMasked: state === 'PHONE_VERIFIED' ? '138****0000' : null });

test('客户登录白名单不保留原手机号或员工权限，安全Long无精度丢失', async () => {
  const { decodeCustomerLogin } = await load();
  const source = login(); const result = decodeCustomerLogin(source);
  assert.deepEqual(result, { tokenName: 'Authorization', tokenValue: 'fictional-customer-token',
    customerId: '9223372036854775807', name: '验收农户', role: 'NORMAL' });
  source.userInfo.id = 7; source.userInfo.role = 'VIP';
  assert.equal(decodeCustomerLogin(source).customerId, '7');
  assert.equal(decodeCustomerLogin(source).role, 'VIP');
  assert.equal(JSON.stringify(result).includes('13800000000'), false);
});
test('员工、开发身份、缺失绑定或不安全ID不能当成真实客户登录', async () => {
  const { decodeCustomerLogin } = await load();
  for (const change of [v => v.userInfo.role = 'ADMIN', v => v.userInfo.extra.bindingState = 'DEV_ONLY',
    v => delete v.userInfo.extra, v => v.userInfo.permissions = ['order:query'],
    v => v.userInfo.id = 9223372036854775807, v => v.userInfo.id = '9223372036854775808',
    v => v.tokenName = 'Cookie', v => v.tokenValue = 'token\r\nHeader: value']) {
    const value = login(); change(value); assert.throws(() => decodeCustomerLogin(value), /CUSTOMER_RESPONSE_INVALID/);
  }
});
test('已关联响应才给客户登录；手机号授权只是独立临时凭据', async () => {
  const { decodeCustomerOnboarding } = await load();
  assert.equal(decodeCustomerOnboarding({ state: 'LINKED', login: login() }, 'issued').login.customerId, '9223372036854775807');
  for (const state of ['PHONE_REQUIRED', 'PHONE_VERIFIED']) {
    const result = decodeCustomerOnboarding(flow(state), 'issued');
    assert.equal(result.state, state); assert.equal('login' in result, false); assert.equal('customerId' in result, false);
    assert.equal(result.expiresAt, now + 600); assert.equal(result.tokenValue, 'fictional-short-token');
  }
});
test('GET授权阶段不产生新令牌，读取响应不可冒充签发响应', async () => {
  const { decodeCustomerOnboarding } = await load();
  const info = { ...flow('PHONE_VERIFIED'), tokenName: null, tokenValue: null };
  assert.deepEqual(decodeCustomerOnboarding(info, 'info'), {
    state: 'PHONE_VERIFIED', expiresAt: now + 600, phoneMasked: '138****0000',
  });
  assert.throws(() => decodeCustomerOnboarding(info, 'issued'), /CUSTOMER_RESPONSE_INVALID/);
  assert.throws(() => decodeCustomerOnboarding(flow(), 'info'), /CUSTOMER_RESPONSE_INVALID/);
  assert.throws(() => decodeCustomerOnboarding({ state: 'LINKED', login: login() }, 'info'), /CUSTOMER_RESPONSE_INVALID/);
});
test('混合状态、未脱敏号码、秒毫秒混淆和非法截止必须拒绝', async () => {
  const { decodeCustomerOnboarding } = await load();
  for (const change of [v => v.state = 'CREATED', v => v.login = login(), v => v.phoneMasked = '13800000000',
    v => v.expiresAt *= 1000, v => v.expiresAt = 0, v => v.expiresAt = String(now + 600),
    v => v.expiresAt += 0.5]) {
    const value = flow(); change(value); assert.throws(() => decodeCustomerOnboarding(value, 'issued'), /CUSTOMER_RESPONSE_INVALID/);
  }
  assert.throws(() => decodeCustomerOnboarding(flow(), 'unexpected'), /CUSTOMER_RESPONSE_INVALID/);
  assert.throws(() => decodeCustomerOnboarding({ state: 'LINKED', login: login(), tokenValue: 'short' }, 'issued'), /CUSTOMER_RESPONSE_INVALID/);
  assert.throws(() => decodeCustomerOnboarding({ ...flow('PHONE_VERIFIED'), phoneMasked: null }, 'issued'), /CUSTOMER_RESPONSE_INVALID/);
});
test('手机本地时间不决定平台授权资格，截止仅保留后端epoch秒而不推算新期限', async () => {
  const { decodeCustomerOnboarding } = await load();
  const reply = flow('PHONE_VERIFIED');
  const original = Date.now;
  try {
    Date.now = () => (now + 3600) * 1000;
    assert.equal(decodeCustomerOnboarding(reply, 'issued').expiresAt, now + 600);
    Date.now = () => (now - 3600) * 1000;
    assert.equal(decodeCustomerOnboarding(reply, 'issued').expiresAt, now + 600);
  } finally { Date.now = original; }
});
test('开户和关联结果仅返回后续动作，UNKNOWN固定原请求、不产生客户令牌', async () => {
  const { decodeCustomerEnrollment } = await load();
  for (const [status, nextAction] of [['CREATED','LOGIN'],['LINKED','LOGIN'],['REVIEW_REQUIRED','CONTACT_STORE'],['REJECTED','RESTART'],['UNKNOWN','RETRY_SAME_REQUEST']]) {
    const result = decodeCustomerEnrollment({ requestKey: key, status, nextAction, customerId: 1 }, key);
    assert.deepEqual(result, { requestKey: key, status, nextAction }); assert.equal('tokenValue' in result, false);
  }
  assert.throws(() => decodeCustomerEnrollment({ requestKey: other, status: 'CREATED', nextAction: 'LOGIN' }, key), /CUSTOMER_REQUEST_MISMATCH/);
  assert.throws(() => decodeCustomerEnrollment({ requestKey: key, status: 'UNKNOWN', nextAction: 'LOGIN' }, key), /CUSTOMER_RESPONSE_INVALID/);
  assert.throws(() => decodeCustomerEnrollment({ requestKey: key, status: 'PROCESSING', nextAction: 'LOGIN' }, key), /CUSTOMER_RESPONSE_INVALID/);
  assert.throws(() => decodeCustomerEnrollment({ requestKey: key, status: 'CREATED', nextAction: 'LOGIN', tokenValue: 'wrong' }, key), /CUSTOMER_RESPONSE_INVALID/);
  assert.throws(() => decodeCustomerEnrollment({}, 'insecure-key'), /CUSTOMER_RESPONSE_INVALID/);
});
test('本人资料只取四字段，开发态不能混入正式客户端', async () => {
  const { decodeCustomerProfile } = await load();
  const profile = { customerId: '9', name: '验收农户', phoneMasked: '138****0000', bindingState: 'LINKED' };
  assert.deepEqual(decodeCustomerProfile({ ...profile, balance: '888.00' }), profile);
  assert.equal(decodeCustomerProfile({ ...profile, phoneMasked: '已登记联系方式' }).phoneMasked, '已登记联系方式');
  assert.equal(decodeCustomerProfile({ ...profile, phoneMasked: null }).phoneMasked, null);
  for (const change of [v => v.bindingState = 'DEV_ONLY', v => v.customerId = 9,
    v => v.phoneMasked = '13800000000', v => v.name = null]) {
    const value = { ...profile }; change(value); assert.throws(() => decodeCustomerProfile(value), /CUSTOMER_RESPONSE_INVALID/);
  }
});

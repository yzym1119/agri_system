/**
 * 前后端联调测试脚本（零依赖，仅使用 Node 原生 fetch）
 *
 * 用途：验证后端（http://localhost:8080）与前端约定的关键接口是否可用。
 * 运行前提：后端服务已启动且管理员账号已初始化；开发阶段不运行本脚本，
 * 由 Task 10 E2E 阶段执行。
 *
 * 用法：node scripts/test_frontend_backend_integration.js
 * 任一必须用例失败时，脚本以非零退出码结束。
 */

const BASE_URL = 'http://localhost:8080';
const REQUEST_TIMEOUT_MS = 5000;

// 测试配置
const TEST_ACCOUNTS = [
  { code: 'ADMIN001', password: '123456' },
  { code: '13800138000', password: '123456' }
];

/**
 * 统一请求封装。
 * 返回 { status, ok, data }；网络层异常直接抛出，由调用方决定是否致命。
 */
async function apiRequest(path, { method = 'GET', token, body } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers['satoken'] = token;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS)
  });

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  return { status: response.status, ok: response.ok, data };
}

/** 断言：HTTP 成功且业务包裹 code === "200" */
function assertEnvelopeOk(label, response) {
  const pass = response.ok && response.data && response.data.code === '200';
  if (pass) {
    console.log(`PASS ${label}（HTTP ${response.status}）`);
  } else {
    console.error(`FAIL ${label}：HTTP ${response.status}，响应 ${JSON.stringify(response.data)}`);
  }
  return !!pass;
}

/** 断言：HTTP 成功且 data 为数组 */
function assertEnvelopeArray(label, response) {
  const pass = assertEnvelopeOk(label, response) && Array.isArray(response.data.data);
  if (!pass) {
    console.error(`FAIL ${label}：data 不是数组（${JSON.stringify(response.data && response.data.data)}）`);
  }
  return !!pass;
}

async function testBackendHealth() {
  console.log('\n-- 后端服务可达性（仅提示，不计入失败） --');
  try {
    const response = await apiRequest('/actuator/health');
    if (response.ok) {
      console.log(`后端可访问（/actuator/health HTTP ${response.status}）`);
      return true;
    }
    const fallback = await apiRequest('/');
    console.log(`后端可访问（/ HTTP ${fallback.status}）`);
    return true;
  } catch (error) {
    console.log(`后端暂不可达：${error.message}`);
    return false;
  }
}

async function testLogin(account, password) {
  console.log(`\n-- 登录：${account} --`);
  const response = await apiRequest('/auth/staff/login', {
    method: 'POST',
    body: { account, password, loginType: 'PASSWORD' }
  });
  if (!response.ok || !response.data || !response.data.tokenValue) {
    console.error(`FAIL 登录失败：HTTP ${response.status}，响应 ${JSON.stringify(response.data)}`);
    return null;
  }
  console.log('PASS 登录成功');
  const token = response.data.tokenValue;
  console.log(`Token: ${String(token).substring(0, 50)}...`);
  return token;
}

async function testGetUserInfo(token) {
  console.log('\n-- 用户信息 --');
  const response = await apiRequest('/auth/staff/info', { token });
  if (!response.ok) {
    console.error(`FAIL 获取用户信息失败：HTTP ${response.status}，响应 ${JSON.stringify(response.data)}`);
    return false;
  }
  console.log('PASS 获取用户信息成功');
  return true;
}

/**
 * 新作业端点（零售事实链 Task 8/9）纯 HTTP 用例。
 * 说明：decisions 为只追加留痕，不触发任何自动动作，此处以固定合法载荷验证写入通路。
 */
async function testNewWorkflowEndpoints(token) {
  const results = [];

  console.log('\n-- GET /api/admin/inventory/locations?storeId=1 --');
  results.push(assertEnvelopeArray('货位列表', await apiRequest('/api/admin/inventory/locations?storeId=1', { token })));

  console.log('\n-- GET /api/admin/orders/pickup/requests?storeId=1 --');
  results.push(assertEnvelopeArray('取货待办', await apiRequest('/api/admin/orders/pickup/requests?storeId=1', { token })));

  console.log('\n-- GET /api/admin/inventory/warning/risks?storeId=1 --');
  results.push(assertEnvelopeArray('风险建议', await apiRequest('/api/admin/inventory/warning/risks?storeId=1', { token })));

  console.log('\n-- POST /api/admin/inventory/decisions（含原因，仅留痕） --');
  const decisionResponse = await apiRequest('/api/admin/inventory/decisions', {
    method: 'POST',
    token,
    body: {
      riskKind: 'LOW_STOCK',
      entityType: 'PRODUCT',
      entityId: 1,
      decisionAction: 'INSPECT',
      decisionReason: '集成测试：人工核查低库存提示（仅留痕，不自动执行）'
    }
  });
  const decisionPass = assertEnvelopeOk('记录人工决策', decisionResponse);
  if (decisionPass) {
    console.log(`decisionId = ${decisionResponse.data.data && decisionResponse.data.data.decisionId}`);
  }
  results.push(decisionPass);

  return results;
}

async function runTests() {
  console.log('开始前后端联调测试');
  console.log('='.repeat(50));

  await testBackendHealth();

  let token = null;
  for (const account of TEST_ACCOUNTS) {
    token = await testLogin(account.code, account.password);
    if (token) break;
    console.log(`账号 ${account.code} 登录失败，尝试下一个账号...`);
  }

  if (!token) {
    console.log('\n' + '='.repeat(50));
    console.log('前后端联调测试失败：无可用登录账号');
    console.log('='.repeat(50));
    console.log('请检查：');
    console.log('1. 后端服务是否运行在 http://localhost:8080');
    console.log('2. 管理员账号是否已创建（执行 scripts/init_admin_account.sql）');
    console.log('3. 数据库连接是否正常');
    process.exit(1);
  }

  const results = [];
  results.push(await testGetUserInfo(token));
  results.push(...(await testNewWorkflowEndpoints(token)));

  const failed = results.filter((pass) => !pass).length;
  console.log('\n' + '='.repeat(50));
  if (failed === 0) {
    console.log(`前后端联调测试通过（${results.length}/${results.length}）`);
    console.log('='.repeat(50));
    return;
  }
  console.log(`前后端联调测试失败（${failed}/${results.length} 个用例未通过）`);
  console.log('='.repeat(50));
  process.exit(1);
}

runTests().catch((error) => {
  console.error(`\n测试过程中发生错误：${error.message}`);
  process.exit(1);
});

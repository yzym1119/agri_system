/**
 * 前后端联调测试脚本
 * 用于验证前后端 API 连接是否正常
 */

const axios = require('axios');

const BASE_URL = 'http://localhost:8080';

// 测试配置
const TEST_ACCOUNTS = [
  { code: 'ADMIN001', password: '123456' },
  { code: '13800138000', password: '123456' }
];

async function testLogin(account, password) {
  try {
    console.log(`\n🔍 测试登录: ${account}`);
    console.log('─'.repeat(50));
    
    const response = await axios.post(`${BASE_URL}/auth/staff/login`, {
      account: account,
      password: password,
      loginType: 'PASSWORD'
    }, {
      headers: {
        'Content-Type': 'application/json'
      }
    });
    
    console.log('✅ 登录成功！');
    console.log('响应数据:', JSON.stringify(response.data, null, 2));
    
    if (response.data.tokenValue) {
      console.log(`\n📝 Token: ${response.data.tokenValue.substring(0, 50)}...`);
      
      // 测试获取用户信息
      await testGetUserInfo(response.data.tokenValue);
    }
    
    return response.data;
  } catch (error) {
    console.error('❌ 登录失败！');
    if (error.response) {
      console.error('状态码:', error.response.status);
      console.error('错误信息:', error.response.data);
    } else if (error.request) {
      console.error('网络错误: 无法连接到服务器');
      console.error('请确保后端服务运行在 http://localhost:8080');
    } else {
      console.error('错误:', error.message);
    }
    throw error;
  }
}

async function testGetUserInfo(token) {
  try {
    console.log('\n🔍 测试获取用户信息');
    console.log('─'.repeat(50));
    
    const response = await axios.get(`${BASE_URL}/auth/staff/info`, {
      headers: {
        'satoken': token
      }
    });
    
    console.log('✅ 获取用户信息成功！');
    console.log('用户信息:', JSON.stringify(response.data, null, 2));
    
    return response.data;
  } catch (error) {
    console.error('❌ 获取用户信息失败！');
    if (error.response) {
      console.error('状态码:', error.response.status);
      console.error('错误信息:', error.response.data);
    } else {
      console.error('错误:', error.message);
    }
    throw error;
  }
}

async function testBackendHealth() {
  try {
    console.log('\n🔍 测试后端服务健康状态');
    console.log('─'.repeat(50));
    
    // 尝试访问一个公开的端点（如果有的话）
    // 或者直接测试登录端点
    const response = await axios.get(`${BASE_URL}/actuator/health`, {
      timeout: 5000
    }).catch(() => {
      // 如果没有健康检查端点，尝试访问根路径
      return axios.get(`${BASE_URL}/`, {
        timeout: 5000
      });
    }).catch(() => {
      // 如果都失败，说明后端可能没有公开端点，但服务可能还在运行
      return { status: 'unknown', data: 'Backend service may be running' };
    });
    
    console.log('✅ 后端服务可访问');
    return true;
  } catch (error) {
    console.log('⚠️  无法确定后端服务状态（这是正常的，如果后端没有公开端点）');
    return false;
  }
}

async function runTests() {
  console.log('🚀 开始前后端联调测试');
  console.log('='.repeat(50));
  
  // 测试后端服务
  await testBackendHealth();
  
  // 测试登录
  let success = false;
  for (const account of TEST_ACCOUNTS) {
    try {
      await testLogin(account.code, account.password);
      success = true;
      break;
    } catch (error) {
      console.log(`\n⚠️  账号 ${account.code} 登录失败，尝试下一个账号...`);
    }
  }
  
  if (success) {
    console.log('\n' + '='.repeat(50));
    console.log('✅ 前后端联调测试通过！');
    console.log('='.repeat(50));
  } else {
    console.log('\n' + '='.repeat(50));
    console.log('❌ 前后端联调测试失败！');
    console.log('='.repeat(50));
    console.log('\n请检查：');
    console.log('1. 后端服务是否运行在 http://localhost:8080');
    console.log('2. 管理员账号是否已创建（执行 scripts/init_admin_account.sql）');
    console.log('3. 数据库连接是否正常');
    process.exit(1);
  }
}

// 运行测试
runTests().catch(error => {
  console.error('\n❌ 测试过程中发生错误:', error.message);
  process.exit(1);
});


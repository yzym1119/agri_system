> 文档状态：归档历史资料，不是现行开发/部署/建库依据。现行入口见[统一文档中心](../../current/README.md)。

# 前后端联调测试问题报告

## 测试时间
2025年1月

## 测试范围
- 门店管理模块（新增、编辑、选择店长）
- 员工管理模块（新增、编辑）
- 角色管理模块（新增、编辑、分配权限）

---

## 🔴 严重问题

### 1. 门店管理 - 新增功能失败
**问题描述：** 新增门店时提交失败，返回错误"系统繁忙，请稍后再试"（code: 10002）

**复现步骤：**
1. 进入门店管理页面
2. 点击"新增门店"按钮
3. 填写表单（门店名称、联系电话、门店地址）
4. 点击"确定"按钮

**错误信息：**
```
POST /api/admin/stores
Response: {code: 10002, message: 系统繁忙，请稍后再试}
```

**问题位置：** `admin-web/src/views/system/store/index.vue:363`

---

### 2. 门店管理 - 编辑功能失败（ID为空）
**问题描述：** 编辑门店时提交失败，返回错误"门店ID不能为空"（code: 10001）

**复现步骤：**
1. 进入门店管理页面
2. 点击某条记录的"编辑"按钮
3. 修改表单数据
4. 点击"确定"按钮

**错误信息：**
```
PUT /api/admin/stores/1
Response: {code: 10001, message: 门店ID不能为空}
```

**问题分析：**
- 代码第350行：`await updateStore(formData.id!, updateData)`
- 虽然`formData.id`在第293行已设置，但可能在后端请求时未正确传递
- 检查`updateStore`函数调用，URL路径参数正确，但后端可能期望在请求体中传递id

**问题位置：** `admin-web/src/views/system/store/index.vue:350`

---

### 3. 门店管理 - 店长下拉框为空
**问题描述：** 新增/编辑门店时，店长下拉框显示"No data"，无法选择店长

**复现步骤：**
1. 进入门店管理页面
2. 点击"新增门店"或"编辑"按钮
3. 点击"店长"下拉框

**问题分析：**
- 代码第256-260行：`loadStaffList`函数为空实现，只设置了空数组
- 没有调用员工列表API来加载数据
- 需要调用`getStaffList`或类似的API来获取员工列表

**问题位置：** `admin-web/src/views/system/store/index.vue:256-260`

**修复建议：**
```typescript
const loadStaffList = async () => {
  try {
    const response = await getStaffList({ pageNum: 1, pageSize: 1000 })
    staffList.value = response.records.map(staff => ({
      id: staff.id,
      name: staff.name,
      code: staff.code
    }))
  } catch (error) {
    console.error('加载员工列表失败:', error)
    ElMessage.error('加载员工列表失败')
    staffList.value = []
  }
}
```

---

### 4. 员工管理 - 新增功能（所属门店显示异常）
**问题描述：** 新增员工时，所属门店下拉框显示"0"而不是门店名称

**复现步骤：**
1. 进入员工管理页面
2. 点击"新增员工"按钮
3. 查看"所属门店"下拉框

**问题分析：**
- 门店列表已加载（从网络请求可见），但下拉框显示异常
- 可能是门店数据格式问题或下拉框绑定问题

---

### 5. 员工管理 - 新增功能（角色必填验证）
**问题描述：** 新增员工时，角色字段必填但未选择时提示"请至少选择一个角色"

**状态：** ✅ 验证正常，但需要确保角色下拉框数据正常加载

---

### 6. 角色管理 - 分配权限功能失败（ID为空）
**问题描述：** 分配权限时提交失败，返回错误"角色ID不能为空"（code: 10001）

**复现步骤：**
1. 进入角色管理页面
2. 点击某条记录的"分配权限"按钮
3. 选择权限（或不选择）
4. 点击"确定"按钮

**错误信息：**
```
POST /api/admin/roles/1/menus
Request: {menuIds: Array(0)}
Response: {code: 10001, message: 角色ID不能为空}
```

**问题分析：**
- 代码第254行：`await assignRoleMenus(currentRoleId.value, menuIds)`
- 虽然`currentRoleId.value`在第190行已设置，但后端返回ID为空
- 检查API调用：`assignRoleMenus`函数URL路径正确（`/api/admin/roles/${roleId}/menus`）
- 后端可能期望在请求体中传递`roleId`，而不是仅通过路径参数

**问题位置：** `admin-web/src/api/role.ts:58-64` 和 `admin-web/src/views/system/role/index.vue:254`

---

### 7. 角色管理 - 分配权限功能（权限未回显）
**问题描述：** 打开分配权限对话框时，已分配的权限没有回显，所有复选框都是未选中状态

**复现步骤：**
1. 进入角色管理页面
2. 点击某条记录的"分配权限"按钮
3. 查看权限树，所有复选框都是未选中状态

**问题分析：**
- 代码第198-201行有TODO注释，说明没有加载角色已分配的菜单
- `checkedMenuIds.value`在第191行被设置为空数组
- 需要调用API获取角色已分配的菜单ID列表

**问题位置：** `admin-web/src/views/system/role/index.vue:191, 198-201`

**修复建议：**
需要添加获取角色已分配菜单的API调用：
```typescript
// 在handleAssignMenus函数中
const roleMenus = await getRoleMenus(row.id) // 需要实现此API
checkedMenuIds.value = roleMenus.map(m => m.id)
```

---

## ⚠️ 中等问题

### 8. 门店管理 - 编辑时状态字段隐藏
**问题描述：** 编辑门店时，状态字段被隐藏（`v-if="!isEdit"`），无法修改状态

**问题位置：** `admin-web/src/views/system/store/index.vue:148`

**修复建议：** 编辑时也应该允许修改状态，移除`v-if="!isEdit"`条件

---

## 📋 测试总结

### 已测试功能
✅ 字典管理 - 新增、编辑、删除、查询（全部正常）
✅ 门店管理 - 查询功能（正常）
✅ 员工管理 - 查询功能（正常）
✅ 角色管理 - 查询功能（正常）
✅ 角色管理 - 分配权限对话框打开（正常）
✅ 角色管理 - 菜单树加载（正常）

### 存在问题功能
❌ 门店管理 - 新增功能（后端错误）
❌ 门店管理 - 编辑功能（ID为空）
❌ 门店管理 - 选择店长（列表为空）
❌ 员工管理 - 新增功能（门店显示异常）
❌ 角色管理 - 分配权限提交（ID为空）
❌ 角色管理 - 权限回显（未实现）

---

## 🔧 修复优先级

### P0 - 紧急修复
1. 门店管理 - 编辑功能ID为空问题
2. 角色管理 - 分配权限ID为空问题
3. 门店管理 - 店长下拉框为空问题

### P1 - 重要修复
4. 门店管理 - 新增功能后端错误
5. 角色管理 - 权限回显功能

### P2 - 优化改进
6. 门店管理 - 编辑时状态字段显示
7. 员工管理 - 门店下拉框显示优化

---

## 📝 备注

1. 所有API请求都返回200状态码，但业务错误码（10001, 10002）表示业务逻辑错误
2. 后端Command类要求请求体中必须包含ID字段（即使路径参数中也有ID）
3. ✅ 已实现获取角色已分配菜单的API接口
4. ✅ 已实现获取员工列表的API调用（用于门店管理的店长选择）

---

## ✅ 已修复问题

### 1. 门店管理 - 编辑功能ID为空 ✅
**修复内容：**
- 在`StoreUpdateRequest`类型中添加`id`字段
- 在提交时确保请求体包含`id`字段

**修改文件：**
- `admin-web/src/types/store.ts`
- `admin-web/src/views/system/store/index.vue`

---

### 2. 角色管理 - 分配权限ID为空 ✅
**修复内容：**
- 在`assignRoleMenus`函数中，请求体包含`roleId`字段

**修改文件：**
- `admin-web/src/api/role.ts`

---

### 3. 门店管理 - 店长下拉框为空 ✅
**修复内容：**
- 实现`loadStaffList`函数，调用员工列表API获取数据
- 使用动态导入避免循环依赖

**修改文件：**
- `admin-web/src/views/system/store/index.vue`

---

### 4. 角色管理 - 权限回显功能 ✅
**修复内容：**
- 在后端添加`findMenuIdsByRoleId`方法
- 在`StaffAuthorityService`中添加`getRoleMenuIds`方法
- 在`RoleController`中添加`GET /api/admin/roles/{id}/menus`接口
- 在前端添加`getRoleMenus` API函数
- 在`handleAssignMenus`中调用API获取已分配的菜单ID

**修改文件：**
- `core-domain/src/main/java/com/agri_system/domain/system/auth/repository/SysRoleMenuRepository.java`
- `core-infrastructure/src/main/java/com/agri_system/infrastructure/system/auth/repository/SysRoleMenuRepositoryImpl.java`
- `core-application/src/main/java/com/agri_system/application/staff/service/StaffAuthorityService.java`
- `api-admin/src/main/java/com/agri_system/api/admin/controller/RoleController.java`
- `admin-web/src/api/role.ts`
- `admin-web/src/views/system/role/index.vue`

---

### 5. 门店管理 - 编辑时状态字段隐藏 ✅
**修复内容：**
- 移除状态字段的`v-if="!isEdit"`条件，允许编辑时修改状态

**修改文件：**
- `admin-web/src/views/system/store/index.vue`

---

### 6. 员工管理 - 编辑功能ID为空 ✅
**修复内容：**
- 在`StaffUpdateRequest`类型中添加`id`、`storeId`和`status`字段
- 在提交时确保请求体包含所有必需字段

**修改文件：**
- `admin-web/src/types/staff.ts`
- `admin-web/src/views/system/staff/index.vue`

---

### 7. 员工管理 - 门店下拉框显示"0" ✅
**修复内容：**
- 将`storeId`初始值从`0`改为`undefined`，避免显示"0"

**修改文件：**
- `admin-web/src/views/system/staff/index.vue`

---

## 🔄 待测试问题

### 8. 门店管理 - 新增功能后端错误
**状态：** ⚠️ 需要后端检查
**问题：** 新增门店时返回"系统繁忙，请稍后再试"（code: 10002）
**建议：** 检查后端日志，确认具体错误原因


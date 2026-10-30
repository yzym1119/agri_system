const test=require('node:test');
const assert=require('node:assert/strict');
const {setupPage}=require('./helpers/page.cjs');
const {createStaffAuth}=require('../../packages/mobile-contracts/dist/staff-auth.js');
const contracts=require('../../packages/mobile-contracts/dist/inventory.js');
const sku={type:'SKU',id:'1',productId:'1',productCode:'P1',name:'商品一',unitId:'11',unitName:'箱',enabled:true,code:'P1'};
const stock={storeId:'1',productId:'1',productCode:'P1',productName:'商品一',productEnabled:true,mainUnitName:'箱',quantity:'0',unreservedQuantity:'0',page:1,totalRows:0,rows:[]};
async function fixture(page='scan',permissions=['inventory:view']) {
 let user={id:1,name:'测试员工',permissions,extra:{storeId:1}};
 const auth=createStaffAuth(async req=>({statusCode:200,data:req.url.endsWith('/login')?{tokenName:'Authorization',tokenValue:'test',userInfo:user}:user}));
 await auth.login('test','pass');
 let answer={storeId:'1',type:'SKU',matches:[sku],hasMore:false}, camera={status:'SCANNED',code:'P1',scanType:'CODE_128'}, readStock=stock;
 const calls={scan:0,resolve:0,search:0,location:0,inventory:0},searchRequests=[];
 const queries={resolveCode:async(code)=>{calls.resolve++;return typeof answer==='function'?answer(code):answer;},
   searchProducts:async(query,page)=>{calls.search++;searchRequests.push([query,page]);return {storeId:'1',page,matches:[sku],hasMore:page===1};},
   readLocationProducts:async()=>{calls.location++;return {storeId:'1',page:1,matches:[sku],hasMore:false};},
   readInventory:async()=>{calls.inventory++;return typeof readStock==='function'?readStock():readStock;}};
 const prefixes=page==='scan'?'../../':'../../../';
 const p=setupPage(page,auth,true,{[prefixes+'services/auth']:{staffAuth:auth},[prefixes+'services/scan']:queries,
   '../../services/platform':{mobilePlatform:{scan:async()=>{calls.scan++;return typeof camera==='function'?camera():camera;}}},
   '@agri/mobile-contracts/inventory':contracts});
 return {p,auth,calls,searchRequests,setAnswer:v=>answer=v,setCamera:v=>camera=v,setStock:v=>readStock=v,setUser:v=>user=v};
}
test('无权限和前台身份未确认均不扫码或读取库存',async()=>{
 const f=await fixture('scan',[]);await f.p.hooks.show();f.p.state.code.value='P1';await f.p.state.resolveManual();await f.p.state.scan();
 assert.equal(f.calls.scan,0);assert.equal(f.calls.resolve,0);assert.match(f.p.state.message.value,/权限/);
 const detail=await fixture('inventory/detail',[]);detail.p.hooks.load({productId:'1'});await detail.p.hooks.show();assert.equal(detail.calls.inventory,0);
});
test('相机拒绝后可手输，同帧双调只解析一次且不自动选择歧义结果',async()=>{
 const f=await fixture();await f.p.hooks.show();f.setCamera({status:'DENIED'});await f.p.state.scan();assert.match(f.p.state.message.value,/手输/);
 let done;f.setAnswer(()=>new Promise(resolve=>done=resolve));f.p.state.code.value='P1';const first=f.p.state.resolveManual();await f.p.state.resolveManual();
 assert.equal(f.calls.resolve,1);done({storeId:'1',type:'AMBIGUOUS',matches:[sku,{...sku,id:'2',productId:'2'}],hasMore:false});await first;
 assert.equal(f.p.state.matches.value.length,2);assert.equal(f.p.routes.length,0);
 f.p.state.choose(f.p.state.matches.value[0]);assert.equal(f.p.routes[0],'/pages/inventory/detail/index?productId=1');
});
test('真实平台SCANNED结果会解析编码，取消不会请求后台',async()=>{
 const f=await fixture();await f.p.hooks.show();await f.p.state.scan();assert.equal(f.calls.resolve,1);assert.equal(f.p.state.matches.value.length,1);
 f.setCamera({status:'CANCELLED'});await f.p.state.scan();assert.equal(f.calls.resolve,1);assert.match(f.p.state.message.value,/已取消/);
});
test('旧扫码响应不能回填新前台，且隐藏时旧候选立即不可选择',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.code.value='P1';await f.p.state.resolveManual();
 const oldCandidate=f.p.state.matches.value[0];f.p.hooks.hide();f.p.state.choose(oldCandidate);assert.equal(f.p.routes.length,0);
 await f.p.hooks.show();let done;f.setAnswer(()=>new Promise(resolve=>done=resolve));const old=f.p.state.resolveManual();
 f.p.hooks.hide();await f.p.hooks.show();done({storeId:'1',type:'SKU',matches:[sku],hasMore:false});await old;
 assert.equal(f.p.state.matches.value.length,0);
});
test('内部批次保留原batchId，货位仅定位本位置商品不当出库证据',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.code.value='B-1';
 f.setAnswer({storeId:'1',type:'RECEIPT_BATCH',matches:[{...sku,type:'RECEIPT_BATCH',id:'20',unitId:null,unitName:null,code:'B-1'}],hasMore:false});
 await f.p.state.resolveManual();f.p.state.choose(f.p.state.matches.value[0]);assert.match(f.p.routes[0],/productId=1&batchId=20$/);
 f.setAnswer({storeId:'1',type:'LOCATION',matches:[{...sku,type:'LOCATION',id:'3',name:'后仓',productId:null,productCode:null,unitId:null,unitName:null,code:'L3'}],hasMore:false});
 await f.p.state.resolveManual();await f.p.state.choose(f.p.state.matches.value[0]);assert.equal(f.calls.location,1);
 f.p.state.choose(f.p.state.matches.value[0]);assert.match(f.p.routes[1],/productId=1&locationId=3$/);
});
test('详情非法定位不读；旧响应和读取失败不显示旧库存或伪装零值',async()=>{
 const invalid=await fixture('inventory/detail');invalid.p.hooks.load({productId:'0'});await invalid.p.hooks.show();assert.equal(invalid.calls.inventory,0);
 const f=await fixture('inventory/detail');f.p.hooks.load({productId:'1'});await f.p.hooks.show();assert.equal(f.p.state.inventory.value.quantity,'0');
 let done;f.setStock(()=>new Promise(resolve=>done=resolve));const pending=f.p.state.load(1);assert.equal(f.p.state.inventory.value,null);
 f.p.hooks.hide();await f.auth.logout();done(stock);await pending;assert.equal(f.p.state.inventory.value,null);
});
test('新会话不能选择旧员工候选',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.code.value='P1';await f.p.state.resolveManual();const candidate=f.p.state.matches.value[0];
 await f.auth.login('other','pass');f.p.state.choose(candidate);assert.equal(f.p.routes.length,0);
});
test('翻回搜索第一页仍使用原查询，改草稿必须主动搜索才生效',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.keyword.value='玉米';await f.p.state.search();
 f.p.state.keyword.value='水稻';await f.p.state.next();await f.p.state.previous();
 assert.deepEqual(f.searchRequests,[['玉米',1],['玉米',2],['玉米',1]]);
 await f.p.state.search();assert.deepEqual(f.searchRequests[3],['水稻',1]);
});
test('读取库存失败显示未确认而非零库存；重试重新读取',async()=>{
 const f=await fixture('inventory/detail');f.p.hooks.load({productId:'1'});await f.p.hooks.show();
 f.setStock(()=>{throw Error('NETWORK_FAILED');});await f.p.state.load(1);
 assert.equal(f.p.state.inventory.value,null);assert.match(f.p.state.message.value,/不能确认库存/);
 f.setStock(stock);await f.p.state.load(1);assert.equal(f.p.state.inventory.value.quantity,'0');
});
test('扫码查货响应401清会话时回到登录，隐藏后的旧失败不误跳转',async()=>{
 const f=await fixture();await f.p.hooks.show();f.p.state.code.value='P1';
 f.setAnswer(async()=>{await f.auth.logout();throw Error('UNAUTHORIZED');});await f.p.state.resolveManual();assert.deepEqual(f.p.routes,['/pages/login/index']);
 const hidden=await fixture();await hidden.p.hooks.show();let fail;hidden.setAnswer(()=>new Promise((_,reject)=>fail=reject));hidden.p.state.code.value='P1';const pending=hidden.p.state.resolveManual();
 hidden.p.hooks.hide();fail(Error('UNAUTHORIZED'));await pending;assert.equal(hidden.p.routes.length,0);
});

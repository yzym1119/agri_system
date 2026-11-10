const {test}=require('node:test'),assert=require('node:assert/strict');
const load=()=>import('../dist/customer-catalog.js');
const product={productId:'7',name:'商品',specification:null,manufacturer:null,registrationNo:null,imageUrl:null,detailText:null,storageConditions:null,shelfLifeDays:null};
const detail=()=>({product,baseUnitName:'瓶',allowFractionalBaseQuantity:false,saleUnits:[{unitName:'瓶',baseQuantityPerUnit:'1'},{unitName:'箱',baseQuantityPerUnit:'12'}],referencePrice:{productId:'7',unitName:'箱',unitPrice:'123.45',state:'AVAILABLE'}});
test('公开白名单丢弃内部字段，页游标与返回项目一致且有界',async()=>{
 const {decodePublicStores,decodePublicProducts}=await load();assert.deepEqual(decodePublicStores({rows:[{storeId:'1',name:'门店',phone:'13800000000',address:'公开地址',managerId:99}],nextAfter:null}).rows[0],{storeId:'1',name:'门店',phone:'13800000000',address:'公开地址'});
 assert.equal(decodePublicProducts({rows:[product],nextAfter:'7'}).nextAfter,'7');
 for(const v of [{rows:[product],nextAfter:'8'},{rows:Array(21).fill(product),nextAfter:null},{rows:[product,product],nextAfter:null}])assert.throws(()=>decodePublicProducts(v),/PUBLIC_RESPONSE_INVALID/);
});
test('后台合法多行地址原样保留，不使同页其他门店读取失败，其他控制字符仍拒绝',async()=>{
 const {decodePublicStores}=await load();const store={storeId:'1',name:'一号门店',phone:'13800000000',address:'第一行地址'};
 for(const newline of ['\n','\r\n','\r']){
  const address='大庆市某街道'+newline+'二楼入口';
  const result=decodePublicStores({rows:[store,{...store,storeId:'2',name:'二号门店',address}],nextAfter:null});
  assert.equal(result.rows.length,2);assert.equal(result.rows[0].name,'一号门店');assert.equal(result.rows[1].address,address);
 }
 for(const address of ['\r\n','地址\u0000内容','地址\u0007内容','x'.repeat(1001)])assert.throws(()=>decodePublicStores({rows:[{...store,address}],nextAfter:null}),/PUBLIC_RESPONSE_INVALID/);
 for(const field of ['name','phone'])assert.throws(()=>decodePublicStores({rows:[{...store,[field]:'第一行\n第二行'}],nextAfter:null}),/PUBLIC_RESPONSE_INVALID/);
});
test('价格单位及原商品必须一致，单位系数只解码不计算价格',async()=>{
 const {decodePublicProduct}=await load();assert.equal(decodePublicProduct(detail(),'7','箱').referencePrice.unitPrice,'123.45');
 for(const change of [v=>v.referencePrice.productId='8',v=>v.referencePrice.unitName='未知',v=>v.referencePrice.unitPrice=0,v=>v.referencePrice.unitPrice='0.00',v=>v.saleUnits[0].baseQuantityPerUnit='0']){
  const v=detail();v.saleUnits=v.saleUnits.map(x=>({...x}));v.referencePrice={...v.referencePrice};change(v);assert.throws(()=>decodePublicProduct(v,'7','箱'),/PUBLIC_RESPONSE_INVALID/);
 }
 assert.throws(()=>decodePublicProduct(detail(),'8'),/PUBLIC_RESPONSE_INVALID/);
});
test('缺单位与价格保持未知，不补0或推断可以买；跨对象回复拒绝',async()=>{
 const {decodePublicProduct}=await load();const v={product,baseUnitName:null,allowFractionalBaseQuantity:null,saleUnits:[],referencePrice:{productId:'7',unitName:null,unitPrice:null,state:'CONFIRM_WITH_STORE'}};
 assert.equal(decodePublicProduct(v,'7').referencePrice.unitPrice,null);assert.equal(decodePublicProduct(v,'7').saleUnits.length,0);
});
test('公开查询始终匿名，只调用固定路径，非法ID/搜索不出网',async()=>{
 const {createPublicCatalog}=await load();const calls=[],api=createPublicCatalog(async r=>{calls.push(r);return{statusCode:200,data:{rows:[],nextAfter:null}}});
 await api.stores();await api.products('玉米');assert.deepEqual(calls[0],{path:'/api/public/stores',query:{}});assert.deepEqual(calls[1],{path:'/api/public/catalog/products',query:{keyword:'玉米'}});assert.equal(JSON.stringify(calls).includes('Authorization'),false);
 await assert.rejects(api.product('90071992547409930000'),/PUBLIC_REQUEST_INVALID/);await assert.rejects(api.products('x'.repeat(101)),/PUBLIC_REQUEST_INVALID/);assert.equal(calls.length,2);
});

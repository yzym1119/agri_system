const test=require('node:test');
const assert=require('node:assert/strict');
const load=()=>import('../dist/inventory.js');
const sku={type:'SKU',id:'1',productId:'1',productCode:'P1',name:'商品',unitId:'10',unitName:'箱',enabled:true,code:'P1'};
const stock={storeId:'1',productId:'1',productCode:'P1',productName:'商品',productEnabled:true,mainUnitName:'箱',quantity:'1',unreservedQuantity:'0.83333333',page:1,totalRows:1,rows:[{batchId:'20',locationId:'1',internalBatchNo:'B-真实',supplierBatchNo:null,locationName:'后仓',locationCode:'L1',locationEnabled:true,baseQuantity:'12',unreservedBaseQuantity:'10',baseUnitName:'瓶',originalUnitName:'箱',originalQuantity:'1',productionDate:null,expiryDate:null}]};
test('扫码合同保留歧义；不接受错误类型、危险数字ID或包裹猜测',async()=>{
 const {isScanResolution}=await load();
 const r={storeId:'1',type:'SKU',matches:[sku],hasMore:false};assert.equal(isScanResolution(r),true);
 assert.equal(isScanResolution({...r,type:'RECEIPT_BATCH'}),false);
 assert.equal(isScanResolution({...r,matches:[{...sku,id:9007199254740992}]}),false);
 assert.equal(isScanResolution({...r,type:'UNKNOWN'}),false);
 assert.equal(isScanResolution({...r,type:'AMBIGUOUS',matches:[sku,{...sku,id:'2',productId:'2'}]}),true);
 assert.equal(isScanResolution({code:'200',data:r}),false);
 assert.equal(isScanResolution({...r,matches:[{...sku,type:'PICKUP_REQUEST'}]}),false);
});
test('库存数量使用无尾零字符串，不把缺失日期或主单位证据猜成0',async()=>{
 const {isInventoryView}=await load();assert.equal(isInventoryView(stock),true);
 assert.equal(isInventoryView({...stock,quantity:null,unreservedQuantity:null}),true);
 assert.equal(isInventoryView({...stock,quantity:1}),false);
 assert.equal(isInventoryView({...stock,quantity:'1.000000000'}),false);
 assert.equal(isInventoryView({...stock,rows:[{...stock.rows[0],productionDate:'2026-02-30'}]}),false);
 assert.equal(isInventoryView({...stock,rows:[{...stock.rows[0],baseUnitName:null}]}),true);
});
test('目录页合同、库存定位只接受正Long及可选原批次/货位',async()=>{
 const {isInventoryMatches,inventoryTarget}=await load();
 assert.equal(isInventoryMatches({storeId:'1',page:1,matches:[sku],hasMore:false}),true);
 assert.equal(isInventoryMatches({storeId:'1',page:0,matches:[sku],hasMore:false}),false);
 assert.deepEqual(inventoryTarget({productId:'1',batchId:'20',locationId:'2'}),{productId:'1',batchId:'20',locationId:'2'});
 for(const value of [{},{productId:'0'},{productId:'9007199254740992',batchId:'x'},{productId:'1',locationId:'-1'}])assert.throws(()=>inventoryTarget(value));
});
module.exports={sku,stock};

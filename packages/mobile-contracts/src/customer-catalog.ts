import { isPositiveLongId } from './ids.js';
import { decodeResponse, type HttpResponse } from './session.js';
export type PublicRequest={path:string;query:Record<string,string>};
export type PublicStore={storeId:string;name:string;phone:string;address:string};
export type PublicProduct={productId:string;name:string;specification:string|null;manufacturer:string|null;imageUrl:string|null};
export type PublicProductDetail={product:PublicProduct&{registrationNo:string|null;detailText:string|null;storageConditions:string|null;shelfLifeDays:number|null};baseUnitName:string|null;allowFractionalBaseQuantity:boolean|null;saleUnits:{unitName:string;baseQuantityPerUnit:string}[];referencePrice:{productId:string;unitName:string|null;unitPrice:string|null;state:'AVAILABLE'|'CONFIRM_WITH_STORE'}};
type Page<T>={rows:T[];nextAfter:string|null};
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const bad=()=>Error('PUBLIC_RESPONSE_INVALID');
function text(v:unknown,max=1000):string{if(typeof v!=='string'||!v.trim()||v.length>max||/[\u0000-\u001f\u007f]/.test(v))throw bad();return v;}
// 后台地址使用多行输入；保留原文供展示/复制，其余控制字符仍按普通文本拒绝。
function address(v:unknown):string{if(typeof v!=='string')throw bad();text(v.replace(/[\r\n]/g,' '));return v;}
function optional(v:unknown,multi=false):string|null{if(v===null||v===undefined)return null;if(typeof v!=='string'||v.length>65535||(!multi&&/[\u0000-\u001f\u007f]/.test(v)))throw bad();return v;}
function id(v:unknown):string{if(!isPositiveLongId(v))throw bad();return v;}
function image(v:unknown):string|null{return typeof v==='string'&&v.length<=2048&&/^https:\/\/[a-z0-9.-]+(?::[0-9]{1,5})?(?:[/?][^\s#]*)?$/i.test(v)?v:null;}
function product(v:unknown):PublicProduct{if(!object(v))throw bad();return{productId:id(v.productId),name:text(v.name),specification:optional(v.specification),manufacturer:optional(v.manufacturer),imageUrl:image(v.imageUrl)};}
function page<T>(value:unknown,decode:(v:unknown)=>T,key:(v:T)=>string):Page<T>{
 if(!object(value)||!Array.isArray(value.rows)||value.rows.length>20)throw bad();const rows=value.rows.map(decode);const ids=rows.map(key);
 for(let n=1;n<ids.length;n++){const a=ids[n-1]!,b=ids[n]!;if(a.length>b.length||a.length===b.length&&a>=b)throw bad();}
 const nextAfter=value.nextAfter==null?null:id(value.nextAfter);if(nextAfter!==null&&nextAfter!==ids[ids.length-1])throw bad();return{rows,nextAfter};
}
export function decodePublicStores(value:unknown):Page<PublicStore>{return page(value,v=>{if(!object(v))throw bad();return{storeId:id(v.storeId),name:text(v.name),phone:text(v.phone),address:address(v.address)}},v=>v.storeId);}
export function decodePublicProducts(value:unknown):Page<PublicProduct>{return page(value,product,v=>v.productId);}
export function decodePublicProduct(value:unknown,expectedId:string,selectedUnit?:string):PublicProductDetail{
 if(!object(value)||!object(value.product)||!object(value.referencePrice)||!Array.isArray(value.saleUnits)||value.saleUnits.length>100)throw bad();
 const p=product(value.product);if(p.productId!==expectedId)throw bad();const raw=value.product,price=value.referencePrice;
 const base=value.baseUnitName===null?null:text(value.baseUnitName,50),fractional=value.allowFractionalBaseQuantity;
 if((base===null&&fractional!==null)||(base!==null&&typeof fractional!=='boolean'))throw bad();
 const saleUnits=value.saleUnits.map(u=>{if(!object(u)||typeof u.baseQuantityPerUnit!=='string'||!/^\d+(?:\.\d+)?$/.test(u.baseQuantityPerUnit)||!/[1-9]/.test(u.baseQuantityPerUnit)||u.baseQuantityPerUnit.length>50)throw bad();return{unitName:text(u.unitName,50),baseQuantityPerUnit:u.baseQuantityPerUnit}});
 if(new Set(saleUnits.map(u=>u.unitName)).size!==saleUnits.length||base===null&&saleUnits.length)throw bad();
 const unitName=price.unitName===null?null:text(price.unitName,50);let unitPrice:string|null=null;
 if(id(price.productId)!==expectedId)throw bad();
 if(price.state==='AVAILABLE'){
  if(base===null||unitName===null||!saleUnits.some(u=>u.unitName===unitName)||selectedUnit!==undefined&&unitName!==selectedUnit||typeof price.unitPrice!=='string'||!/^\d+\.\d{2}$/.test(price.unitPrice)||!/[1-9]/.test(price.unitPrice))throw bad();unitPrice=price.unitPrice;
 }else if(price.state!=='CONFIRM_WITH_STORE'||price.unitPrice!==null)throw bad();
 const life=raw.shelfLifeDays;if(life!==null&&(!Number.isSafeInteger(life)||typeof life!=='number'||life<=0||life>2147483647))throw bad();
 return{product:{...p,registrationNo:optional(raw.registrationNo),detailText:optional(raw.detailText,true),storageConditions:optional(raw.storageConditions,true),shelfLifeDays:life as number|null},baseUnitName:base,allowFractionalBaseQuantity:fractional as boolean|null,saleUnits,referencePrice:{productId:expectedId,unitName,unitPrice,state:price.state}};
}
export function createPublicCatalog(send:(request:PublicRequest)=>Promise<HttpResponse>){
 async function read<T>(request:PublicRequest,decode:(v:unknown)=>T):Promise<T>{let response:HttpResponse;try{response=await send(request)}catch{throw Error('RESULT_UNKNOWN')}return decode(decodeResponse('raw',response,(v):v is unknown=>v!==null&&v!==undefined));}
 function cursor(after?:string):Record<string,string>{if(after!==undefined&&!isPositiveLongId(after))throw Error('PUBLIC_REQUEST_INVALID');return after?{after}:{};}
 return{
  stores(after?:string){return Promise.resolve().then(()=>read({path:'/api/public/stores',query:cursor(after)},decodePublicStores))},
  products(keyword?:string,after?:string){return Promise.resolve().then(()=>{if(keyword!==undefined&&(keyword.length>100||/[\u0000-\u001f\u007f]/.test(keyword)))throw Error('PUBLIC_REQUEST_INVALID');return read({path:'/api/public/catalog/products',query:{...cursor(after),...(keyword?.trim()?{keyword:keyword.trim()}:{})}},decodePublicProducts)})},
  product(productId:string,unit?:string){return Promise.resolve().then(()=>{if(!isPositiveLongId(productId)||unit!==undefined&&(!unit.trim()||unit.length>50||/[\u0000-\u001f\u007f]/.test(unit)))throw Error('PUBLIC_REQUEST_INVALID');return read({path:'/api/public/catalog/products/'+productId,query:unit?{unit}:{}},v=>decodePublicProduct(v,productId,unit))})},
 };
}

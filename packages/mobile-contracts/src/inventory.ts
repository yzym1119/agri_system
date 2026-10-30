import { isPositiveLongId } from './ids.js';

export type InventoryMatch={type:'SKU'|'RECEIPT_BATCH'|'LOCATION';id:string;productId:string|null;productCode:string|null;
  name:string;unitId:string|null;unitName:string|null;enabled:boolean;code:string};
export type ScanResolution={storeId:string;type:InventoryMatch['type']|'AMBIGUOUS'|'UNKNOWN';matches:InventoryMatch[];hasMore:boolean};
export type InventoryMatches={storeId:string;page:number;matches:InventoryMatch[];hasMore:boolean};
export type InventoryStock={batchId:string;locationId:string;internalBatchNo:string|null;supplierBatchNo:string|null;
  locationName:string|null;locationCode:string|null;locationEnabled:boolean;baseQuantity:string;unreservedBaseQuantity:string;
  baseUnitName:string|null;originalUnitName:string|null;originalQuantity:string|null;productionDate:string|null;expiryDate:string|null};
export type InventoryView={storeId:string;productId:string;productCode:string;productName:string;productEnabled:boolean;
  mainUnitName:string;quantity:string|null;unreservedQuantity:string|null;page:number;totalRows:number;rows:InventoryStock[]};
export type InventoryTarget={productId:string;batchId?:string;locationId?:string};
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const id=(v:unknown):v is string=>typeof v==='string'&&isPositiveLongId(v);
const text=(v:unknown):v is string=>typeof v==='string'&&v.trim().length>0&&v.length<=200;
const optionalText=(v:unknown)=>v===null||text(v);
const optionalId=(v:unknown)=>v===null||id(v);
const quantity=(v:unknown):v is string=>typeof v==='string'&&/^(0|[1-9]\d{0,29})(\.\d{1,8})?$/.test(v);
const optionalQuantity=(v:unknown)=>v===null||quantity(v);
const page=(v:unknown)=>Number.isSafeInteger(v)&&Number(v)>0&&Number(v)<=10000;
function date(v:unknown) {
  if(v===null)return true;
  if(typeof v!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(v))return false;
  const parsed=new Date(v+'T00:00:00Z');return !Number.isNaN(parsed.getTime())&&parsed.toISOString().slice(0,10)===v;
}
function match(v:unknown):v is InventoryMatch {
  if(!object(v)||!id(v.id)||!text(v.name)||!text(v.code)||typeof v.enabled!=='boolean'
    ||!optionalId(v.unitId)||!optionalText(v.unitName))return false;
  if(v.type==='LOCATION')return v.productId===null&&v.productCode===null&&v.unitId===null&&v.unitName===null;
  if(v.type!=='SKU'&&v.type!=='RECEIPT_BATCH')return false;
  return id(v.productId)&&text(v.productCode)&&(v.type!=='SKU'||v.id===v.productId);
}
export function isScanResolution(v:unknown):v is ScanResolution {
  if(!object(v)||!id(v.storeId)||!Array.isArray(v.matches)||v.matches.length>20||!v.matches.every(match)||typeof v.hasMore!=='boolean')return false;
  if(v.type==='UNKNOWN')return v.matches.length===0&&!v.hasMore;
  if(v.type==='AMBIGUOUS')return v.matches.length>=2;
  return v.matches.length===1&&!v.hasMore&&v.matches[0]?.type===v.type;
}
export function isInventoryMatches(v:unknown):v is InventoryMatches {
  return object(v)&&id(v.storeId)&&page(v.page)&&Array.isArray(v.matches)&&v.matches.length<=20
    &&v.matches.every(m=>match(m)&&m.type==='SKU')&&typeof v.hasMore==='boolean';
}
function stock(v:unknown):v is InventoryStock {
  return object(v)&&id(v.batchId)&&id(v.locationId)&&optionalText(v.internalBatchNo)&&optionalText(v.supplierBatchNo)
    &&optionalText(v.locationName)&&optionalText(v.locationCode)&&typeof v.locationEnabled==='boolean'
    &&quantity(v.baseQuantity)&&quantity(v.unreservedBaseQuantity)&&optionalText(v.baseUnitName)
    &&optionalText(v.originalUnitName)&&optionalQuantity(v.originalQuantity)&&date(v.productionDate)&&date(v.expiryDate);
}
export function isInventoryView(v:unknown):v is InventoryView {
  return object(v)&&id(v.storeId)&&id(v.productId)&&text(v.productCode)&&text(v.productName)&&typeof v.productEnabled==='boolean'
    &&text(v.mainUnitName)&&optionalQuantity(v.quantity)&&optionalQuantity(v.unreservedQuantity)&&page(v.page)
    &&Number.isSafeInteger(v.totalRows)&&Number(v.totalRows)>=0&&Array.isArray(v.rows)&&v.rows.length<=30&&v.rows.every(stock);
}
export function inventoryTarget(value:unknown):InventoryTarget {
  if(!object(value)||!id(value.productId))throw Error('INVENTORY_TARGET_INVALID');
  const result:InventoryTarget={productId:value.productId};
  for(const key of ['batchId','locationId'] as const)if(value[key]!==undefined) {
    if(!id(value[key]))throw Error('INVENTORY_TARGET_INVALID');result[key]=value[key];
  }
  return result;
}

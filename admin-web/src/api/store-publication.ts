import request from '@/utils/request'
export interface StorePublication {storeId:string;name:string;phone:string|null;address:string|null;visible:boolean;version:string;fingerprint:string;canPublish:boolean}
const id=(v:unknown):v is string=>typeof v==='string'&&/^[1-9][0-9]{0,18}$/.test(v)&&(v.length<19||v<='9223372036854775807')
function checked(value:any,storeId:string):StorePublication{
  if(!value||value.storeId!==storeId||!id(value.storeId)||typeof value.name!=='string'||!id(value.version)||typeof value.visible!=='boolean'||typeof value.canPublish!=='boolean'||typeof value.fingerprint!=='string'||!/^([0-9a-f]{64})$/.test(value.fingerprint)||(value.phone!==null&&typeof value.phone!=='string')||(value.address!==null&&typeof value.address!=='string'))throw Error('公开资料响应无效，请重新读取')
  return value
}
export async function getStorePublication(storeId:string,expectedSessionToken:string):Promise<StorePublication>{
  if(!id(storeId)||!expectedSessionToken)throw Error('需要当前门店和员工身份')
  return checked(await request({url:`/api/admin/stores/${storeId}/publication`,method:'get',expectedSessionToken}),storeId)
}
export async function setStorePublication(preview:StorePublication,visible:boolean,expectedSessionToken:string):Promise<StorePublication>{
  if(!id(preview.storeId)||!expectedSessionToken)throw Error('需要当前门店和员工身份')
  const result=checked(await request({url:`/api/admin/stores/${preview.storeId}/publication`,method:'post',expectedSessionToken,data:{visible,expectedVersion:preview.version,fingerprint:preview.fingerprint}}),preview.storeId)
  if(result.visible!==visible)throw Error('当前公开设置未能确认，请重新读取')
  return result
}

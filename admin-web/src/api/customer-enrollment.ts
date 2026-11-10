import request from '@/utils/request'
import {useUserStore} from '@/store/user'

export type EnrollmentStatus = 'REVIEW_REQUIRED' | 'CREATED' | 'LINKED' | 'REJECTED'
export interface EnrollmentSummary {id:string;requestKey:string;displayName:string;phoneMasked:string;status:EnrollmentStatus;reviewReason:string|null;createdAt:string}
export interface EnrollmentCandidate {id:string;name:string;phoneMasked:string;status:number;deleted:number|null}
export interface EnrollmentDetail {request:EnrollmentSummary;appId:string;openIdMasked:string;verifiedAt:string;candidates:EnrollmentCandidate[];customerId:string|null;identityId:string|null;identityAuthVersion:string|null;reviewedBy:string|null;verificationMethod:string|null;reason:string|null;reviewedAt:string|null}
export interface EnrollmentDecision {requestKey:string;action:'LINK'|'REJECT';targetCustomerId:string|null;verificationMethod:'IN_PERSON'|'KNOWN_CONTACT_CALLBACK'|'UNCONFIRMED';reason:string}
export interface EnrollmentDecisionResult {enrollmentId:string;requestKey:string;status:'UNKNOWN'|'CONFLICT'|'LINKED'|'REJECTED';action:'LINK'|'REJECT'|null;targetCustomerId:string|null;verificationMethod:string|null;reason:string|null}
const base='/api/admin/customer-identities/enrollments'
function session(){const expectedSessionToken=useUserStore().token;if(!expectedSessionToken)throw Error('需要当前员工登录');return {expectedSessionToken}}
const id=(value:unknown):value is string=>typeof value==='string'&&/^[1-9][0-9]{0,18}$/.test(value)
const key=(value:unknown):value is string=>typeof value==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value)
function checkId(value:string){if(!id(value))throw Error('申请编号无效')}
function checkKey(value:string){if(!key(value))throw Error('原请求编号无效')}
function summary(v:any):v is EnrollmentSummary{return !!v&&id(v.id)&&key(v.requestKey)&&typeof v.displayName==='string'&&typeof v.phoneMasked==='string'&&['REVIEW_REQUIRED','CREATED','LINKED','REJECTED'].includes(v.status)&&typeof v.createdAt==='string'}
function candidates(v:unknown):v is EnrollmentCandidate[]{return Array.isArray(v)&&v.length<=20&&v.every(c=>c&&id(c.id)&&typeof c.name==='string'&&typeof c.phoneMasked==='string'&&[1,2].includes(c.status)&&(c.deleted===null||[0,1].includes(c.deleted)))}
export async function getEnrollmentQueue(status:EnrollmentStatus,before?:string):Promise<{rows:EnrollmentSummary[];nextBefore:string|null}>{
  if(before)checkId(before)
  const r:any=await request({...session(),url:base,method:'get',params:{status,before}})
  if(!r||!Array.isArray(r.rows)||r.rows.length>20||!r.rows.every(summary)||(r.nextBefore!==null&&!id(r.nextBefore)))throw Error('核验队列资料不完整，请刷新核对')
  return r
}
export async function getEnrollment(enrollmentId:string):Promise<EnrollmentDetail>{
  checkId(enrollmentId);const r:any=await request({...session(),url:`${base}/${enrollmentId}`,method:'get'})
  if(!r||!summary(r.request)||r.request.id!==enrollmentId||!candidates(r.candidates)||typeof r.appId!=='string'||typeof r.openIdMasked!=='string'||typeof r.verifiedAt!=='string')throw Error('申请详情不完整，请重新读取')
  return r
}
export async function findEnrollmentCandidates(keyword:string):Promise<EnrollmentCandidate[]>{
  const r:any=await request({...session(),url:`${base}/candidates`,method:'get',params:{keyword}})
  if(!candidates(r))throw Error('候选客户资料不完整，请重新查询');return r
}
export async function submitEnrollmentDecision(enrollmentId:string,data:EnrollmentDecision):Promise<void>{
  checkId(enrollmentId);checkKey(data.requestKey)
  await request({...session(),url:`${base}/${enrollmentId}/decision`,method:'post',data})
}
export async function getEnrollmentDecision(enrollmentId:string,requestKey:string):Promise<EnrollmentDecisionResult>{
  checkId(enrollmentId);checkKey(requestKey);const r:any=await request({...session(),url:`${base}/${enrollmentId}/decisions/${requestKey}`,method:'get'})
  if(!r||r.enrollmentId!==enrollmentId||r.requestKey!==requestKey||!['UNKNOWN','CONFLICT','LINKED','REJECTED'].includes(r.status))throw Error('原决定结果不完整，仍需核对')
  if(['LINKED','REJECTED'].includes(r.status)&&(r.action!==(r.status==='LINKED'?'LINK':'REJECT')||(r.action==='LINK'?!id(r.targetCustomerId):r.targetCustomerId!==null)||typeof r.verificationMethod!=='string'||typeof r.reason!=='string'))throw Error('原决定内容不完整，仍需核对')
  return r
}

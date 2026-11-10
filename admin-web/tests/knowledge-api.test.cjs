const {test}=require('node:test'),assert=require('node:assert/strict'),load=require('./helpers/knowledge.cjs');
const key='11111111-1111-4111-8111-111111111111',content={title:'标题',summary:'',crop:'',region:'',growthStage:'',appliesTo:'',exclusions:'',body:'',sourceText:'',audience:'INTERNAL',riskLevel:'REQUIRES_HUMAN',effectiveFrom:null,effectiveUntil:null};
const row=()=>({id:'1',logicalKey:key,revision:1,storeId:'1',content,contentHash:'0'.repeat(64),state:'DRAFT',effectiveState:'DRAFT',version:'1',reviewedAt:null});
test('知识API固定原token/URL与原状态版本，返回白名单不借客户端员工字段',async()=>{
 const calls=[];let response=row();const request=async options=>{calls.push(options);return response};request.defaults={baseURL:'https://api.example.test'};const api=load('api/knowledge',{'@/utils/request':{default:request}},{window:{location:{origin:'https://admin.example.test'}}});
 assert.equal(api.knowledgeOrigin(),'https://api.example.test');await api.knowledgeLatest(key,'old-token');assert.equal(calls[0].expectedSessionToken,'old-token');assert.equal(calls[0].url,'/api/admin/agronomy/knowledge/'+key+'/latest');
 response={...row(),state:'REVIEWED',effectiveState:'REVIEWED',version:'2',reviewedAt:'2026-11-10T17:00:00'};await api.knowledgeDecision(row(),'review','核对原因','old-token');assert.equal(calls[1].data.expectedVersion,'1');assert.equal(calls[1].data.reason,'核对原因');assert.equal(calls[1].data.staffId,undefined);
 response={...row(),logicalKey:'22222222-2222-4222-8222-222222222222'};await assert.rejects(api.knowledgeLatest(key,'old-token'));
});
test('知识API非法ID、原版本或缺会话均不发送请求',async()=>{
 let calls=0;const request=async()=>{calls++;return row()};request.defaults={baseURL:'https://api.example.test'};const api=load('api/knowledge',{'@/utils/request':{default:request}});
 await assert.rejects(api.knowledgeDetail(key,1.5,'token'));await assert.rejects(api.knowledgePage('9223372036854775808',null,'token'));await assert.rejects(api.knowledgeLatest(key,''));await assert.rejects(api.knowledgeDecision(row(),'review','  ','token'));assert.equal(calls,0);
});

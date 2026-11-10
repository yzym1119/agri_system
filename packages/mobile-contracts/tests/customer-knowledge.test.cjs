const {test}=require('node:test'),assert=require('node:assert/strict');const load=()=>import('../dist/customer-knowledge.js');
const key='11111111-1111-4111-8111-111111111111',scope={region:'大庆',crop:'玉米',growthStage:'苗期'};
const summary=()=>({id:'7',logicalKey:key,revision:1,title:'软件测试知识',summary:'摘要\n第二行',scope,effectiveUntil:'2026-12-10',reviewedAt:'2026-11-10T17:00:00'});
const detail=()=>({article:summary(),body:'软件测试正文\n第二行',appliesTo:'适用条件',exclusions:'不适用条件',sourceText:'虚构软件来源',effectiveFrom:'2026-11-10',contentHash:'0'.repeat(64)});
test('公开知识白名单核原组版本及完整范围，多行原文不变',async()=>{
 const {decodeKnowledgeDetail}=await load();const value=decodeKnowledgeDetail({...detail(),reviewedBy:999,requestKey:'private'},key,1,scope);assert.equal(value.body,detail().body);assert.equal(value.reviewedBy,undefined);assert.equal(value.requestKey,undefined);
 for(const args of [[key,2,scope],[key,1,{...scope,region:'其他地区'}],['22222222-2222-4222-8222-222222222222',1,scope]])assert.throws(()=>decodeKnowledgeDetail(detail(),...args));
 for(const change of [x=>x.article.id='9223372036854775808',x=>x.article.effectiveUntil='2026-02-30',x=>x.body='',x=>x.contentHash='bad']){const v=detail();change(v);assert.throws(()=>decodeKnowledgeDetail(v,key,1,scope))}
});
test('筛选组合真实且有界，分页游标与当前项目一致，不跨范围混入知识',async()=>{
 const {decodeKnowledgeScopes,decodeKnowledgePage}=await load();assert.deepEqual(decodeKnowledgeScopes([scope]),[scope]);assert.throws(()=>decodeKnowledgeScopes(Array(201).fill(scope)));assert.throws(()=>decodeKnowledgeScopes([scope,scope]));
 assert.equal(decodeKnowledgePage({rows:[summary()],nextAfter:'7'},scope).nextAfter,'7');assert.throws(()=>decodeKnowledgePage({rows:[summary()],nextAfter:'8'},scope));assert.throws(()=>decodeKnowledgePage({rows:[summary()],nextAfter:null},{...scope,crop:'水稻'}));
});
test('知识请求仅固定匿名路径，地区作物阶段未选全与非法原版本都不出网',async()=>{
 const {createPublicKnowledge}=await load();const calls=[],api=createPublicKnowledge(async r=>{calls.push(r);return{statusCode:200,data:r.path.endsWith('/scopes')?[scope]:r.path.endsWith(key)?detail():{rows:[summary()],nextAfter:null}}});
 await api.scopes();await api.page(scope,'%_');await api.detail(key,1,scope);assert.equal(calls[2].query.expectedRevision,'1');assert.deepEqual(calls[2].query,{...scope,expectedRevision:'1'});assert.equal(JSON.stringify(calls).includes('Authorization'),false);
 await assert.rejects(api.page({...scope,growthStage:''}));await assert.rejects(api.detail(key,1.5,scope));assert.equal(calls.length,3);
});
test('客户端接受后端LocalDateTime省略零秒但不接受无效日时',async()=>{
 const {decodeKnowledgeDetail}=await load();for(const reviewedAt of ['2026-11-10T10:20','2026-11-10T10:20:00','2026-11-10T10:20:00.123456']){const v=detail();v.article.reviewedAt=reviewedAt;assert.equal(decodeKnowledgeDetail(v,key,1,scope).article.reviewedAt,reviewedAt)}
 for(const reviewedAt of ['2026-02-30T10:20','2026-11-10T24:00','2026-11-10T10:99','2026-11-10T10:20:70']){const v=detail();v.article.reviewedAt=reviewedAt;assert.throws(()=>decodeKnowledgeDetail(v,key,1,scope))}
});

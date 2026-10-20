const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),path=require('node:path')
test('缺失回库证据的成本不得展示成确定毛利，正常和估算记录保留各自标识',()=>{
  const code=fs.readFileSync(path.join(__dirname,'../src/utils/reportCost.ts'),'utf8')
  const ast=ts.createSourceFile('reportCost.ts',code,ts.ScriptTarget.Latest,true)
  const script=ast.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(ast)).join('\n')
  const scope={exports:{}};vm.createContext(scope);vm.runInContext(ts.transpileModule(script,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,scope)
  const unknown=scope.exports.hasUnverifiedCost
  assert.equal(unknown({costAmount:30,profit:30,costWarnings:['缺原回库证据']}),true)
  assert.equal(unknown({costUnavailableSaleAmount:20}),true)
  assert.equal(unknown({costStatus:'待核对',saleAmount:0,costUnavailableSaleAmount:0}),true)
  assert.equal(unknown({costStatus:'估算',costAmount:30}),false)
  assert.equal(unknown({costStatus:'已核对',costAmount:30,costWarnings:[]}),false)
})
for(const view of ['report','dashboard','store-analytics']) test(`${view}真实图表未知成本不画毛利，已核对记录保留`,()=>{
  const source=fs.readFileSync(path.join(__dirname,`../src/views/${view}/index.vue`),'utf8').match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1]
  const ast=ts.createSourceFile('view.ts',source,ts.ScriptTarget.Latest,true)
  let expression
  for(const stmt of ast.statements) if(ts.isVariableStatement(stmt)) for(const d of stmt.declarationList.declarations) if(d.name.getText(ast)===(view==='report'?'salesChartOption':'trendOption')) expression=d.initializer.getText(ast)
  assert.ok(expression,'真实销售趋势计算必须存在')
  const code=fs.readFileSync(path.join(__dirname,'../src/utils/reportCost.ts'),'utf8')
  const helperAst=ts.createSourceFile('reportCost.ts',code,ts.ScriptTarget.Latest,true)
  const script=helperAst.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(helperAst)).join('\n')
  const scope={exports:{},computed:fn=>fn(),C:{primary:'blue',accent:'green'},salesTrend:{value:[{date:'2026-10-19',saleAmount:60,costAmount:30,profit:30,costStatus:'待核对'},{date:'2026-10-20',saleAmount:24,costAmount:12,profit:12,costStatus:'已核对'}]}}
  vm.createContext(scope);vm.runInContext(ts.transpileModule(script,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,scope)
  Object.assign(scope,scope.exports)
  const option=vm.runInContext(ts.transpileModule(`globalThis.actualChart = ${expression}`,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText+';actualChart',scope)
  const profits=option.series.find(s=>s.name==='毛利额').data
  assert.equal(profits[0],null,'历史成本待核对时不能显示确定毛利')
  assert.equal(profits[1],12)
  if(option.tooltip.formatter) {
    const tip=option.tooltip.formatter([{axisValueLabel:'2026-10-19',marker:'',seriesName:'毛利额',value:null}])
    assert.ok(tip.includes('待核对'))
    assert.ok(!tip.includes('¥0.00'))
  }
})

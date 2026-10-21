const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),path=require('node:path')
for(const view of ['dashboard','store-analytics']) test(`${view}回款率未知时不能画作零回款`,()=>{
 const source=fs.readFileSync(path.join(__dirname,`../src/views/${view}/index.vue`),'utf8').match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1]
 const ast=ts.createSourceFile('view.ts',source,ts.ScriptTarget.Latest,true)
 let expression
 for(const stmt of ast.statements) if(ts.isVariableStatement(stmt)) for(const d of stmt.declarationList.declarations) if(d.name.getText(ast)==='riskOption') expression=d.initializer.getText(ast)
 for(const rate of [undefined,null,0,100,37.5]) {
  const scope={computed:fn=>fn(),toNumber:v=>Number(v??0),creditRisk:{value:{repaymentRate:rate}},C:{accent:'green'}}; vm.createContext(scope)
  const option=vm.runInContext(ts.transpileModule(`var chart=${expression}`,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText+';chart',scope)
  const label=option.series[0].label.formatter()
  if(rate==null) {assert.ok(label.includes('—'));assert.equal(option.series[0].data.length,1);assert.ok(!label.includes('0.0%'))}
  else {assert.ok(label.includes(`${rate.toFixed(1)}%`));assert.equal(option.series[0].data[0].value,rate)}
 }
})

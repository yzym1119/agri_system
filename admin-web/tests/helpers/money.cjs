const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript')

const webRoot = path.resolve(__dirname, '../..')

/**
 * 把生产 money.ts 的真实实现注入测试 vm 上下文。
 * 测试因此不必为页面脚本再造一套金额口径，页面改了算法测试会跟着变。
 */
function moneyUtils() {
  const code = fs.readFileSync(path.join(webRoot, 'src/utils/money.ts'), 'utf8')
  const ast = ts.createSourceFile('money.ts', code, ts.ScriptTarget.Latest, true)
  const script = ast.statements.filter(s => !ts.isImportDeclaration(s)).map(s => s.getFullText(ast)).join('\n')
  const scope = {exports: {}}
  vm.createContext(scope)
  vm.runInContext(ts.transpileModule(script, {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020}}).outputText, scope)
  return {subtotalAmount: scope.exports.subtotalAmount, sumAmount: scope.exports.sumAmount}
}

module.exports = {moneyUtils}

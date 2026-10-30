const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const { parse, compileScript } = require('@vue/compiler-sfc');
function setupPage(page, auth, configured = true, extraImports = {}) {
  const file = path.join(__dirname, '../../src/pages',page,'index.vue');
  const { descriptor } = parse(fs.readFileSync(file, 'utf8'));
  const compiled = compileScript(descriptor, { id: page });
  const js = ts.transpileModule(compiled.content, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS } }).outputText;
  const hooks = {}, routes = [], mod = { exports: {} };
  const imports = { vue: require('vue'), '@dcloudio/uni-app': {
    onShow: cb => hooks.show = cb, onHide: cb => hooks.hide = cb,
  }, '../../services/auth': { staffAuth: auth, serviceConfigured: configured }, ...extraImports };
  vm.runInNewContext(js, { module: mod, exports: mod.exports, Error, uni: {
    reLaunch: options => routes.push(options.url), navigateTo: options => routes.push(options.url),
  }, require: name => { assert.ok(name in imports, name); return imports[name]; } }, { filename: file });
  return { state: mod.exports.default.setup({}, { expose() {} }), hooks, routes };
}
module.exports = { setupPage };

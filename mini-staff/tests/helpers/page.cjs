const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const { parse, compileScript } = require('@vue/compiler-sfc');
function setupPage(page, auth, configured = true, extraImports = {}, platform = {}) {
  const file = path.join(__dirname, '../../src/pages',page,'index.vue');
  const { descriptor } = parse(fs.readFileSync(file, 'utf8'));
  const compiled = compileScript(descriptor, { id: page });
  const js = ts.transpileModule(compiled.content, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS } }).outputText;
  const hooks = {}, routes = [], scrolls=[],clipboard=[], mod = { exports: {} };
  const imports = { vue: require('vue'), '@dcloudio/uni-app': {
    onShow: cb => hooks.show = cb, onHide: cb => hooks.hide = cb, onLoad: cb => hooks.load = cb,
  }, '../../services/auth': { staffAuth: auth, serviceConfigured: configured }, ...extraImports };
  vm.runInNewContext(js, { module: mod, exports: mod.exports, Error, uni: {
    ...platform,
    reLaunch: options => routes.push(options.url), navigateTo: options => routes.push(options.url),
    redirectTo: options=>routes.push(options.url),pageScrollTo: options=>scrolls.push(options.selector),
    setClipboardData:options=>{clipboard.push(options.data);options.success?.({errMsg:'ok'});},
  }, require: name => { assert.ok(name in imports, name); return imports[name]; },
  setTimeout, clearTimeout, clearInterval }, { filename: file });
  return { state: mod.exports.default.setup({}, { expose() {} }), hooks, routes,scrolls,clipboard };
}
module.exports = { setupPage };
module.exports.setupService = (name, imports) => {
  const file=path.join(__dirname,'../../src/services',name+'.ts');
  const js=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS}}).outputText;
  const mod={exports:{}};
  vm.runInNewContext(js,{module:mod,exports:mod.exports,Error,encodeURIComponent,require:key=>{assert.ok(key in imports,key);return imports[key];}},{filename:file});
  return mod.exports;
};

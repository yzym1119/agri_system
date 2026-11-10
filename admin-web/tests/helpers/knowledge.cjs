const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');
module.exports=function load(file,mocks={},extra={}){
 const cache={};function read(name,from=''){if(name in mocks)return mocks[name];const target=name.startsWith('@/')?name.slice(2):name.startsWith('.')?path.posix.normalize(path.posix.join(path.posix.dirname(from),name)):name;if(cache[target])return cache[target];const module={exports:{}};cache[target]=module.exports;
  const code=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../../src',target+'.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;
  vm.runInNewContext(code,{module,exports:module.exports,Error,URL,console,crypto:require('node:crypto').webcrypto,...extra,require:name=>read(name,target)});return module.exports;
 }return read(file);
};

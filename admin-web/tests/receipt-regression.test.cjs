const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vue = require('vue')
const {renderToString} = require('@vue/server-renderer')
const {parse} = require('@vue/compiler-sfc')
const {baseParse} = require('@vue/compiler-dom')

for (const change of ['修改批号', '添加批次']) test(`收货${change}后保持采购行展开`, async () => {
  const source = fs.readFileSync(path.join(__dirname,'../src/views/purchase/receipt/index.vue'),'utf8')
  const ast = baseParse(parse(source).descriptor.template.content)
  let key
  function walk(n) {
    if(n.tag==='el-table' && n.props.some(p=>p.name==='bind' && p.arg?.content==='data' && p.exp?.content==='lineRows')) {
      key=n.props.find(p=>p.name==='row-key')?.value?.content
    }
    n.children?.forEach(walk)
  }
  walk(ast)
  const {default: useExpand} = await import('../node_modules/element-plus/es/components/table/src/store/expand.mjs')
  await renderToString(vue.createSSRApp({setup() {
    const row=vue.reactive({purchaseOrderItemId:17,batches:[{supplierBatchNo:''}]})
    const data=vue.ref([row])
    const table=useExpand({data,rowKey:vue.ref(key)})
    table.toggleRowExpansion(row,true)
    if(change==='修改批号') row.batches[0].supplierBatchNo='A-TEST'
    else row.batches.push({supplierBatchNo:''})
    // 真实el-table数据深度监听执行setData时会调用此入口。
    table.updateExpandRows()
    assert.equal(table.isRowExpanded(row),true,'录入批次不应令正在操作的展开行消失')
    return ()=>vue.h('div')
  }}))
})

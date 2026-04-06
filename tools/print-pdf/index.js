#!/usr/bin/env node
const fs = require('fs')
const path = require('path')
const puppeteer = require('puppeteer')

async function buildHtml(od) {
  const escape = (s) => (s == null ? '' : String(s).replace(/[&<>"']/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' })[c]))
  const lines = []
  lines.push('<div style="font-family: Arial, Helvetica, sans-serif; width:320px; padding:12px; color:#000;">')
  lines.push(`<h3 style="text-align:center; margin:0 0 8px 0;">${escape(od.storeName || '门店')}</h3>`)
  lines.push(`<div style="text-align:center; font-size:12px; margin-bottom:8px;">小票 / 订单号: ${escape(od.orderNo)}</div>`)
  lines.push(`<div style="font-size:12px; margin-bottom:8px;">时间: ${escape(od.createdAt)}</div>`)
  if (od.operatorName) lines.push(`<div style="font-size:12px; margin-bottom:8px;">操作员: ${escape(od.operatorName)}</div>`)
  lines.push('<hr style="border:none; border-top:1px dashed #444; margin:8px 0;">')
  lines.push('<div style="font-size:13px;">')
  lines.push('<div style="display:flex; justify-content:space-between; font-weight:600; margin-bottom:6px;"><div>商品</div><div>数量×单价</div><div>小计</div></div>')
  if (od.items && od.items.length) {
    od.items.forEach(it => {
      const name = escape(it.productName || it.name || '')
      const qty = escape(it.quantity)
      const unit = escape(it.unit || '')
      const unitPrice = Number(it.unitPrice || 0).toFixed(2)
      const subtotal = Number(it.subtotal || it.totalPrice || 0).toFixed(2)
      lines.push(`<div style="display:flex; justify-content:space-between; margin-bottom:4px; font-size:12px;"><div style="flex:1">${name}</div><div style="width:110px;text-align:right">${qty}${unit}×${unitPrice}</div><div style="width:80px;text-align:right">¥${subtotal}</div></div>`)
    })
  }
  lines.push('</div>')
  lines.push('<hr style="border:none; border-top:1px dashed #444; margin:8px 0;">')
  lines.push('<div style="font-size:13px;">')
  lines.push(`<div style="display:flex; justify-content:space-between; margin-bottom:4px;"><div>应付</div><div>¥${Number(od.totalAmount||0).toFixed(2)}</div></div>`)
  if (od.discountAmount) lines.push(`<div style="display:flex; justify-content:space-between; margin-bottom:4px;color:#f56c6c;"><div>优惠</div><div>-¥${Number(od.discountAmount).toFixed(2)}</div></div>`)
  if (od.couponAmount) lines.push(`<div style="display:flex; justify-content:space-between; margin-bottom:4px;color:#f56c6c;"><div>优惠券</div><div>-¥${Number(od.couponAmount).toFixed(2)}</div></div>`)
  lines.push(`<div style="display:flex; justify-content:space-between; font-weight:700; font-size:15px; margin-top:6px;"><div>实付</div><div>¥${Number(od.payAmount||0).toFixed(2)}</div></div>`)
  lines.push('</div>')
  lines.push('<hr style="border:none; border-top:1px dashed #444; margin:8px 0;">')
  lines.push('<div style="font-size:13px; margin-bottom:6px;">支付方式明细：</div>')
  if (od.payments && od.payments.length) {
    od.payments.forEach(p => {
      const map = {1:'现金',2:'微信',3:'支付宝',4:'本金余额',5:'优惠券',6:'赊销',10:'混合支付'}
      const name = map[p.type || p.paymentType] || map[p.paymentType] || '其他'
      const amount = Number(p.amount || 0).toFixed(2)
      lines.push(`<div style="display:flex; justify-content:space-between; margin-bottom:2px; font-size:12px;"><div>${escape(name)}</div><div>¥${amount}</div></div>`)
    })
  }
  if (od.remark) {
    lines.push('<hr style="border:none; border-top:1px dashed #444; margin:8px 0;">')
    lines.push(`<div style="font-size:12px; margin-top:6px;">备注：${escape(od.remark)}</div>`)
  }
  lines.push('<div style="text-align:center; margin-top:10px; font-size:12px;">谢谢惠顾，欢迎再次光临</div>')
  lines.push('</div>')
  return lines.join('')
}

async function main() {
  const argv = process.argv.slice(2)
  if (argv.length < 2) {
    console.error('Usage: node index.js <inputJsonPath> <outputDir>')
    process.exit(2)
  }
  const inputPath = path.resolve(argv[0])
  const outputDir = path.resolve(argv[1])
  if (!fs.existsSync(inputPath)) {
    console.error('Input file not found:', inputPath)
    process.exit(3)
  }
  const raw = fs.readFileSync(inputPath, 'utf8')
  let payload = {}
  try {
    payload = JSON.parse(raw)
  } catch (e) {
    console.error('Invalid JSON payload', e)
    process.exit(4)
  }
  const od = payload
  const html = buildHtml(od)
  if (!fs.existsSync(outputDir)) fs.mkdirSync(outputDir, { recursive: true })
  const fileName = `receipt-${od.orderNo || Date.now()}.pdf`
  const outputPath = path.join(outputDir, fileName)
  const browser = await puppeteer.launch({ args: ['--no-sandbox','--disable-setuid-sandbox'] })
  try {
    const page = await browser.newPage()
    await page.setContent(html, { waitUntil: 'networkidle0' })
    await page.pdf({ path: outputPath, width: od.options && od.options.paperWidth ? od.options.paperWidth : '320px', printBackground: true })
    console.log(fileName)
    process.exit(0)
  } catch (err) {
    console.error('PDF generation failed', err)
    process.exit(5)
  } finally {
    await browser.close()
  }
}

main()












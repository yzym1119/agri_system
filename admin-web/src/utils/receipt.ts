import { formatStock } from './number'

export type ReceiptOptions = {
  // 'receipt_80' - 80mm 热敏小票, 'invoice_std' - 标准销售单据/三联单
  paperWidth?: 'receipt_80' | 'invoice_std' | string
  orientation?: 'portrait' | 'landscape'
  title?: string
}

function escapeHtml(s: any) {
  if (s == null) return ''
  return String(s).replace(/[&<>"']/g, (c) => {
    return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' } as any)[c]
  })
}

// Build HTML for two templates:
// - receipt_80: narrow thermal receipt (80mm), compact layout
// - invoice_std: full-width printable invoice with borders and signature area
export function buildReceiptHtml(orderDetail: any, options: ReceiptOptions = {}) {
  const od = orderDetail || {}
  const mode = options.paperWidth || 'receipt_80'
  const orientation = options.orientation || 'portrait'
  const store = escapeHtml(od.storeName || options.title || '门店')
  const customer = escapeHtml(od.customerName || '散客')
  const operator = escapeHtml(od.operatorName || '-')

  if (mode === 'invoice_std') {
    // Standard invoice / three-part form
    const items = (od.items || []).map((it: any, idx: number) => {
      return `<tr>
        <td style="padding:6px;border:1px solid #000;text-align:center;width:40px">${idx + 1}</td>
        <td style="padding:6px;border:1px solid #000">${escapeHtml(it.productName || '')}</td>
        <td style="padding:6px;border:1px solid #000;text-align:center;width:100px">${escapeHtml(it.manufacturer || '')}</td>
        <td style="padding:6px;border:1px solid #000;text-align:center;width:100px">${escapeHtml(it.specification || '')}</td>
        <td style="padding:6px;border:1px solid #000;text-align:center;width:60px">${escapeHtml(it.unit || '')}</td>
        <td style="padding:6px;border:1px solid #000;text-align:right;width:60px">${formatStock(it.quantity)}</td>
        <td style="padding:6px;border:1px solid #000;text-align:right;width:80px">¥${Number(it.unitPrice || 0).toFixed(2)}</td>
        <td style="padding:6px;border:1px solid #000;text-align:right;width:80px">¥${Number(it.totalPrice || 0).toFixed(2)}</td>
      </tr>`
    }).join('')

    const totalQty = (od.items || []).reduce((s: number, it: any) => s + (Number(it.quantity || 0) || 0), 0)
    const totalAmount = Number(od.totalAmount || 0).toFixed(2)
    const discountNum = Number(od.discountAmount || 0)
    const couponNum = Number(od.couponAmount || 0)
    const pay = Number(od.payAmount || 0).toFixed(2)
    const owe = (Number(od.totalAmount || 0) - discountNum - couponNum - Number(od.payAmount || 0))
    const oweNum = owe
    const oweStr = (oweNum > 0.001) ? `¥${oweNum.toFixed(2)}` : null

    const html = `
      <div style="font-family: Arial, Helvetica, sans-serif; color:#000; width:100%; padding:12px;">
        <h2 style="text-align:center; margin:6px 0;">${store} 销售单</h2>
        <div style="display:flex; justify-content:space-between; margin-bottom:8px;">
          <div>
            <div>客户：${customer}</div>
            <div>电话：${escapeHtml(od.receiverPhone || od.customerPhone || '-')}</div>
          </div>
          <div style="text-align:right;">
            <div>单号：${escapeHtml(od.orderNo)}</div>
            <div>日期：${escapeHtml(od.createdAt || '')}</div>
            <div>操作员：${operator}</div>
          </div>
        </div>

        <table style="width:100%; border-collapse:collapse; margin-bottom:8px;">
          <thead>
            <tr>
              <th style="padding:6px;border:1px solid #000;width:40px">序号</th>
              <th style="padding:6px;border:1px solid #000;width:140px">商品名称</th>
              <th style="padding:6px;border:1px solid #000;width:100px">厂家</th>
              <th style="padding:6px;border:1px solid #000;width:80px">规格</th>
              <th style="padding:6px;border:1px solid #000;width:60px">单位</th>
              <th style="padding:6px;border:1px solid #000;width:60px">数量</th>
              <th style="padding:6px;border:1px solid #000;width:80px">单价</th>
              <th style="padding:6px;border:1px solid #000;width:80px">金额</th>
            </tr>
          </thead>
          <tbody>
            ${items}
            <tr>
              <td colspan="5" style="padding:6px;border:1px solid #000;text-align:right;font-weight:700">合计</td>
              <td style="padding:6px;border:1px solid #000;text-align:right;font-weight:700">${totalQty}</td>
              <td style="padding:6px;border:1px solid #000"></td>
              <td style="padding:6px;border:1px solid #000;text-align:right;font-weight:700">¥${totalAmount}</td>
            </tr>
          </tbody>
        </table>

        <div style="margin-bottom:12px;">
        <div style="display:flex; justify-content:flex-end; gap:24px;">
            <div>总金额：¥${totalAmount}</div>
            ${discountNum > 0 ? `<div>优惠：-¥${discountNum.toFixed(2)}</div>` : ''}
            ${couponNum > 0 ? `<div>优惠券：-¥${couponNum.toFixed(2)}</div>` : ''}
            <div style="font-weight:800">实收：¥${pay}</div>
            ${oweStr ? `<div style="font-weight:900; color:#c00">本单欠款：${oweStr}</div>` : ''}
          </div>
        </div>

        <div style="display:flex; justify-content:space-between; margin-top:36px;">
          <div style="width:45%;">
            <div>制单人：${operator}</div>
          </div>
          <div style="width:45%; text-align:left;">
            <div>客户签字确认：______________________________</div>
          </div>
        </div>

        <div style="font-size:12px; color:#666; margin-top:12px;">
          注：请核对商品数量与金额，签字后生效。
        </div>
      </div>
    `

    return { html, meta: { orientation } }
  }

  // Default: receipt_80 - thermal receipt style
  const widthPx = '320px'
  const lines: string[] = []
  lines.push(`<div style="font-family: Arial, Helvetica, sans-serif; width:${widthPx}; padding:10px; color:#000;">`)
  lines.push(`<div style="text-align:center; font-weight:700; font-size:16px; margin-bottom:6px;">${store}</div>`)
  lines.push(`<div style="text-align:center; font-size:13px; margin-bottom:6px;">销售小票</div>`)
  lines.push(`<div style="font-size:12px; margin-bottom:6px;">单号: ${escapeHtml(od.orderNo)}</div>`)
  lines.push(`<div style="font-size:12px; margin-bottom:6px;">时间: ${escapeHtml(od.createdAt)}</div>`)
  lines.push(`<div style="font-size:12px; margin-bottom:6px;">操作员: ${operator}</div>`)
  lines.push(`<div style="font-size:12px; margin-bottom:6px;">客户: ${customer}</div>`)
  lines.push('<hr style="border:none; border-top:1px dashed #444; margin:6px 0;">')
  lines.push('<div style="font-size:12px;">')
  lines.push('<div style="display:flex; justify-content:space-between; font-weight:600; margin-bottom:6px;"><div>商品</div><div>数量×单价</div><div>小计</div></div>')
  if (od.items && od.items.length) {
    od.items.forEach((it: any) => {
      const name = escapeHtml(it.productName || it.name || '')
      const qty = escapeHtml(formatStock(it.quantity))
      const unit = escapeHtml(it.unit || '')
      const unitPrice = Number(it.unitPrice || 0).toFixed(2)
      const subtotal = Number(it.totalPrice || 0).toFixed(2)
      lines.push(`<div style="display:flex; justify-content:space-between; margin-bottom:4px; font-size:12px;"><div style="flex:1">${name}</div><div style="width:120px;text-align:right">${qty}${unit} × ¥${unitPrice}</div><div style="width:80px;text-align:right">¥${subtotal}</div></div>`)
    })
  }
  lines.push('</div>')
  lines.push('<hr style="border:none; border-top:1px dashed #444; margin:6px 0;">')
  lines.push(`<div style="font-size:13px;">应付：¥${Number(od.totalAmount || 0).toFixed(2)}</div>`)
  if (Number(od.discountAmount || 0) > 0) lines.push(`<div style="font-size:12px;color:#f56c6c">优惠：-¥${Number(od.discountAmount).toFixed(2)}</div>`)
  if (Number(od.couponAmount || 0) > 0) lines.push(`<div style="font-size:12px;color:#f56c6c">优惠券：-¥${Number(od.couponAmount).toFixed(2)}</div>`)
  lines.push(`<div style="font-size:15px;font-weight:700;margin-top:6px;">实付：¥${Number(od.payAmount || 0).toFixed(2)}</div>`)
  lines.push('<hr style="border:none; border-top:1px dashed #444; margin:6px 0;">')
  if (od.payments && od.payments.length) {
    od.payments.forEach((p: any) => {
      const ptype = escapeHtml(getPaymentTypeText(p.paymentType || p.paymentType))
      const amount = Number(p.amount || 0).toFixed(2)
      lines.push(`<div style="display:flex; justify-content:space-between; font-size:12px;"><div>${ptype}</div><div>¥${amount}</div></div>`)
    })
  }
  if (od.remark) {
    lines.push('<hr style="border:none; border-top:1px dashed #444; margin:6px 0;">')
    lines.push(`<div style="font-size:12px;">备注：${escapeHtml(od.remark)}</div>`)
  }
  lines.push('<div style="text-align:center; margin-top:8px; font-size:12px;">谢谢惠顾，欢迎再次光临</div>')
  lines.push('</div>')

  return { html: lines.join(''), meta: { width: widthPx, orientation } }
}

export function printReceipt(orderDetail: any, options: ReceiptOptions = {}) {
  const { html } = buildReceiptHtml(orderDetail, options)
  const w = window.open('', '_blank', `width=900,height=900`)
  if (!w) return Promise.reject(new Error('popup blocked'))
  w.document.open()
  w.document.write(`
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8" />
        <title>打印预览</title>
        <style>
          @page { margin: 6mm; }
          @media print {
            @page { margin: 6mm; }
            body { -webkit-print-color-adjust: exact; }
          }
          body { margin:0; padding:0; font-family: Arial, Helvetica, sans-serif; color:#000; }
        </style>
      </head>
      <body>
        ${html}
      </body>
    </html>
  `)
  w.document.close()
  return new Promise<void>((resolve) => {
    setTimeout(() => {
      try { w.focus(); w.print() } catch (e) {}
      resolve()
    }, 300)
  })
}

// Helper for display text in receipt (not exported)
function getPaymentTypeText(type: number) {
  const map: Record<number, string> = {
    1: '现金',
    2: '微信',
    3: '支付宝',
    4: '本金余额',
    5: '红包/优惠券',
    6: '赊销',
    10: '混合支付'
  }
  return map[type] || '其他'
}

// Keep buildReceiptPayload for backwards compatibility if needed (no backend send)
export function buildReceiptPayload(orderDetail: any, options: ReceiptOptions = {}) {
  const od = orderDetail || {}
  return {
    orderNo: od.orderNo,
    storeName: od.storeName,
    createdAt: od.createdAt,
    operatorName: od.operatorName,
    items: (od.items || []).map((it: any) => ({
      name: it.productName,
      quantity: formatStock(it.quantity),
      unit: it.unit,
      unitPrice: Number(it.unitPrice || 0).toFixed(2),
      subtotal: Number(it.totalPrice || 0).toFixed(2)
    })),
    totals: {
      totalAmount: Number(od.totalAmount || 0).toFixed(2),
      discountAmount: Number(od.discountAmount || 0).toFixed(2),
      couponAmount: Number(od.couponAmount || 0).toFixed(2),
      payAmount: Number(od.payAmount || 0).toFixed(2)
    },
    payments: (od.payments || []).map((p: any) => ({ type: p.paymentType, amount: Number(p.amount || 0).toFixed(2) })),
    remark: od.remark || '',
    options
  }
}





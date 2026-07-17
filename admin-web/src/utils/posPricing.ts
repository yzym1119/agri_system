import type { ProductDetailVO, ProductPriceRuleVO } from '@/types/product'
import type { POSCustomerInfo } from '@/types/pos'

type NullableNumber = number | null | undefined

interface PriceContext {
  storeId?: NullableNumber
  customerType?: NullableNumber
  vipLevel?: NullableNumber
}

interface ProductPriceResolveInput extends PriceContext {
  product?: ProductDetailVO | null
  rules?: ProductPriceRuleVO[]
  currentUnit?: string
  fallbackPrice?: number | null
}

interface PackagePriceResolveInput {
  customer?: POSCustomerInfo | null
  packagePrice?: number | null
  vipPrice?: number | null
  fallbackPrice?: number | null
}

const toNullableNumber = (value: NullableNumber): number | null => {
  return typeof value === 'number' && !Number.isNaN(value) ? value : null
}

const toPositiveNumber = (value: unknown): number | null => {
  if (value == null) return null
  const parsed = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) return null
  return parsed
}

const isRuleActive = (rule: ProductPriceRuleVO, now = new Date()): boolean => {
  if (rule.status !== 1) return false
  if (rule.effectiveDate && new Date(rule.effectiveDate) > now) return false
  if (rule.expiryDate && new Date(rule.expiryDate) < now) return false
  return true
}

const findRulePrice = (
  rules: ProductPriceRuleVO[],
  priceTypes: number[],
  expected: { storeId: number | null; customerType: number | null; vipLevel: number | null }
): number | null => {
  for (const priceType of priceTypes) {
    const rule = rules.find((candidate) =>
      candidate.priceType === priceType
      && toNullableNumber(candidate.storeId) === expected.storeId
      && toNullableNumber(candidate.customerType) === expected.customerType
      && toNullableNumber(candidate.vipLevel) === expected.vipLevel
    )
    if (rule) {
      return toPositiveNumber(rule.price)
    }
  }
  return null
}

export const matchProductPriceRule = (
  rules: ProductPriceRuleVO[] | null | undefined,
  context: PriceContext
): number | null => {
  if (!rules || rules.length === 0) return null

  const activeRules = rules.filter((rule) => isRuleActive(rule))
  if (activeRules.length === 0) return null

  const storeId = toNullableNumber(context.storeId)
  const customerType = toNullableNumber(context.customerType)
  const vipLevel = toNullableNumber(context.vipLevel)
  const targetPriceTypes = customerType === 2 ? [2, 1] : [1]

  const candidates = [
    { storeId, customerType, vipLevel },
    { storeId, customerType, vipLevel: null },
    { storeId, customerType: null, vipLevel: null },
    { storeId: null, customerType, vipLevel },
    { storeId: null, customerType, vipLevel: null },
    { storeId: null, customerType: null, vipLevel: null }
  ]

  const dedupedCandidates = candidates.filter((candidate, index) =>
    candidates.findIndex((item) =>
      item.storeId === candidate.storeId
      && item.customerType === candidate.customerType
      && item.vipLevel === candidate.vipLevel
    ) === index
  )

  for (const candidate of dedupedCandidates) {
    const matched = findRulePrice(activeRules, targetPriceTypes, candidate)
    if (matched !== null) {
      return matched
    }
  }

  return null
}

export const resolveProductBasePrice = (
  product?: ProductDetailVO | null,
  customerType?: NullableNumber
): number | null => {
  if (!product) return null

  if (customerType === 2) {
    const vipPrice = toPositiveNumber(product.vipPrice)
    if (vipPrice !== null) return vipPrice
  }

  return toPositiveNumber(product.suggestedRetailPrice)
    ?? toPositiveNumber(product.minPrice)
    ?? toPositiveNumber(product.onlinePrice)
}

export const applyProductUnitPrice = (
  basePrice: number | null,
  product?: ProductDetailVO | null,
  currentUnit?: string
): number | null => {
  if (basePrice === null || !product) return basePrice

  const ratio = toPositiveNumber(product.mainToAuxRatio)
  if (product.auxUnit && ratio !== null) {
    if (currentUnit === product.auxUnit) {
      return basePrice
    }
    return basePrice * ratio
  }

  return basePrice
}

export const resolveProductCartUnitPrice = ({
  product,
  rules,
  storeId,
  customerType,
  vipLevel,
  currentUnit,
  fallbackPrice
}: ProductPriceResolveInput): number | null => {
  const priceFromRule = matchProductPriceRule(rules, { storeId, customerType, vipLevel })
  const basePrice = priceFromRule
    ?? resolveProductBasePrice(product ?? null, customerType)
    ?? toPositiveNumber(fallbackPrice)

  return applyProductUnitPrice(basePrice, product ?? null, currentUnit)
}

export const resolvePackageUnitPrice = ({
  customer,
  packagePrice,
  vipPrice,
  fallbackPrice
}: PackagePriceResolveInput): number | null => {
  if (customer?.type === 2) {
    return toPositiveNumber(vipPrice) ?? toPositiveNumber(packagePrice) ?? toPositiveNumber(fallbackPrice)
  }
  return toPositiveNumber(packagePrice) ?? toPositiveNumber(fallbackPrice) ?? toPositiveNumber(vipPrice)
}

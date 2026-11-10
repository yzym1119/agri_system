import { ref } from 'vue';
const storageKey = 'agri:customer:large-text:v1';
let enabled = false;
try { enabled = uni.getStorageSync(storageKey) === true; } catch { /* 字号读取失败仍可阅读，不影响身份。 */ }
export const largeText = ref(enabled);
export function toggleLargeText() {
  largeText.value = !largeText.value;
  try { uni.setStorageSync(storageKey, largeText.value); } catch { /* 本次仍生效，下次可再次选择。 */ }
}

import { createStaffAuth } from '@agri/mobile-contracts/staff-auth';

const base = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/$/, '');
const httpsOrigin = /^https:\/\/[a-z0-9.-]+(?::\d{1,5})?$/i;
const localOrigin = /^http:\/\/(localhost|127\.0\.0\.1|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+)(?::\d{1,5})?$/;
export const serviceConfigured = httpsOrigin.test(base) || (import.meta.env.DEV && localOrigin.test(base));

export const staffAuth = createStaffAuth(request => {
  if (!serviceConfigured) return Promise.reject(new Error('SERVICE_NOT_CONFIGURED'));
  return new Promise((resolve, reject) => uni.request({
    url: base + request.url, method: request.method, data: request.data,
    header: { 'Content-Type': 'application/json', ...request.header }, timeout: 10000,
    success: response => resolve({ statusCode: response.statusCode, data: response.data }),
    fail: () => reject(new Error('NETWORK_FAILED')),
  }));
});

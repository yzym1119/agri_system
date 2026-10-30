import { createMobilePlatform, type SecureRandomPort } from '@agri/mobile-contracts';
declare const wx: SecureRandomPort;
const random = typeof wx !== 'undefined' && typeof wx.getRandomValues === 'function'
  ? { getRandomValues: (options: Parameters<SecureRandomPort['getRandomValues']>[0]) => wx.getRandomValues(options) } : undefined;
export const mobilePlatform = createMobilePlatform(uni, random);

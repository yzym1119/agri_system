import { createPOSRecovery, type RecoveryActor } from '@agri/mobile-contracts/submission';
import { staffAuth } from './auth';
import { mobilePlatform } from './platform';
export const posRecovery = createPOSRecovery(mobilePlatform, () => staffAuth.current(), () => mobilePlatform.createRequestKey());
const payload = (value: unknown): value is unknown => value !== undefined && value !== null;
export const readPOSResult = (key: string, actor: RecoveryActor) => staffAuth.request({
  url: '/api/admin/orders/pos/submissions/' + encodeURIComponent(key), method: 'GET',
}, payload, actor.epoch);
export const cancelPOSRequest = (key: string, actor: RecoveryActor) => staffAuth.request({
  url: '/api/admin/orders/pos/submissions/' + encodeURIComponent(key) + '/cancel', method: 'POST',
}, payload, actor.epoch);

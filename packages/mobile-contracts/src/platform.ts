/** 窄平台端口：不包含身份、报价或任何钱货写入。 */
type Failure = { errMsg?: string };
type Callbacks<T> = { success(value: T): void; fail(error: Failure): void };
export interface UniPlatformPort {
  scanCode(options: Callbacks<{ result: string; scanType: string }> & { onlyFromCamera: boolean }): void;
  getStorageInfoSync(): { keys: string[] };
  getStorageSync(key: string): unknown;
  setStorageSync(key: string, value: string): void;
  removeStorageSync(key: string): void;
  openLocation(options: Callbacks<void> & { latitude: number; longitude: number; address: string }): void;
}
export interface SecureRandomPort {
  getRandomValues(options: Callbacks<{ randomValues: ArrayBuffer }> & { length: number }): void;
}
export type ScanResult = { status: 'SCANNED'; code: string; scanType: string } | { status: 'CANCELLED' | 'DENIED' };
export type Coordinate = { latitude: number; longitude: number; system: 'GCJ02' };

export function createMobilePlatform(uni: UniPlatformPort, random?: SecureRandomPort) {
  let scanning = false;
  function readStorage(key: string): string | null {
    try {
      if (!uni.getStorageInfoSync().keys.includes(key)) return null;
      const value = uni.getStorageSync(key);
      if (typeof value !== 'string') throw new Error();
      return value;
    } catch { throw new Error('STORAGE_READ_FAILED'); }
  }
  return {
    async scan(): Promise<ScanResult> {
      if (scanning) throw new Error('SCAN_BUSY');
      scanning = true;
      try {
        return await new Promise<ScanResult>((resolve, reject) => {
          uni.scanCode({ onlyFromCamera: true,
            success: result => {
              if (typeof result.result !== 'string' || !result.result.trim() || typeof result.scanType !== 'string') {
                reject(new Error('SCAN_INVALID')); return;
              }
              resolve({ status: 'SCANNED', code: result.result, scanType: result.scanType });
            },
            fail: error => {
              const message = error.errMsg ?? '';
              if (/cancel/i.test(message)) resolve({ status: 'CANCELLED' });
              else if (/auth deny|auth denied|permission denied/i.test(message)) resolve({ status: 'DENIED' });
              else reject(new Error('SCAN_FAILED'));
            },
          });
        });
      } finally { scanning = false; }
    },
    readStorage,
    writeStorage(key: string, value: string): void {
      try {
        uni.setStorageSync(key, value);
        if (readStorage(key) !== value) throw new Error();
      } catch { throw new Error('STORAGE_WRITE_FAILED'); }
    },
    removeStorage(key: string): void {
      try {
        uni.removeStorageSync(key);
        if (readStorage(key) !== null) throw new Error();
      } catch { throw new Error('STORAGE_REMOVE_FAILED'); }
    },
    async secureRandom(size: number): Promise<Uint8Array> {
      if (!Number.isInteger(size) || size < 1 || size > 65536) throw new Error('RANDOM_SIZE');
      if (!random) throw new Error('RANDOM_UNAVAILABLE');
      return new Promise((resolve, reject) => random.getRandomValues({ length: size,
        success: result => {
          if (!(result.randomValues instanceof ArrayBuffer) || result.randomValues.byteLength !== size) {
            reject(new Error('RANDOM_INVALID')); return;
          }
          resolve(new Uint8Array(result.randomValues));
        },
        fail: () => reject(new Error('RANDOM_FAILED')),
      }));
    },
    async createRequestKey(): Promise<string> {
      const bytes = await this.secureRandom(16);
      bytes[6] = (bytes[6]! & 15) | 64;
      bytes[8] = (bytes[8]! & 63) | 128;
      const h = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
      return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
    },
    async navigateToAddress(address: string, coordinate?: Coordinate): Promise<{ status: 'ADDRESS_ONLY' | 'OPENED'; address: string }> {
      if (!address.trim()) throw new Error('ADDRESS_REQUIRED');
      if (!coordinate) return { status: 'ADDRESS_ONLY', address };
      if (coordinate.system !== 'GCJ02' || !Number.isFinite(coordinate.latitude) || !Number.isFinite(coordinate.longitude)
        || Math.abs(coordinate.latitude) > 90 || Math.abs(coordinate.longitude) > 180) throw new Error('COORDINATE_INVALID');
      await new Promise<void>((resolve, reject) => uni.openLocation({ latitude: coordinate.latitude,
        longitude: coordinate.longitude, address, success: () => resolve(), fail: () => reject(new Error('NAVIGATION_FAILED')) }));
      return { status: 'OPENED', address };
    },
  };
}

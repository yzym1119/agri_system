import type { CustomerRequest } from '@agri/mobile-contracts/customer-session';
import type { HttpResponse } from '@agri/mobile-contracts/session';
import type { PublicRequest } from '@agri/mobile-contracts/customer-catalog';
type Options = CustomerRequest & { timeout: number; success(response: HttpResponse): void; fail(error: unknown): void };

function origin(value: string, development: boolean): string | null {
  const source = value.trim().replace(/\/$/, '');
  const match = /^(https?):\/\/([a-z0-9.-]+)(?::([0-9]{1,5}))?$/i.exec(source);
  if (!match) return null;
  const [, protocol, host, port] = match;
  if (port && (Number(port) < 1 || Number(port) > 65535)) return null;
  if (!host || host.length > 253 || !host.split('.').every(part => /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/i.test(part))) return null;
  const ip = /^\d+\.\d+\.\d+\.\d+$/.test(host) ? host.split('.').map(Number) : null;
  if (ip && ip.some(part => part > 255)) return null;
  if (protocol?.toLowerCase() === 'https') return source;
  const local = host === 'localhost' || host === '127.0.0.1'
    || !!ip && (ip[0] === 10 || ip[0] === 192 && ip[1] === 168 || ip[0] === 172 && ip[1]! >= 16 && ip[1]! <= 31);
  return development && local ? source : null;
}
/** 只向显式源站的既有客户身份接口发送，不带开发手机号入口或通用任意URL能力。 */
export function createCustomerTransport(value: string, development: boolean, request: (options: Options) => void) {
  const base = origin(value, development);
  return {
    configured: base !== null,
    origin: base,
    async sendPublic(input: PublicRequest): Promise<HttpResponse> {
      if (!base) throw Error('SERVICE_NOT_CONFIGURED');
      const list=input.path==='/api/public/catalog/products',stores=input.path==='/api/public/stores';
      const detail=/^\/api\/public\/catalog\/products\/[1-9][0-9]{0,18}$/.test(input.path);
      if(!list&&!stores&&!detail||!input.query||Object.entries(input.query).some(([key,value])=>typeof value!=='string'||value.length>100||/[\u0000-\u001f\u007f]/.test(value)||!(key==='after'&&(list||stores)||key==='keyword'&&list||key==='unit'&&detail)))throw Error('PUBLIC_REQUEST_INVALID');
      const query=Object.entries(input.query).map(([key,value])=>encodeURIComponent(key)+'='+encodeURIComponent(value)).join('&');
      return new Promise((resolve,reject)=>{
        try{request({url:base+input.path+(query?'?'+query:''),method:'GET',header:{'Content-Type':'application/json'},timeout:10000,success:response=>resolve({statusCode:response.statusCode,data:response.data}),fail:()=>reject(Error('NETWORK_FAILED'))});}
        catch{reject(Error('NETWORK_FAILED'));}
      });
    },
    async send(input: CustomerRequest): Promise<HttpResponse> {
      if (!base) throw Error('SERVICE_NOT_CONFIGURED');
      const post = /^\/auth\/client\/(?:logout|onboarding\/(?:wechat|phone|registration))$/;
      const get = /^\/auth\/client\/(?:info|onboarding\/info|onboarding\/registration\/[0-9a-f-]{36})$/;
      if (!(input.method === 'POST' && post.test(input.url) || input.method === 'GET' && get.test(input.url))
        || input.header && Object.entries(input.header).some(([key, token]) => key !== 'Authorization' || !/^[\x21-\x7e]{1,1024}$/.test(token)))
        throw Error('CUSTOMER_REQUEST_INVALID');
      return new Promise((resolve, reject) => {
        try {
          request({ ...input, url: base + input.url, header: { 'Content-Type': 'application/json', ...input.header }, timeout: 10000,
            success: response => resolve({ statusCode: response.statusCode, data: response.data }),
            fail: () => reject(Error('NETWORK_FAILED')) });
        } catch { reject(Error('NETWORK_FAILED')); }
      });
    },
  };
}

import { createRecorderProbe } from '@agri/mobile-contracts/recorder';

// RecorderManager为平台共享对象，一次绑定事件；页面只订阅并在卸载时撤销订阅。
let probe: ReturnType<typeof createRecorderProbe> | undefined;
export function getDeviceRecorder() {
  if (!probe) probe = createRecorderProbe(uni.getRecorderManager());
  return probe;
}

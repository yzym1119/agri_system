export type RecordingState = {
  phase: 'IDLE' | 'STARTING' | 'RECORDING' | 'STOPPING' | 'READY' | 'ERROR';
  clip: string;
};
export interface RecorderPort {
  start(options: { duration: number; format: 'mp3' }): void;
  stop(): void;
  onStart(callback: () => void): void;
  onStop(callback: (result: { tempFilePath?: string; duration?: number }) => void): void;
  onError(callback: (error: unknown) => void): void;
}

/** 单个RecorderManager只绑定一次事件；页面订阅可撤销，不向外上传临时录音。 */
export function createRecorderProbe(manager: RecorderPort) {
  let visible = false;
  let keepResult = false;
  let state: RecordingState = { phase: 'IDLE', clip: '' };
  const subscribers = new Set<(state: RecordingState) => void>();
  const busy = () => ['STARTING', 'RECORDING', 'STOPPING'].includes(state.phase);
  function emit(phase: RecordingState['phase'], clip = '') {
    state = { phase, clip };
    subscribers.forEach(callback => callback({ ...state }));
  }
  function fail() { keepResult = false; emit(visible ? 'ERROR' : 'IDLE'); }
  function stopManager() {
    emit('STOPPING');
    // stop异常也可能仍在录，保持隔离直至平台明确停止或报错。
    try { manager.stop(); } catch { keepResult = false; }
  }
  manager.onStart(() => {
    if (state.phase !== 'STARTING' || !visible) {
      if (state.phase === 'STOPPING') stopManager();
      return;
    }
    emit('RECORDING');
  });
  manager.onStop(result => {
    if (!busy()) return;
    if (!visible || !keepResult) { emit('IDLE'); return; }
    if (typeof result.tempFilePath !== 'string' || !result.tempFilePath.trim()
      || !Number.isFinite(result.duration) || result.duration! <= 0) { fail(); return; }
    emit('READY', result.tempFilePath);
  });
  manager.onError(() => { if (busy()) fail(); });
  return {
    snapshot: (): RecordingState => ({ ...state }),
    subscribe(callback: (state: RecordingState) => void) {
      subscribers.add(callback); callback({ ...state });
      return () => { subscribers.delete(callback); };
    },
    show() { visible = true; },
    hide() {
      visible = false; keepResult = false;
      if (busy()) stopManager(); else emit('IDLE');
    },
    start() {
      if (!visible || busy()) return;
      keepResult = true; emit('STARTING');
      try { manager.start({ duration: 10000, format: 'mp3' }); } catch { fail(); }
    },
    stop() { if (state.phase === 'STARTING' || state.phase === 'RECORDING') stopManager(); },
    clear() { keepResult = false; if (busy()) stopManager(); else emit('IDLE'); },
  };
}

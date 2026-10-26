import { Injectable, NgZone, inject, signal } from '@angular/core';
import { LogService } from '../core/log/log.service';

@Injectable({
  providedIn: 'root'
})
export class LevelMeterService {
  private log = inject(LogService);
  private ngZone = inject(NgZone);

  private audioCtx: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private mediaStream: MediaStream | null = null;
  private animationId: number | null = null;

  private rmsSignal = signal<number>(0);
  readonly rmsLevel = this.rmsSignal.asReadonly();

  async startMeter(deviceId?: string) {
    if (typeof window === 'undefined' || !navigator.mediaDevices) return;
    this.log.info('LevelMeter', 'Iniciando vúmetro RMS (audio limpio)...');
    this.log.setPipelineStepStatus('audio', 'active');
    try {
      const constraints: MediaStreamConstraints = {
        audio: deviceId ? { deviceId: { exact: deviceId } } : true
      };

      try {
        this.mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
      } catch (err) {
        this.log.warn('LevelMeter', 'Fallback a micrófono predeterminado.');
        this.mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      }

      this.audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
      const source = this.audioCtx.createMediaStreamSource(this.mediaStream);
      this.analyser = this.audioCtx.createAnalyser();
      this.analyser.fftSize = 512;
      source.connect(this.analyser);

      const bufferLength = this.analyser.frequencyBinCount;
      const dataArray = new Uint8Array(bufferLength);

      this.ngZone.runOutsideAngular(() => {
        const updateRms = () => {
          if (!this.analyser) return;
          this.analyser.getByteTimeDomainData(dataArray);

          let sumSquares = 0.0;
          for (let i = 0; i < bufferLength; i++) {
            const normalized = (dataArray[i] - 128) / 128.0;
            sumSquares += normalized * normalized;
          }
          const rms = Math.sqrt(sumSquares / bufferLength);
          const level = Math.min(100, Math.round(rms * 250));

          this.ngZone.run(() => {
            this.rmsSignal.set(level);
          });

          this.animationId = requestAnimationFrame(updateRms);
        };
        updateRms();
      });

      this.log.info('LevelMeter', 'Vúmetro RMS iniciado correctamente.');
      this.log.setPipelineStepStatus('audio', 'ok');
    } catch (err: any) {
      this.log.error('LevelMeter', `Error al iniciar vúmetro RMS: ${err.message}`, 'LEVEL_METER_ERR');
      this.log.setPipelineStepStatus('audio', 'error');
    }
  }

  stopMeter() {
    if (this.animationId) {
      cancelAnimationFrame(this.animationId);
      this.animationId = null;
    }
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(track => track.stop());
      this.mediaStream = null;
    }
    if (this.audioCtx && this.audioCtx.state !== 'closed') {
      this.audioCtx.close();
      this.audioCtx = null;
    }
    this.analyser = null;
    this.rmsSignal.set(0);
    this.log.info('LevelMeter', 'Vúmetro RMS detenido.');
  }
}

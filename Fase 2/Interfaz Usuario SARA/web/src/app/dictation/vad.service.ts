import { Injectable, inject, signal, effect } from '@angular/core';
import { LevelMeterService } from './level-meter.service';
import { LocalWhisperService } from './local-whisper.service';
import { LogService } from '../core/log/log.service';

@Injectable({
  providedIn: 'root'
})
export class VadService {
  private levelMeter = inject(LevelMeterService);
  private whisper = inject(LocalWhisperService);
  private log = inject(LogService);

  private isAutoModeSignal = signal<boolean>(false);
  readonly isAutoMode = this.isAutoModeSignal.asReadonly();

  private silenceTimer: any = null;
  private speechCount = 0;

  constructor() {
    effect(() => {
      if (!this.isAutoModeSignal()) return;
      const rms = this.levelMeter.rmsLevel();

      if (rms > 18) {
        this.speechCount++;
        if (this.speechCount >= 2 && !this.whisper.isListening()) {
          this.log.info('VAD', '¡Voz detectada automáticamente! Iniciando dictado local con Whisper...');
          this.whisper.startDictation(undefined, 'spanish');
          this.resetSilenceTimer();
        } else if (this.whisper.isListening()) {
          this.resetSilenceTimer();
        }
      }
    });
  }

  toggleAutoMode(enabled: boolean) {
    this.isAutoModeSignal.set(enabled);
    if (enabled) {
      this.log.info('VAD', 'Modo Automático por Detección de Voz (VAD) activado. Habla cuando quieras.');
      this.levelMeter.startMeter();
    } else {
      this.log.info('VAD', 'Modo Automático desactivado.');
      this.whisper.stopDictation();
      this.levelMeter.stopMeter();
    }
  }

  private resetSilenceTimer() {
    if (this.silenceTimer) clearTimeout(this.silenceTimer);
    this.silenceTimer = setTimeout(() => {
      if (this.whisper.isListening() && this.isAutoModeSignal()) {
        this.log.info('VAD', 'Silencio detectado. Pausando escucha automática hasta nueva voz...');
        this.whisper.stopDictation();
        this.speechCount = 0;
      }
    }, 4000);
  }
}

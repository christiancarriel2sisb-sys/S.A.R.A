import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AudioDevicesService } from './audio-devices.service';
import { LevelMeterService } from './level-meter.service';
import { LocalWhisperService } from './local-whisper.service';
import { VadService } from './vad.service';
import { LogService } from '../core/log/log.service';
import { PipelineBarComponent } from '../core/log/pipeline-bar.component';
import { LogPanelComponent } from '../core/log/log-panel.component';

@Component({
  selector: 'app-dictation',
  standalone: true,
  imports: [CommonModule, FormsModule, PipelineBarComponent, LogPanelComponent],
  template: `
    <div class="dictation-container">
      <header class="app-header">
        <h1>🎙️ SARA - Dictado Local con Whisper & VAD Automático</h1>
        <p class="subtitle">Reconocimiento 100% local con modo Manos Libres (Detección automática de voz)</p>
      </header>

      <app-pipeline-bar></app-pipeline-bar>

      @if (whisper.isLoading()) {
        <div class="model-loading-banner">
          <div class="spinner"></div>
          <span>{{ whisper.progress() }}</span>
        </div>
      }

      <div class="control-panel">
        <!-- Audio Devices -->
        <div class="control-group">
          <label for="micSelect">Dispositivo de Audio:</label>
          <div class="select-row">
            <select id="micSelect" [value]="audioDevices.selectedDeviceId()" (change)="onDeviceChange($event)">
              @for (device of audioDevices.devices(); track device.deviceId) {
                <option [value]="device.deviceId">{{ device.label }}</option>
              }
            </select>
            <button class="btn-secondary" (click)="refreshDevices()" title="Actualizar dispositivos">🔄</button>
          </div>
        </div>

        <!-- Language Selector -->
        <div class="control-group">
          <label for="langSelect">Idioma:</label>
          <select id="langSelect" [(ngModel)]="selectedLanguage" (change)="onLanguageChange()">
            <option value="spanish">Español</option>
            <option value="english">English</option>
          </select>
        </div>

        <!-- Mode Toggle (Manual vs Auto VAD) -->
        <div class="control-group">
          <label>Modo de Operación:</label>
          <button class="btn-mode" [class.active-mode]="vad.isAutoMode()" (click)="toggleVadMode()">
            {{ vad.isAutoMode() ? '🤖 Automático (VAD)' : '🎛️ Manual (Botón)' }}
          </button>
        </div>

        <!-- Start/Stop Dictation Button (Only enabled in Manual mode) -->
        @if (!vad.isAutoMode()) {
          <div class="control-group action-group">
            @if (!whisper.isListening()) {
              <button class="btn-primary start-btn" (click)="startDictation()" [disabled]="whisper.isLoading()">
                {{ whisper.isLoading() ? 'Cargando Modelo...' : '▶ Iniciar Dictado' }}
              </button>
            } @else {
              <button class="btn-primary stop-btn" (click)="stopDictation()">⏹ Detener Dictado</button>
            }
          </div>
        } @else {
          <div class="control-group action-group">
            <div class="vad-status" [class.listening]="whisper.isListening()">
              {{ whisper.isListening() ? '🔴 Escuchando voz...' : '⏳ Esperando tu voz...' }}
            </div>
          </div>
        }
      </div>

      <!-- RMS Vumeter -->
      <div class="vumeter-container">
        <div class="vumeter-label">
          <span>Vúmetro RMS en Tiempo Real (Supresión de Ruido Activa)</span>
          <span class="vumeter-value">{{ levelMeter.rmsLevel() }}%</span>
        </div>
        <div class="vumeter-bar-bg">
          <div class="vumeter-bar-fill" [style.width.%]="levelMeter.rmsLevel()" [ngClass]="getVumeterClass()"></div>
        </div>
      </div>

      <!-- Realtime Transcript Area -->
      <div class="transcript-section">
        <div class="transcript-header">
          <span>Área de Transcripción (Whisper Local)</span>
          <div class="transcript-actions">
            <button class="btn-action" (click)="copyToClipboard()">📋 Copiar</button>
            <button class="btn-action" (click)="whisper.clearTranscript()">🗑️ Limpiar</button>
          </div>
        </div>
        <div class="transcript-box" #transcriptBox>
          <span class="final-text">{{ whisper.finalText() }}</span>
          @if (!whisper.finalText()) {
            <span class="placeholder">El texto dictado aparecerá aquí automáticamente al hablar...</span>
          }
        </div>
      </div>

      <!-- Log Panel -->
      <app-log-panel></app-log-panel>
    </div>
  `,
  styles: [`
    .dictation-container {
      max-width: 900px;
      margin: 30px auto;
      padding: 24px;
      background: #0f111a;
      color: #cdd6f4;
      border-radius: 12px;
      font-family: system-ui, -apple-system, sans-serif;
      box-shadow: 0 8px 24px rgba(0,0,0,0.5);
    }
    .app-header {
      text-align: center;
      margin-bottom: 20px;
    }
    .app-header h1 {
      font-size: 24px;
      color: #f5e0dc;
      margin-bottom: 6px;
    }
    .subtitle {
      color: #a6adc8;
      font-size: 14px;
    }
    .model-loading-banner {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      background: #1e1e2e;
      border: 1px solid #3b82f6;
      padding: 12px;
      border-radius: 8px;
      margin-bottom: 20px;
      color: #60a5fa;
      font-weight: bold;
    }
    .spinner {
      width: 20px;
      height: 20px;
      border: 3px solid rgba(96, 165, 250, 0.3);
      border-top-color: #60a5fa;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
    .control-panel {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr 1fr;
      gap: 16px;
      background: #1e1e2e;
      padding: 16px;
      border-radius: 8px;
      align-items: end;
      margin-bottom: 20px;
      border: 1px solid #313244;
    }
    @media (max-width: 900px) {
      .control-panel { grid-template-columns: 1fr; }
    }
    .control-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .control-group label {
      font-size: 12px;
      color: #bac2de;
      font-weight: bold;
    }
    .select-row {
      display: flex;
      gap: 8px;
    }
    select, input {
      background: #11111b;
      border: 1px solid #45475a;
      color: #cdd6f4;
      padding: 8px 12px;
      border-radius: 6px;
      font-size: 14px;
      width: 100%;
    }
    .btn-secondary {
      background: #313244;
      border: 1px solid #45475a;
      color: #cdd6f4;
      padding: 0 12px;
      border-radius: 6px;
      cursor: pointer;
    }
    .btn-secondary:hover { background: #45475a; }
    .btn-mode {
      background: #313244;
      border: 1px solid #45475a;
      color: #cdd6f4;
      padding: 9px 12px;
      border-radius: 6px;
      cursor: pointer;
      font-weight: bold;
      font-size: 13px;
      transition: all 0.2s;
    }
    .btn-mode:hover { background: #45475a; }
    .btn-mode.active-mode {
      background: #3b82f6;
      border-color: #60a5fa;
      color: #fff;
    }
    .action-group {
      justify-content: flex-end;
    }
    .btn-primary {
      padding: 10px 16px;
      border: none;
      border-radius: 6px;
      font-weight: bold;
      cursor: pointer;
      font-size: 14px;
      color: #fff;
      transition: all 0.2s;
      width: 100%;
    }
    .btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
    .start-btn {
      background: #10b981;
    }
    .start-btn:hover:not(:disabled) { background: #059669; }
    .stop-btn {
      background: #ef4444;
      animation: pulse-btn 1.5s infinite;
    }
    .stop-btn:hover { background: #dc2626; }
    .vad-status {
      padding: 10px;
      text-align: center;
      border-radius: 6px;
      font-weight: bold;
      font-size: 13px;
      background: #11111b;
      border: 1px dashed #45475a;
      color: #a6adc8;
    }
    .vad-status.listening {
      background: rgba(16, 185, 129, 0.15);
      border: 1px solid #10b981;
      color: #34d399;
      animation: pulse-vad 1.5s infinite;
    }
    @keyframes pulse-vad {
      0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.4); }
      70% { box-shadow: 0 0 0 8px rgba(16, 185, 129, 0); }
      100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
    }
    @keyframes pulse-btn {
      0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); }
      70% { box-shadow: 0 0 0 10px rgba(239, 68, 68, 0); }
      100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
    }
    .vumeter-container {
      background: #1e1e2e;
      padding: 16px;
      border-radius: 8px;
      border: 1px solid #313244;
      margin-bottom: 20px;
    }
    .vumeter-label {
      display: flex;
      justify-content: space-between;
      font-size: 13px;
      margin-bottom: 8px;
      color: #bac2de;
      font-weight: bold;
    }
    .vumeter-bar-bg {
      height: 12px;
      background: #11111b;
      border-radius: 6px;
      overflow: hidden;
      border: 1px solid #45475a;
    }
    .vumeter-bar-fill {
      height: 100%;
      width: 0%;
      transition: width 0.05s linear;
      background: #3b82f6;
    }
    .vumeter-bar-fill.normal { background: #10b981; }
    .vumeter-bar-fill.high { background: #f59e0b; }
    .vumeter-bar-fill.peak { background: #ef4444; }
    .transcript-section {
      background: #1e1e2e;
      border-radius: 8px;
      border: 1px solid #313244;
      overflow: hidden;
    }
    .transcript-header {
      background: #181825;
      padding: 10px 16px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: bold;
      font-size: 13px;
      border-bottom: 1px solid #313244;
    }
    .transcript-actions {
      display: flex;
      gap: 8px;
    }
    .btn-action {
      background: #313244;
      border: 1px solid #45475a;
      color: #cdd6f4;
      padding: 4px 10px;
      border-radius: 4px;
      cursor: pointer;
      font-size: 12px;
    }
    .btn-action:hover { background: #45475a; }
    .transcript-box {
      min-height: 160px;
      max-height: 300px;
      overflow-y: auto;
      padding: 16px;
      font-size: 16px;
      line-height: 1.6;
      background: #11111b;
      color: #cdd6f4;
      white-space: pre-wrap;
      word-break: break-word;
    }
    .final-text {
      color: #cdd6f4;
    }
    .placeholder {
      color: #6c7086;
      font-style: italic;
    }
  `]
})
export class DictationComponent implements OnInit, OnDestroy {
  audioDevices = inject(AudioDevicesService);
  levelMeter = inject(LevelMeterService);
  whisper = inject(LocalWhisperService);
  vad = inject(VadService);
  log = inject(LogService);

  selectedLanguage = 'spanish';

  async ngOnInit() {
    this.log.info('DictationUI', 'Inicializando componente con soporte VAD...');
    try {
      await this.audioDevices.initDevices();
      this.whisper.loadModel();
    } catch (e) {
      this.log.error('DictationUI', 'No se pudieron inicializar los dispositivos o el modelo.');
    }
  }

  ngOnDestroy() {
    this.stopDictation();
    this.vad.toggleAutoMode(false);
  }

  async refreshDevices() {
    await this.audioDevices.refreshDevices();
    this.log.info('DictationUI', 'Dispositivos de audio actualizados.');
  }

  onDeviceChange(event: any) {
    const deviceId = event.target.value;
    this.audioDevices.selectDevice(deviceId);
    if (this.whisper.isListening() || this.vad.isAutoMode()) {
      this.levelMeter.stopMeter();
      this.levelMeter.startMeter(deviceId);
    }
  }

  onLanguageChange() {
    this.log.info('DictationUI', `Idioma cambiado a: ${this.selectedLanguage}`);
    this.whisper.setLanguage(this.selectedLanguage);
  }

  toggleVadMode() {
    const newState = !this.vad.isAutoMode();
    if (newState) {
      if (this.whisper.isLoading()) {
        alert('Espera a que termine de cargar el modelo Whisper local antes de activar el modo automático.');
        return;
      }
      const deviceId = this.audioDevices.selectedDeviceId();
      this.vad.toggleAutoMode(true);
    } else {
      this.vad.toggleAutoMode(false);
    }
  }

  async startDictation() {
    const deviceId = this.audioDevices.selectedDeviceId();
    await this.levelMeter.startMeter(deviceId);
    await this.whisper.startDictation(deviceId, this.selectedLanguage);
  }

  stopDictation() {
    this.levelMeter.stopMeter();
    this.whisper.stopDictation();
  }

  copyToClipboard() {
    const text = this.whisper.finalText().trim();
    if (!text) {
      alert('No hay texto para copiar.');
      return;
    }
    navigator.clipboard.writeText(text).then(() => {
      this.log.info('DictationUI', 'Texto copiado al portapapeles.');
      alert('Texto copiado al portapapeles.');
    }).catch(err => {
      this.log.error('DictationUI', `Error al copiar: ${err}`, 'CLIPBOARD_ERR');
    });
  }

  getVumeterClass(): string {
    const lvl = this.levelMeter.rmsLevel();
    if (lvl > 75) return 'peak';
    if (lvl > 45) return 'high';
    return 'normal';
  }
}

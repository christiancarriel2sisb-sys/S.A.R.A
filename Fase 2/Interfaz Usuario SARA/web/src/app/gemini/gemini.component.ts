import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GeminiConnectionService } from './gemini-connection.service';
import { LocalWhisperService } from '../dictation/local-whisper.service';
import { SpeechSynthesisAdapter } from '../core/adapters/speech-synthesis.adapter';
import { AudioDevicesService } from '../dictation/audio-devices.service';
import { LogService } from '../core/log/log.service';
import { LogPanelComponent } from '../core/log/log-panel.component';

@Component({
  selector: 'app-gemini',
  standalone: true,
  imports: [CommonModule, FormsModule, LogPanelComponent],
  template: `
    <div class="gemini-container">
      <header class="app-header">
        <h1>🤖 SARA - Conversación Inteligente (Gemini + Local Whisper)</h1>
        <p class="subtitle">Interactúa con Sara mediante voz o texto con respuestas habladas</p>
      </header>

      <!-- Connection Status Banner -->
      <div class="status-banner" [class.connected]="geminiConn.isConnected()" [class.disconnected]="!geminiConn.isConnected()">
        <span>{{ geminiConn.isConnected() ? '🟢 API de Gemini Conectada y Operativa (gemini-3.5-flash-lite)' : '🔴 API de Gemini Desconectada o con Error' }}</span>
        <button class="btn-secondary" (click)="checkApiStatus()">Comprobar Conexión</button>
      </div>

      <!-- Chat & Voice Interaction Card -->
      <div class="card chat-card">
        <div class="chat-header">
          <span>💬 Conversación con Sara</span>
          <button class="btn-secondary" (click)="geminiConn.clearHistory()">Limpiar Chat</button>
        </div>

        <div class="chat-history">
          @for (msg of geminiConn.history(); track $index) {
            <div class="chat-bubble" [ngClass]="msg.role">
              <span class="bubble-sender">{{ msg.role === 'user' ? 'Tú' : 'Sara' }}</span>
              <p>{{ msg.text }}</p>
            </div>
          } @empty {
            <div class="empty-chat">
              Presiona el botón de micrófono para hablar o escribe tu mensaje abajo...
            </div>
          }
        </div>

        <!-- Input Controls -->
        <div class="chat-controls">
          <!-- Voice Toggle Button -->
          @if (!isVoiceActive) {
            <button class="btn-mic" (click)="startVoiceChat()" [disabled]="localWhisper.isLoading()">
              {{ localWhisper.isLoading() ? '⏳ Cargando Whisper...' : '🎤 Hablar con Sara' }}
            </button>
          } @else {
            <button class="btn-mic recording" (click)="stopAndSendVoiceChat()">
              ⏹ Detener y Enviar Voz
            </button>
          }

          <!-- Text Input -->
          <input type="text" [(ngModel)]="inputText" (keydown.enter)="sendTextMessage()" placeholder="Escribe tu mensaje y presiona Enviar..." />
          <button class="btn-primary" (click)="sendTextMessage()">Enviar</button>
        </div>
      </div>

      <app-log-panel></app-log-panel>
    </div>
  `,
  styles: [`
    .gemini-container {
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
    .status-banner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 16px;
      border-radius: 8px;
      margin-bottom: 16px;
      font-size: 13px;
      font-weight: bold;
      border: 1px solid #313244;
    }
    .status-banner.connected { background: rgba(16, 185, 129, 0.15); border-color: #10b981; color: #34d399; }
    .status-banner.disconnected { background: rgba(239, 68, 68, 0.15); border-color: #ef4444; color: #f87171; }
    .card {
      background: #1e1e2e;
      border: 1px solid #313244;
      border-radius: 8px;
      padding: 20px;
      margin-bottom: 20px;
    }
    input {
      background: #11111b;
      border: 1px solid #45475a;
      color: #cdd6f4;
      padding: 10px 14px;
      border-radius: 6px;
      font-size: 14px;
      flex: 1;
    }
    .btn-primary {
      background: #3b82f6;
      border: none;
      color: #fff;
      padding: 10px 18px;
      border-radius: 6px;
      font-weight: bold;
      cursor: pointer;
    }
    .btn-primary:hover { background: #2563eb; }
    .btn-secondary {
      background: #313244;
      border: 1px solid #45475a;
      color: #cdd6f4;
      padding: 6px 12px;
      border-radius: 6px;
      cursor: pointer;
      font-size: 12px;
    }
    .chat-card {
      display: flex;
      flex-direction: column;
      height: 500px;
    }
    .chat-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: bold;
      padding-bottom: 12px;
      border-bottom: 1px solid #313244;
    }
    .chat-history {
      flex: 1;
      overflow-y: auto;
      padding: 16px 0;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .chat-bubble {
      max-width: 75%;
      padding: 12px 16px;
      border-radius: 12px;
      font-size: 14px;
      line-height: 1.5;
    }
    .chat-bubble.user {
      align-self: flex-end;
      background: #2563eb;
      color: #fff;
      border-bottom-right-radius: 2px;
    }
    .chat-bubble.model {
      align-self: flex-start;
      background: #313244;
      color: #cdd6f4;
      border-bottom-left-radius: 2px;
    }
    .bubble-sender {
      display: block;
      font-size: 11px;
      opacity: 0.7;
      margin-bottom: 4px;
      font-weight: bold;
    }
    .empty-chat {
      text-align: center;
      color: #6c7086;
      margin: auto;
      font-style: italic;
    }
    .chat-controls {
      display: flex;
      gap: 12px;
      padding-top: 12px;
      border-top: 1px solid #313244;
      align-items: center;
    }
    .btn-mic {
      background: #10b981;
      border: none;
      color: #fff;
      padding: 10px 16px;
      border-radius: 6px;
      font-weight: bold;
      cursor: pointer;
      user-select: none;
      white-space: nowrap;
    }
    .btn-mic.recording {
      background: #ef4444;
      animation: pulse-mic 1.5s infinite;
    }
    @keyframes pulse-mic {
      0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); }
      70% { box-shadow: 0 0 0 10px rgba(239, 68, 68, 0); }
      100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
    }
  `]
})
export class GeminiComponent implements OnInit, OnDestroy {
  geminiConn = inject(GeminiConnectionService);
  localWhisper = inject(LocalWhisperService);
  speechSynth = inject(SpeechSynthesisAdapter);
  audioDevices = inject(AudioDevicesService);
  log = inject(LogService);

  inputText = '';
  isVoiceActive = false;

  async ngOnInit() {
    await this.audioDevices.initDevices();
    await this.checkApiStatus();
    this.localWhisper.loadModel();
  }

  ngOnDestroy() {
    if (this.isVoiceActive) {
      this.localWhisper.stopDictation();
    }
  }

  async checkApiStatus() {
    await this.geminiConn.checkConnection();
  }

  async sendTextMessage() {
    const text = this.inputText.trim();
    if (!text) return;
    this.inputText = '';

    try {
      this.log.info('GeminiChat', `📤 [MENSAJE ENVIADO] Mensaje enviado a Gemini: "${text}"`);
      const reply = await this.geminiConn.sendMessage(text);
      this.log.info('GeminiChat', `📥 [GEMINI RECIBIDO] Gemini ha recibido y respondido: "${reply}"`);
      this.speechSynth.speak(reply);
    } catch (err: any) {
      alert(`Error: ${err.message}`);
    }
  }

  async startVoiceChat() {
    if (this.isVoiceActive) return;
    const deviceId = this.audioDevices.selectedDeviceId();
    
    this.log.info('GeminiVoice', '🎤 [MICRÓFONO] Micrófono activado y grabando...');
    this.isVoiceActive = true;
    this.localWhisper.clearTranscript();

    try {
      await this.localWhisper.startDictation(deviceId, 'spanish');
    } catch (err: any) {
      this.isVoiceActive = false;
      this.log.error('GeminiVoice', `Error al activar micrófono: ${err.message}`, 'MIC_ERR');
      alert('No se pudo activar el micrófono.');
    }
  }

  async stopAndSendVoiceChat() {
    if (!this.isVoiceActive) return;

    this.log.info('GeminiVoice', '⚙️ [TRANSCRIPCIÓN] Procesando audio con Whisper local...');
    this.isVoiceActive = false;
    this.localWhisper.stopDictation();

    setTimeout(async () => {
      const transcript = this.localWhisper.finalText().trim();
      if (transcript) {
        this.log.info('GeminiChat', `📤 [MENSAJE ENVIADO] Mensaje enviado a Gemini: "${transcript}"`);
        try {
          const reply = await this.geminiConn.sendMessage(transcript);
          this.log.info('GeminiChat', `📥 [GEMINI RECIBIDO] Gemini ha recibido y respondido: "${reply}"`);
          this.speechSynth.speak(reply);
        } catch (err: any) {
          alert(`Error en chat con Gemini: ${err.message}`);
        }
      } else {
        this.log.warn('GeminiVoice', 'No se detectó voz o transcripción válida en la grabación.');
        alert('No se detectó voz clara. Inténtalo de nuevo.');
      }
    }, 800);
  }
}

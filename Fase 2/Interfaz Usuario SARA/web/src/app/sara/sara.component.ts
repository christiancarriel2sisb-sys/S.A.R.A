import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaceComponent } from '../face/face.component';
import { GeminiConnectionService } from '../gemini/gemini-connection.service';
import { LocalWhisperService } from '../dictation/local-whisper.service';
import { SpeechSynthesisAdapter } from '../core/adapters/speech-synthesis.adapter';
import { AudioDevicesService } from '../dictation/audio-devices.service';
import { LogService } from '../core/log/log.service';
import { LogPanelComponent } from '../core/log/log-panel.component';

@Component({
  selector: 'app-sara',
  standalone: true,
  imports: [CommonModule, FormsModule, FaceComponent, LogPanelComponent],
  template: `
    <div class="sara-view" (click)="wakeUp()">
      <!-- Animated SVG Face -->
      <app-face [state]="currentState()"></app-face>

      <!-- Chat Bar -->
      <div class="chat-bar" (click)="$event.stopPropagation()">
        <input type="text"
               [(ngModel)]="textInput"
               (keyup.enter)="sendText()"
               [placeholder]="isListeningMic ? 'Escuchando tu voz (Whisper)...' : 'Escribe o usa el micrófono...'" />
        
        <button class="btn-action"
                [class.listening]="isListeningMic"
                (click)="toggleMic()">
          @if (textInput.trim().length > 0) {
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
          } @else {
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"></path><path d="M19 10v1a7 7 0 0 1-14 0v-1"></path><line x1="12" y1="19" x2="12" y2="23"></line><line x1="8" y1="23" x2="16" y2="23"></line></svg>
          }
        </button>
      </div>

      @if (currentState() === 'IDLE' || currentState() === 'ERROR') {
        <div class="overlay">
          <div class="message-box">
            <p class="main-msg">Toca la pantalla, usa el micrófono o escribe para despertar a Sara</p>
          </div>
        </div>
      }

      <div class="log-wrapper" (click)="$event.stopPropagation()">
        <app-log-panel></app-log-panel>
      </div>
    </div>
  `,
  styles: [`
    .sara-view { position: relative; width: 100%; height: calc(100vh - 65px); background: #000; overflow: hidden; display: flex; flex-direction: column; align-items: center; justify-content: center; }
    .overlay { position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; pointer-events: none; z-index: 10; }
    .message-box { background: rgba(5, 5, 10, 0.85); border: 1px solid #14142A; padding: 20px 32px; border-radius: 16px; text-align: center; }
    .main-msg { color: #8A7DFF; font-family: sans-serif; font-size: 1.1rem; margin: 0; }
    .chat-bar { position: fixed; bottom: 30px; left: 50%; transform: translateX(-50%); display: flex; gap: 8px; background: rgba(13, 13, 26, 0.9); border: 1px solid #6B5BFF; padding: 6px 6px 6px 16px; border-radius: 30px; z-index: 1000; width: 90%; max-width: 500px; align-items: center; box-shadow: 0 4px 20px rgba(107,91,255,0.3); }
    .chat-bar input { flex: 1; background: transparent; border: none; color: #fff; font-size: 0.95rem; outline: none; }
    .btn-action { background: #6B5BFF; color: #fff; border: none; width: 42px; height: 42px; border-radius: 50%; display: flex; align-items: center; justify-content: center; cursor: pointer; }
    .btn-action.listening { background: #ff4757; animation: pulse 1.5s infinite; }
    .log-wrapper { position: fixed; bottom: 90px; right: 20px; width: 400px; z-index: 999; max-height: 300px; }
    @keyframes pulse { 0% { box-shadow: 0 0 0 0 rgba(255, 71, 87, 0.6); } 70% { box-shadow: 0 0 0 10px rgba(255, 71, 87, 0); } 100% { box-shadow: 0 0 0 0 rgba(255, 71, 87, 0); } }
  `]
})
export class SaraComponent implements OnInit, OnDestroy {
  geminiConn = inject(GeminiConnectionService);
  localWhisper = inject(LocalWhisperService);
  speechSynth = inject(SpeechSynthesisAdapter);
  audioDevices = inject(AudioDevicesService);
  log = inject(LogService);

  currentState = signal<string>('IDLE');
  textInput = '';
  isListeningMic = false;

  async ngOnInit() {
    await this.audioDevices.initDevices();
    await this.geminiConn.checkConnection();
    this.localWhisper.loadModel();
  }

  ngOnDestroy() {
    if (this.isListeningMic) {
      this.localWhisper.stopDictation();
    }
  }

  wakeUp() {
    if (this.currentState() === 'IDLE' || this.currentState() === 'ERROR') {
      this.currentState.set('LISTENING');
      this.log.info('SaraFace', 'Sara despertada. Lista para escuchar.');
    }
  }

  toggleMic() {
    if (this.isListeningMic) {
      this.stopMicAndSend();
    } else {
      this.startMic();
    }
  }

  async startMic() {
    if (this.currentState() === 'IDLE' || this.currentState() === 'ERROR') {
      this.currentState.set('LISTENING');
    }
    this.isListeningMic = true;
    this.textInput = '';
    this.localWhisper.clearTranscript();
    this.log.info('SaraFace', '🎤 [MICRÓFONO] Micrófono activado con dictado local Whisper...');

    const deviceId = this.audioDevices.selectedDeviceId();
    try {
      await this.localWhisper.startDictation(deviceId, 'spanish');
    } catch (e: any) {
      this.isListeningMic = false;
      this.currentState.set('ERROR');
      this.log.error('SaraFace', `Error al iniciar dictado local: ${e.message}`);
    }
  }

  async stopMicAndSend() {
    this.isListeningMic = false;
    this.localWhisper.stopDictation();
    this.log.info('SaraFace', '⚙️ [TRANSCRIPCIÓN] Procesando audio con Whisper local...');

    setTimeout(async () => {
      const transcript = this.localWhisper.finalText().trim();
      if (transcript) {
        this.textInput = transcript;
        this.sendText(transcript);
      } else {
        this.log.warn('SaraFace', 'No se detectó voz en la grabación.');
        this.currentState.set('LISTENING');
      }
    }, 800);
  }

  async sendText(textOverride?: string) {
    const text = textOverride || this.textInput.trim();
    if (!text) return;
    this.textInput = '';

    if (this.isListeningMic) {
      this.isListeningMic = false;
      this.localWhisper.stopDictation();
    }

    this.currentState.set('THINKING');
    this.log.info('SaraFace', `📤 [MENSAJE ENVIADO] Enviando a Gemini: "${text}"`);

    try {
      const reply = await this.geminiConn.sendMessage(text);
      this.log.info('SaraFace', `📥 [GEMINI RECIBIDO] Respuesta de Sara: "${reply}"`);
      this.currentState.set('SPEAKING');

      this.speechSynth.speak(reply, () => {
        this.currentState.set('LISTENING');
        this.log.info('SaraFace', 'Voz finalizada. Sara vuelve a escuchar.');
      });
    } catch (err: any) {
      this.currentState.set('ERROR');
      this.log.error('SaraFace', `Error en chat con Gemini: ${err.message}`);
      alert(`Error: ${err.message}`);
    }
  }
}

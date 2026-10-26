import { Injectable, NgZone, inject, signal } from '@angular/core';
import { LogService } from '../core/log/log.service';

@Injectable({
  providedIn: 'root'
})
export class DictationService {
  private log = inject(LogService);
  private ngZone = inject(NgZone);

  private recognition: any = null;
  private isRunning = false;
  private shouldBeRunning = false;
  private currentLanguage = 'es-ES';
  private restartTimeout: any = null;
  private watchdogTimeout: any = null;
  private lastStartTime = 0;
  private restartAttempts = 0;
  private maxRestartAttempts = 5;

  private finalTextSignal = signal<string>('');
  readonly finalText = this.finalTextSignal.asReadonly();

  private interimTextSignal = signal<string>('');
  readonly interimText = this.interimTextSignal.asReadonly();

  private isListeningSignal = signal<boolean>(false);
  readonly isListening = this.isListeningSignal.asReadonly();

  startDictation(language: string = 'es-ES') {
    if (typeof window === 'undefined') return;
    this.currentLanguage = language;
    this.restartAttempts = 0;
    this.log.info('Dictation', `Iniciando dictado con idioma: ${language}`);
    this.log.setPipelineStepStatus('speech', 'active');
    this.shouldBeRunning = true;

    this.initRecognitionInstance();
  }

  private initRecognitionInstance() {
    const SpeechRecognitionAPI = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognitionAPI) {
      const err = 'La API de Reconocimiento de Voz no está soportada en este navegador (use Google Chrome o Microsoft Edge).';
      this.log.error('Dictation', err, 'NO_SPEECH_API');
      this.log.setPipelineStepStatus('speech', 'error');
      alert(err);
      return;
    }

    try {
      if (this.recognition) {
        try { this.recognition.abort(); } catch (e) {}
      }

      this.log.info('DictationAPI', `Instanciando webkitSpeechRecognition para idioma: ${this.currentLanguage}`);
      this.recognition = new SpeechRecognitionAPI();
      this.recognition.lang = this.currentLanguage;
      this.recognition.continuous = true;
      this.recognition.interimResults = true;

      this.recognition.onaudiostart = () => {
        this.log.info('DictationAPI', '[RECIBIDO] Evento onaudiostart: El navegador comenzó a capturar audio para la API de voz.');
      };

      this.recognition.onsoundstart = () => {
        this.log.info('DictationAPI', '[RECIBIDO] Evento onsoundstart: Sonido detectado por el motor.');
      };

      this.recognition.onspeechstart = () => {
        this.log.info('DictationAPI', '[RECIBIDO] Evento onspeechstart: Voz humana detectada.');
      };

      this.recognition.onnomatch = () => {
        this.log.warn('DictationAPI', '[RECIBIDO] Evento onnomatch: El motor escuchó audio pero no pudo transcribirlo.');
      };

      this.recognition.onstart = () => {
        this.isRunning = true;
        this.lastStartTime = Date.now();
        if (this.watchdogTimeout) clearTimeout(this.watchdogTimeout);
        this.ngZone.run(() => {
          this.isListeningSignal.set(true);
          this.log.info('DictationAPI', '[RECIBIDO] Evento onstart: ¡Reconocimiento activo! (Paso 4 OK)');
          this.log.setPipelineStepStatus('speech', 'ok');
          this.log.setPipelineStepStatus('ready', 'ok');
        });
      };

      this.recognition.onresult = (event: any) => {
        this.log.info('DictationAPI', '[RECIBIDO] Evento onresult: Transcripción recibida desde la API de voz.');
        this.ngZone.run(() => {
          let interim = '';
          let finalChunk = '';

          for (let i = event.resultIndex; i < event.results.length; ++i) {
            const transcript = event.results[i][0].transcript;
            if (event.results[i].isFinal) {
              finalChunk += transcript + ' ';
            } else {
              interim += transcript;
            }
          }

          if (finalChunk) {
            this.finalTextSignal.update(current => current ? `${current} ${finalChunk}` : finalChunk);
            this.log.debug('Dictation', `Texto definitivo agregado: "${finalChunk.trim()}"`);
          }

          this.interimTextSignal.set(interim);
        });
      };

      this.recognition.onerror = (event: any) => {
        this.log.error('DictationAPI', `[RECIBIDO] Error reportado por API de voz: ${event.error}`, 'SPEECH_ERR');
        if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
          this.shouldBeRunning = false;
          this.stopDictation();
        }
      };

      this.recognition.onend = () => {
        this.ngZone.run(() => {
          this.isRunning = false;
          this.log.info('DictationAPI', '[RECIBIDO] Evento onend: La sesión de reconocimiento finalizó.');

          const duration = Date.now() - this.lastStartTime;

          if (this.shouldBeRunning) {
            if (duration < 1200) {
              this.restartAttempts++;
              if (this.restartAttempts >= this.maxRestartAttempts) {
                this.log.error('Dictation', 'Demasiados intentos fallidos consecutivos (< 1.2s). Deteniendo para evitar bucles.', 'LOOP_PREVENTED');
                this.shouldBeRunning = false;
                this.isListeningSignal.set(false);
                this.log.setPipelineStepStatus('speech', 'error');
                this.log.setPipelineStepStatus('ready', 'error');
                return;
              }
            } else {
              this.restartAttempts = 0;
            }

            this.log.info('Dictation', 'Reiniciando automáticamente el reconocimiento tras cierre...');
            if (this.restartTimeout) clearTimeout(this.restartTimeout);
            this.restartTimeout = setTimeout(() => {
              if (this.shouldBeRunning) {
                this.initRecognitionInstance();
              }
            }, 600);
          } else {
            this.isListeningSignal.set(false);
            this.log.setPipelineStepStatus('ready', 'pending');
          }
        });
      };

      try {
        this.log.info('DictationAPI', '[ENVIADO] Llamada a recognition.start() enviada a la API del navegador.');
        this.recognition.start();

        // Watchdog: if onstart does not fire within 4 seconds, log a diagnostic warning
        if (this.watchdogTimeout) clearTimeout(this.watchdogTimeout);
        this.watchdogTimeout = setTimeout(() => {
          if (!this.isRunning && this.shouldBeRunning) {
            this.log.warn('DictationAPI', '[ALERTA] La API de voz del navegador no ha respondido con onstart tras 4s. Posible bloqueo de red (servidores Google Cloud Speech), modo offline o permisos de red/firewall.');
          }
        }, 4000);

      } catch (startErr: any) {
        this.log.error('Dictation', `Excepción al llamar a recognition.start(): ${startErr.message}`, 'START_EXCEPTION');
        this.log.setPipelineStepStatus('speech', 'error');
        this.log.setPipelineStepStatus('ready', 'error');
      }

    } catch (err: any) {
      this.log.error('Dictation', `Error crítico al instanciar reconocimiento: ${err.message}`, 'DICTATION_CRITICAL_ERR');
      this.log.setPipelineStepStatus('speech', 'error');
      this.log.setPipelineStepStatus('ready', 'error');
    }
  }

  stopDictation() {
    this.log.info('Dictation', 'Deteniendo dictado a petición del usuario...');
    this.shouldBeRunning = false;
    if (this.restartTimeout) {
      clearTimeout(this.restartTimeout);
      this.restartTimeout = null;
    }
    if (this.watchdogTimeout) {
      clearTimeout(this.watchdogTimeout);
      this.watchdogTimeout = null;
    }
    if (this.recognition) {
      try {
        this.recognition.stop();
      } catch (e) {}
    }
    this.isListeningSignal.set(false);
    this.interimTextSignal.set('');
    this.log.setPipelineStepStatus('speech', 'pending');
    this.log.setPipelineStepStatus('ready', 'pending');
  }

  clearTranscript() {
    this.finalTextSignal.set('');
    this.interimTextSignal.set('');
    this.log.info('Dictation', 'Transcripción limpiada.');
  }

  setLanguage(language: string) {
    this.currentLanguage = language;
    if (this.isRunning && this.recognition) {
      this.log.info('Dictation', `Cambiando idioma en caliente a: ${language}`);
      this.stopDictation();
      setTimeout(() => this.startDictation(language), 300);
    }
  }
}

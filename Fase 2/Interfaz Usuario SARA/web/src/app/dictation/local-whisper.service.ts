import { Injectable, NgZone, inject, signal } from '@angular/core';
import { LogService } from '../core/log/log.service';

function isMusicOrArtifact(text: string): boolean {
  const lower = text.toLowerCase().trim();
  if (!lower) return true;
  const artifactPatterns = [
    /\[música\]/i,
    /\(música\)/i,
    /\[music\]/i,
    /\(music\)/i,
    /♪/,
    /♫/,
    /subtítulos por/i,
    /amara\.org/i,
    /^[\.,\-\s]+$/
  ];
  return artifactPatterns.some(pattern => pattern.test(text));
}

@Injectable({
  providedIn: 'root'
})
export class LocalWhisperService {
  private log = inject(LogService);
  private ngZone = inject(NgZone);

  private transcriber: any = null;
  private isModelLoading = signal<boolean>(false);
  private modelLoadProgress = signal<string>('Modelo no cargado');
  readonly isLoading = this.isModelLoading.asReadonly();
  readonly progress = this.modelLoadProgress.asReadonly();

  private mediaRecorder: MediaRecorder | null = null;
  private audioChunks: Blob[] = [];
  private mediaStream: MediaStream | null = null;
  private isRecording = false;

  private finalTextSignal = signal<string>('');
  readonly finalText = this.finalTextSignal.asReadonly();

  private interimTextSignal = signal<string>('');
  readonly interimText = this.interimTextSignal.asReadonly();

  private isListeningSignal = signal<boolean>(false);
  readonly isListening = this.isListeningSignal.asReadonly();

  async loadModel() {
    if (this.transcriber || this.isModelLoading()) return;
    this.isModelLoading.set(true);
    this.log.info('LocalWhisper', 'Cargando modelo local OpenAI Whisper (Xenova/whisper-tiny) vía CDN en el navegador...');
    this.log.setPipelineStepStatus('speech', 'active');

    try {
      const transformers: any = await (eval as any)('import("https://cdn.jsdelivr.net/npm/@huggingface/transformers@3.2.4")');
      const { pipeline, env } = transformers;
      env.allowLocalModels = false;

      this.transcriber = await pipeline('automatic-speech-recognition', 'Xenova/whisper-tiny', {
        progress_callback: (data: any) => {
          if (data.status === 'progress') {
            const pct = Math.round(data.progress || 0);
            this.modelLoadProgress.set(`Descargando modelo: ${pct}% (${data.file || ''})`);
            this.log.debug('LocalWhisper', `Progreso de descarga del modelo: ${pct}% - ${data.file || ''}`);
          }
        }
      });

      this.modelLoadProgress.set('Modelo listo');
      this.isModelLoading.set(false);
      this.log.info('LocalWhisper', '¡Modelo OpenAI Whisper cargado exitosamente en el navegador!');
      this.log.setPipelineStepStatus('speech', 'ok');
    } catch (err: any) {
      this.isModelLoading.set(false);
      this.modelLoadProgress.set('Error al cargar modelo');
      this.log.error('LocalWhisper', `Error al cargar modelo Whisper: ${err.message}`, 'WHISPER_LOAD_ERR');
      this.log.setPipelineStepStatus('speech', 'error');
    }
  }

  async startDictation(deviceId?: string, language: string = 'spanish') {
    if (typeof window === 'undefined') return;
    this.log.info('LocalWhisper', 'Iniciando dictado local con Whisper (audio limpio y filtro de música)...');
    
    if (!this.transcriber) {
      await this.loadModel();
      if (!this.transcriber) return;
    }

    try {
      const constraints: MediaStreamConstraints = {
        audio: deviceId ? { deviceId: { exact: deviceId } } : true
      };

      try {
        this.mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
      } catch (err) {
        this.log.warn('LocalWhisper', 'Fallback a micrófono predeterminado.');
        this.mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      }

      this.mediaRecorder = new MediaRecorder(this.mediaStream, { mimeType: 'audio/webm' });
      this.audioChunks = [];
      this.isRecording = true;

      this.mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          this.audioChunks.push(event.data);
        }
      };

      this.mediaRecorder.onstop = async () => {
        if (this.audioChunks.length === 0 || !this.isRecording) return;
        const audioBlob = new Blob(this.audioChunks, { type: 'audio/webm' });
        this.audioChunks = [];
        await this.processAudioBlob(audioBlob, language);

        if (this.isRecording && this.mediaRecorder) {
          try {
            this.mediaRecorder.start();
            setTimeout(() => {
              if (this.mediaRecorder && this.isRecording && this.mediaRecorder.state === 'recording') {
                this.mediaRecorder.stop();
              }
            }, 4000);
          } catch (e) {}
        }
      };

      this.mediaRecorder.start();
      this.isListeningSignal.set(true);
      this.log.setPipelineStepStatus('ready', 'ok');
      this.log.info('LocalWhisper', 'Grabación local iniciada con filtro anti-música. Habla ahora...');

      setTimeout(() => {
        if (this.mediaRecorder && this.isRecording && this.mediaRecorder.state === 'recording') {
          this.mediaRecorder.stop();
        }
      }, 4000);

    } catch (err: any) {
      this.log.error('LocalWhisper', `Error al iniciar grabación local: ${err.message}`, 'RECORD_ERR');
      this.log.setPipelineStepStatus('speech', 'error');
    }
  }

  private async processAudioBlob(blob: Blob, language: string) {
    if (!this.transcriber) return;
    try {
      this.log.debug('LocalWhisper', 'Procesando chunk de audio para transcripción local...');
      const arrayBuffer = await blob.arrayBuffer();
      const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 16000 });
      const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer);
      const audioData = audioBuffer.getChannelData(0);

      const result = await this.transcriber(audioData, {
        language: language,
        task: 'transcribe'
      });

      if (result && result.text) {
        const text = result.text.trim();
        if (text && !isMusicOrArtifact(text)) {
          this.ngZone.run(() => {
            this.finalTextSignal.update(current => current ? `${current} ${text}` : text);
            this.log.info('LocalWhisper', `Transcrito localmente: "${text}"`);
          });
        } else {
          this.log.debug('LocalWhisper', `Audio detectado como música/ruido ignorado: "${text}"`);
        }
      }
    } catch (err: any) {
      this.log.error('LocalWhisper', `Error en transcripción Whisper: ${err.message}`, 'INFERENCE_ERR');
    }
  }

  stopDictation() {
    this.log.info('LocalWhisper', 'Deteniendo dictado local...');
    this.isRecording = false;
    if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
      try {
        this.mediaRecorder.stop();
      } catch (e) {}
    }
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(t => t.stop());
      this.mediaStream = null;
    }
    this.isListeningSignal.set(false);
    this.interimTextSignal.set('');
    this.log.setPipelineStepStatus('ready', 'pending');
  }

  clearTranscript() {
    this.finalTextSignal.set('');
    this.interimTextSignal.set('');
    this.log.info('LocalWhisper', 'Transcripción limpiada.');
  }

  setLanguage(language: string) {
    this.log.info('LocalWhisper', `Idioma local cambiado a: ${language}`);
  }
}

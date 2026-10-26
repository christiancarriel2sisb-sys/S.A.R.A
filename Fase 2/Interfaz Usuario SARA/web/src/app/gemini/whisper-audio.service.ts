import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LogService } from '../core/log/log.service';

@Injectable({
  providedIn: 'root'
})
export class WhisperAudioService {
  private http = inject(HttpClient);
  private log = inject(LogService);

  private isRecordingSignal = signal<boolean>(false);
  readonly isRecording = this.isRecordingSignal.asReadonly();

  private mediaRecorder: MediaRecorder | null = null;
  private audioChunks: Blob[] = [];
  private mediaStream: MediaStream | null = null;

  async startRecording(deviceId?: string): Promise<void> {
    this.log.info('WhisperAudio', 'Iniciando grabación de audio para Whisper STT...');
    try {
      const constraints: MediaStreamConstraints = {
        audio: deviceId ? { deviceId: { exact: deviceId } } : true
      };
      this.mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
      this.mediaRecorder = new MediaRecorder(this.mediaStream, { mimeType: 'audio/webm' });
      this.audioChunks = [];

      this.mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          this.audioChunks.push(e.data);
        }
      };

      this.mediaRecorder.start();
      this.isRecordingSignal.set(true);
      this.log.info('WhisperAudio', 'Grabación iniciada.');
    } catch (err: any) {
      this.log.error('WhisperAudio', `Error al iniciar grabación: ${err.message}`, 'REC_START_ERR');
      throw err;
    }
  }

  async stopAndTranscribe(): Promise<string> {
    this.log.info('WhisperAudio', 'Deteniendo grabación y enviando a /api/whisper-stt...');
    return new Promise((resolve, reject) => {
      if (!this.mediaRecorder) {
        reject(new Error('No hay grabadora activa'));
        return;
      }

      this.mediaRecorder.onstop = async () => {
        try {
          const audioBlob = new Blob(this.audioChunks, { type: 'audio/webm' });
          const formData = new FormData();
          formData.append('audio', audioBlob, 'audio.webm');

          const res: any = await firstValueFrom(this.http.post('/api/whisper-stt', formData));
          if (res && res.success && res.text) {
            this.log.info('WhisperAudio', `Transcripción obtenida: "${res.text}"`);
            resolve(res.text);
          } else {
            reject(new Error(res?.error || 'Error en transcripción Whisper STT'));
          }
        } catch (err: any) {
          const msg = err.error?.error || err.message || 'Error de red en STT';
          this.log.error('WhisperAudio', `Error al enviar audio: ${msg}`);
          reject(err);
        } finally {
          this.cleanup();
        }
      };

      this.mediaRecorder.stop();
      this.isRecordingSignal.set(false);
    });
  }

  private cleanup() {
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(t => t.stop());
      this.mediaStream = null;
    }
    this.mediaRecorder = null;
    this.audioChunks = [];
    this.isRecordingSignal.set(false);
  }
}

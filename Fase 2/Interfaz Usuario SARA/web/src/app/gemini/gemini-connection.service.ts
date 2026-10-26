import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LogService } from '../core/log/log.service';

export interface ChatMessage {
  role: 'user' | 'model';
  text: string;
}

@Injectable({
  providedIn: 'root'
})
export class GeminiConnectionService {
  private http = inject(HttpClient);
  private log = inject(LogService);

  private isConnectedSignal = signal<boolean>(false);
  readonly isConnected = this.isConnectedSignal.asReadonly();

  private historySignal = signal<ChatMessage[]>([]);
  readonly history = this.historySignal.asReadonly();

  async checkConnection(): Promise<{ success: boolean; latency?: number; error?: string }> {
    this.log.info('GeminiConn', '🔍 [VERIFICACIÓN] Iniciando comprobación de conexión con la API de Gemini...');
    const startTime = Date.now();
    try {
      const res: any = await firstValueFrom(this.http.post('/api/gemini/check', {}));
      const latency = Date.now() - startTime;
      if (res && res.success) {
        this.isConnectedSignal.set(true);
        this.log.info('GeminiConn', `✅ [VERIFICACIÓN EXITOSA] Conexión operativa (${res.latency || latency}ms).`);
        return { success: true, latency: res.latency || latency };
      } else {
        this.isConnectedSignal.set(false);
        this.log.error('GeminiConn', `❌ [VERIFICACIÓN RECHAZADA]: ${res?.error || 'Desconocido'}`, 'API_FAIL');
        return { success: false, error: res?.error || 'Error desconocido' };
      }
    } catch (err: any) {
      this.isConnectedSignal.set(false);
      const msg = err.error?.error || err.message || 'Error de red';
      this.log.error('GeminiConn', `❌ [VERIFICACIÓN FALLIDA]: ${msg}`, 'API_OFFLINE');
      return { success: false, error: msg };
    }
  }

  async sendMessage(text: string): Promise<string> {
    const userMessage: ChatMessage = { role: 'user', text };
    const currentHistory = this.history();

    this.historySignal.update(h => [...h, userMessage]);
    this.log.info('GeminiConn', `📤 [CHAT] Enviando mensaje: "${text}"`);

    try {
      const body = {
        history: currentHistory,
        message: text
      };

      const res: any = await firstValueFrom(this.http.post('/api/chat', body));
      if (res && res.success && res.reply) {
        const replyText = res.reply;
        const modelMessage: ChatMessage = { role: 'model', text: replyText };
        this.historySignal.update(h => [...h, modelMessage]);
        this.log.info('GeminiConn', `📥 [CHAT] Respuesta recibida de Sara: "${replyText}"`);
        return replyText;
      } else {
        throw new Error(res?.error || 'Respuesta inválida del servidor');
      }
    } catch (err: any) {
      const msg = err.error?.error || err.message || 'Error de comunicación con Gemini';
      this.log.error('GeminiConn', `❌ [CHAT ERROR] ${msg}`, 'CHAT_ERR');
      throw err;
    }
  }

  clearHistory() {
    this.historySignal.set([]);
    this.log.info('GeminiConn', '🧹 Historial de chat limpiado.');
  }
}

import { Injectable, signal } from '@angular/core';
import { maskSecrets } from './mask-secrets';

export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

export interface LogEntry {
  id: string;
  timestamp: string;
  level: LogLevel;
  module: string;
  message: string;
  code?: string;
}

export interface PipelineStep {
  id: string;
  label: string;
  status: 'pending' | 'active' | 'ok' | 'error';
}

@Injectable({
  providedIn: 'root'
})
export class LogService {
  private maxBufferSize = 200;
  private logsSignal = signal<LogEntry[]>([]);
  readonly logs = this.logsSignal.asReadonly();

  private pipelineStepsSignal = signal<PipelineStep[]>([
    { id: 'mic', label: '1. Micrófono', status: 'pending' },
    { id: 'audio', label: '2. Vúmetro RMS', status: 'pending' },
    { id: 'speech', label: '3. Reconocimiento', status: 'pending' },
    { id: 'ready', label: '4. Dictado Activo', status: 'pending' }
  ]);
  readonly pipelineSteps = this.pipelineStepsSignal.asReadonly();

  log(level: LogLevel, module: string, message: string, code?: string) {
    const maskedMessage = maskSecrets(message);
    const entry: LogEntry = {
      id: Math.random().toString(36).substring(2, 9),
      timestamp: new Date().toLocaleTimeString(),
      level,
      module,
      message: maskedMessage,
      code
    };

    this.logsSignal.update(current => {
      const updated = [entry, ...current];
      if (updated.length > this.maxBufferSize) {
        updated.pop();
      }
      return updated;
    });

    const consoleMsg = `[${entry.timestamp}] [${level}] [${module}] ${maskedMessage}`;
    if (level === 'ERROR') console.error(consoleMsg);
    else if (level === 'WARN') console.warn(consoleMsg);
    else if (level === 'DEBUG') console.debug(consoleMsg);
    else console.log(consoleMsg);
  }

  debug(module: string, message: string, code?: string) {
    this.log('DEBUG', module, message, code);
  }

  info(module: string, message: string, code?: string) {
    this.log('INFO', module, message, code);
  }

  warn(module: string, message: string, code?: string) {
    this.log('WARN', module, message, code);
  }

  error(module: string, message: string, code?: string) {
    this.log('ERROR', module, message, code);
  }

  clearLogs() {
    this.logsSignal.set([]);
  }

  setPipelineStepStatus(stepId: string, status: 'pending' | 'active' | 'ok' | 'error') {
    this.pipelineStepsSignal.update(steps =>
      steps.map(s => s.id === stepId ? { ...s, status } : s)
    );
  }
}

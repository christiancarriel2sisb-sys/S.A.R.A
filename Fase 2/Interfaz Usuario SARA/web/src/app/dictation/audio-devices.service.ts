import { Injectable, signal, inject } from '@angular/core';
import { LogService } from '../core/log/log.service';

export interface AudioDevice {
  deviceId: string;
  label: string;
}

@Injectable({
  providedIn: 'root'
})
export class AudioDevicesService {
  private log = inject(LogService);

  private devicesSignal = signal<AudioDevice[]>([]);
  readonly devices = this.devicesSignal.asReadonly();

  private selectedDeviceIdSignal = signal<string>('');
  readonly selectedDeviceId = this.selectedDeviceIdSignal.asReadonly();

  private storageKey = 'sara_selected_audio_device';

  async initDevices() {
    if (typeof window === 'undefined' || !navigator.mediaDevices) return;
    this.log.info('AudioDevices', 'Solicitando permisos de micrófono (audio limpio)...');
    this.log.setPipelineStepStatus('mic', 'active');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach(track => track.stop());

      await this.refreshDevices();
      this.log.setPipelineStepStatus('mic', 'ok');
    } catch (err: any) {
      this.log.error('AudioDevices', `Error al obtener permisos de micrófono: ${err.message}`, 'MIC_PERM_DENIED');
      this.log.setPipelineStepStatus('mic', 'error');
      throw err;
    }
  }

  async refreshDevices() {
    if (typeof window === 'undefined' || !navigator.mediaDevices) return;
    try {
      const allDevices = await navigator.mediaDevices.enumerateDevices();
      const audioInputs = allDevices
        .filter(d => d.kind === 'audioinput')
        .map(d => ({
          deviceId: d.deviceId,
          label: d.label || `Micrófono (${d.deviceId.slice(0, 5)}...)`
        }));

      this.devicesSignal.set(audioInputs);
      this.log.info('AudioDevices', `Se encontraron ${audioInputs.length} dispositivos de entrada de audio.`);

      const savedId = localStorage.getItem(this.storageKey);
      const exists = audioInputs.some(d => d.deviceId === savedId);

      if (savedId && exists) {
        this.selectedDeviceIdSignal.set(savedId);
        this.log.info('AudioDevices', `Dispositivo seleccionado restaurado desde localStorage: ${savedId}`);
      } else if (audioInputs.length > 0) {
        this.selectedDeviceIdSignal.set(audioInputs[0].deviceId);
        localStorage.setItem(this.storageKey, audioInputs[0].deviceId);
        this.log.info('AudioDevices', `Dispositivo predeterminado asignado: ${audioInputs[0].label}`);
      }
    } catch (err: any) {
      this.log.error('AudioDevices', `Error al enumerar dispositivos: ${err.message}`, 'ENUM_DEVICES_ERR');
    }
  }

  selectDevice(deviceId: string) {
    this.selectedDeviceIdSignal.set(deviceId);
    localStorage.setItem(this.storageKey, deviceId);
    this.log.info('AudioDevices', `Dispositivo cambiado a: ${deviceId}`);
  }
}

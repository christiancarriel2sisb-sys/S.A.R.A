import { Injectable } from '@angular/core';
import { getSpanishFemaleVoice } from '../voice-picker';
import { stripMarkdown } from '../text-utils';

@Injectable({
  providedIn: 'root'
})
export class SpeechSynthesisAdapter {
  private synth: SpeechSynthesis | null = null;

  constructor() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      this.synth = window.speechSynthesis;
    }
  }

  speak(text: string, onEnd?: () => void) {
    if (!this.synth) {
      if (onEnd) onEnd();
      return;
    }

    this.stop();

    const cleanText = stripMarkdown(text);
    if (!cleanText) {
      if (onEnd) onEnd();
      return;
    }

    const utterance = new SpeechSynthesisUtterance(cleanText);
    utterance.lang = 'es-ES';
    utterance.rate = 1.0;
    utterance.pitch = 1.0;

    const voices = this.synth.getVoices();
    const voice = getSpanishFemaleVoice(voices);
    if (voice) {
      utterance.voice = voice;
    }

    if (onEnd) {
      utterance.onend = () => onEnd();
      utterance.onerror = () => onEnd();
    }

    this.synth.speak(utterance);
  }

  stop() {
    if (this.synth && this.synth.speaking) {
      this.synth.cancel();
    }
  }
}

export function getSpanishFemaleVoice(voices: SpeechSynthesisVoice[]): SpeechSynthesisVoice | null {
  if (!voices || voices.length === 0) return null;

  const spanishVoices = voices.filter(v => v.lang.startsWith('es') || v.lang.startsWith('ES'));

  const femaleKeywords = ['female', 'zira', 'helena', 'laura', 'sofia', 'paula', 'mónica', 'monica', 'carmen', 'elena', 'google', 'natural'];

  for (const keyword of femaleKeywords) {
    const found = spanishVoices.find(v => v.name.toLowerCase().includes(keyword));
    if (found) return found;
  }

  if (spanishVoices.length > 0) {
    return spanishVoices[0];
  }

  return voices[0] || null;
}

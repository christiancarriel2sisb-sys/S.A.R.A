export function assembleTranscript(finalText: string, interimText: string): string {
  const trimmedFinal = finalText.trim();
  const trimmedInterim = interimText.trim();

  if (!trimmedFinal) {
    return trimmedInterim;
  }
  if (!trimmedInterim) {
    return trimmedFinal;
  }
  return `${trimmedFinal} ${trimmedInterim}`;
}

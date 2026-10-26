export function stripMarkdown(text: string): string {
  if (!text) return '';
  return text
    .replace(/```[\s\S]*?```/g, '') // code blocks
    .replace(/`([^`]+)`/g, '$1')     // inline code
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1') // links
    .replace(/[*_#_~`>-]/g, '')     // markdown symbols
    .replace(/\s+/g, ' ')           // extra spaces
    .trim();
}

export function splitSentences(text: string): string[] {
  if (!text) return [];
  // Split by sentence ending punctuation (. ! ?)
  const cleaned = stripMarkdown(text);
  return cleaned
    .split(/(?<=[.!?])\s+/)
    .map(s => s.trim())
    .filter(s => s.length > 0);
}

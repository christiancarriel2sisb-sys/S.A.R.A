export function maskSecrets(message: string): string {
  if (!message || typeof message !== 'string') {
    return message;
  }
  // Mask common API keys, tokens, and secrets (e.g., AIza..., bearer tokens, api_key=...)
  return message
    .replace(/(AIza[0-9A-Za-z-_]{35})/g, 'AIza***********************************')
    .replace(/(bearer\s+[a-zA-Z0-9_\-\.]+)/gi, 'bearer [MASKED]')
    .replace(/(api[_-]?key['"]?\s*[:=]\s*['"]?[a-zA-Z0-9_\-\.]{10,}['"]?)/gi, 'api_key: [MASKED]');
}

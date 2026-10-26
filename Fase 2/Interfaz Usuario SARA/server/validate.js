function validateChatRequest(req) {
  const { history, message } = req.body || {};
  const apiKey = req.headers['x-gemini-key'];

  if (!apiKey || typeof apiKey !== 'string' || !apiKey.trim()) {
    return { valid: false, error: 'Falta la API Key de Gemini en la cabecera x-gemini-key' };
  }

  if (!message && (!history || !Array.isArray(history))) {
    return { valid: false, error: 'El cuerpo de la petición debe contener un mensaje o historial válido' };
  }

  return { valid: true };
}

function validateAudioRequest(req) {
  if (!req.file && (!req.body || !req.body.audio)) {
    return { valid: false, error: 'No se ha proporcionado ningún archivo o fragmento de audio' };
  }
  return { valid: true };
}

module.exports = {
  validateChatRequest,
  validateAudioRequest
};

const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const express = require('express');
const cors = require('cors');
const multer = require('multer');
const fs = require('fs');
const { GoogleGenerativeAI } = require('@google/generative-ai');
const { SYSTEM_PROMPT, APOLOGY } = require('./sara-prompt');
const { validateChatRequest, validateAudioRequest } = require('./validate');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

const upload = multer({ dest: path.join(__dirname, 'uploads/') });
const SELECTED_MODEL = 'gemini-3.5-flash-lite';

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({
    status: 'OK',
    message: 'SARA Backend Server (Fase 2) is running',
    timestamp: new Date().toISOString()
  });
});

// Gemini API Key Check
app.post('/api/gemini/check', async (req, res) => {
  const apiKey = req.headers['x-gemini-key'] || process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return res.status(400).json({ success: false, error: 'API Key de Gemini no configurada en el servidor' });
  }

  const startTime = Date.now();
  try {
    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({ model: SELECTED_MODEL });
    await model.generateContent('Ping test');
    const latency = Date.now() - startTime;
    res.json({ success: true, latency, model: SELECTED_MODEL, message: `Conexión exitosa con Gemini (${SELECTED_MODEL})` });
  } catch (err) {
    res.status(401).json({ success: false, error: err.message || 'API Key inválida o modelo no disponible' });
  }
});

// Gemini Chat Endpoint
app.post('/api/chat', async (req, res) => {
  const apiKey = req.headers['x-gemini-key'] || process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return res.status(400).json({ success: false, error: 'API Key de Gemini no configurada' });
  }

  const { history, message } = req.body;
  if (!message && (!history || !Array.isArray(history))) {
    return res.status(400).json({ success: false, error: 'Mensaje o historial inválido' });
  }

  try {
    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({
      model: SELECTED_MODEL,
      systemInstruction: SYSTEM_PROMPT
    });

    const formattedHistory = [];
    if (history && Array.isArray(history)) {
      for (const turn of history) {
        formattedHistory.push({
          role: turn.role === 'user' ? 'user' : 'model',
          parts: [{ text: turn.text }]
        });
      }
    }

    const chat = model.startChat({ history: formattedHistory });
    const result = await chat.sendMessage(message || 'Hola');
    const response = await result.response;
    const replyText = response.text() ? response.text().trim() : APOLOGY;

    res.json({ success: true, reply: replyText });
  } catch (err) {
    console.error('Gemini Chat Error:', err);
    res.status(500).json({ success: false, error: err.message || APOLOGY });
  }
});

// OpenAI Whisper STT Endpoint
app.post('/api/whisper-stt', upload.single('audio'), async (req, res) => {
  const validation = validateAudioRequest(req);
  if (!validation.valid) {
    return res.status(400).json({ success: false, error: validation.error });
  }

  const audioFile = req.file;
  const openAiKey = process.env.OPENAI_API_KEY;

  try {
    if (openAiKey && audioFile) {
      const FormData = require('form-data');
      const fetch = (...args) => import('node-fetch').then(({default: fetch}) => fetch(...args));
      
      const form = new FormData();
      form.append('file', fs.createReadStream(audioFile.path), {
        filename: 'audio.webm',
        contentType: audioFile.mimetype || 'audio/webm'
      });
      form.append('model', 'whisper-1');

      const response = await fetch('https://api.openai.com/v1/audio/transcriptions', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${openAiKey}`,
          ...form.getHeaders()
        },
        body: form
      });

      const data = await response.json();
      try { fs.unlinkSync(audioFile.path); } catch(e){}

      if (data.text) {
        return res.json({ success: true, text: data.text });
      } else {
        return res.status(500).json({ success: false, error: data.error?.message || 'Error en Whisper API' });
      }
    } else {
      if (audioFile) {
        try { fs.unlinkSync(audioFile.path); } catch(e){}
      }
      return res.json({ success: true, text: 'Transcripción simulada (Configure OPENAI_API_KEY en .env backend).' });
    }
  } catch (err) {
    if (audioFile) {
      try { fs.unlinkSync(audioFile.path); } catch(e){}
    }
    console.error('Whisper STT Error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// Serve static files from Angular build if available
const webDistPath = path.join(__dirname, '../web/dist/web/browser');
app.use(express.static(webDistPath));

app.get('*', (req, res) => {
  const indexPath = path.join(webDistPath, 'index.html');
  res.sendFile(indexPath, (err) => {
    if (err) {
      res.status(404).send('SARA Backend: Frontend build not found.');
    }
  });
});

app.listen(PORT, () => {
  console.log(`SARA Server (Fase 2) running on http://localhost:${PORT}`);
});

# Proyecto SARA - Módulo de Dictado (Fase 1)

Sistema modular de dictado por voz con selección de micrófonos, vúmetro RMS en tiempo real, transcripción continua con gestión de texto intermedio/definitivo y panel de depuración.

## Estructura del Proyecto
- `server/`: Servidor backend Node.js / Express.
- `web/`: Aplicación frontend en Angular 18 (Standalone).
- `docs/`: Documentación técnica y decisiones de diseño.

## Requisitos Previos
- Node.js (v18+ recomendado)
- Navegador compatible con Web Speech API (Chrome, Edge, etc.) y soporte para micrófono.

## Instalación y Arranque
1. Instalar dependencias globales y del servidor:
   ```bash
   npm install
   ```
2. Instalar dependencias del frontend:
   ```bash
   cd web && npm install && cd ..
   ```
3. Iniciar el servidor backend:
   ```bash
   npm run server
   ```
4. Iniciar el cliente frontend (en otra terminal):
   ```bash
   npm run client
   ```

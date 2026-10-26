# Registro de Decisiones Técnicas (DECISIONES.md)

## 1. Arquitectura Standalone en Angular 18
- **Decisión:** Utilizar componentes Standalone de Angular 18 sin módulos NgModules (`NgModule`).
- **Motivación:** Mayor simplicidad, rendimiento y alineación con los estándares modernos de Angular.

## 2. Motor de Reconocimiento Local (OpenAI Whisper en WASM)
- **Decisión:** Migrar del API de reconocimiento en la nube de Google (`webkitSpeechRecognition`) a **OpenAI Whisper (modelo `whisper-tiny` vía Transformers.js / WebAssembly)** ejecutado 100% en el navegador.
- **Motivación:** Evitar bloqueos de red, firewalls corporativos o restricciones de VPN que impedían la conexión con servidores externos en la nube, garantizando un funcionamiento 100% local, privado y offline.

## 3. Vúmetro RMS en Tiempo Real
- **Decisión:** Uso de `AudioContext`, `AnalyserNode` y `requestAnimationFrame` ejecutado fuera de la zona de Angular (`NgZone.runOutsideAngular`) para calcular el nivel RMS a ~30 fps sin afectar el rendimiento de la interfaz.

## 4. Sistema de Logs y Progreso (`core/log/`)
- **Decisión:** Buffer circular singleton con enmascaramiento automático de secretos (`mask-secrets.ts`) y componentes visuales reactivos (`LogPanelComponent` y `PipelineBarComponent`).
- **Motivación:** Trazabilidad robusta de eventos de audio, descarga de modelos y red, protegiendo credenciales y permitiendo depuración en tiempo real.

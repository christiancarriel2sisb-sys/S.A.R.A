# -*- coding: utf-8 -*-
import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches

OUTPUT_DIR = r"A:\IU Sara\S.A.R.A\Fase 2\Interfaz Usuario SARA\iteraciones\diagramas"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Configuración global de estilos limpios
plt.rcParams['font.sans-serif'] = 'DejaVu Sans'
plt.rcParams['font.family'] = 'sans-serif'

def create_box(ax, x, y, w, h, title, subtitle="", bg_color="#EBF8FF", border_color="#2B6CB0", title_color="#1A365D", sub_color="#2D3748", fontsize_title=10, fontsize_sub=8, corner_radius=0.03):
    box = patches.FancyBboxPatch(
        (x, y), w, h,
        boxstyle=f"round,pad=0.02,rounding_size={corner_radius}",
        facecolor=bg_color,
        edgecolor=border_color,
        linewidth=1.8,
        zorder=2
    )
    ax.add_patch(box)
    
    if subtitle:
        ax.text(x + w/2, y + h*0.68, title, ha='center', va='center', fontsize=fontsize_title, fontweight='bold', color=title_color, zorder=3)
        ax.text(x + w/2, y + h*0.32, subtitle, ha='center', va='center', fontsize=fontsize_sub, color=sub_color, zorder=3)
    else:
        ax.text(x + w/2, y + h/2, title, ha='center', va='center', fontsize=fontsize_title, fontweight='bold', color=title_color, zorder=3)

def create_arrow(ax, x1, y1, x2, y2, label="", color="#4A5568", rad=0.0, lw=1.8, label_color="#1A365D", fontsize=8.5, ha='center', va='bottom'):
    arrow = patches.FancyArrowPatch(
        (x1, y1), (x2, y2),
        connectionstyle=f"arc3,rad={rad}",
        arrowstyle="-|>,head_length=6,head_width=4",
        color=color,
        linewidth=lw,
        zorder=4
    )
    ax.add_patch(arrow)
    if label:
        mx = (x1 + x2) / 2
        my = (y1 + y2) / 2
        ax.text(mx, my + 0.02, label, ha=ha, va=va, fontsize=fontsize, fontweight='bold', color=label_color, zorder=5,
                bbox=dict(boxstyle="round,pad=0.2", facecolor="#FFFFFF", edgecolor="#CBD5E0", alpha=0.9, lw=0.8))

# ==========================================
# DIAGRAMA 1: ARQUITECTURA GENERAL DOBLE CEREBRO
# ==========================================
def generate_diagram_1():
    fig, ax = plt.subplots(figsize=(10, 6.5), dpi=300)
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 6.5)
    ax.axis('off')
    
    # Fondo general
    fig.patch.set_facecolor('#FFFFFF')
    
    # Título
    ax.text(5, 6.2, "ARQUITECTURA INTEGRAL DE ROBOT SARA: DOBLE CEREBRO", ha='center', va='center', fontsize=14, fontweight='bold', color='#1A365D')
    ax.text(5, 5.85, "Desacoplamiento entre Inteligencia Superior (Android) y Control Físico (Arduino)", ha='center', va='center', fontsize=10, color='#4A5568')

    # Gran Caja Cerebro Superior (Smartphone)
    big_phone = patches.FancyBboxPatch((0.5, 2.0), 9.0, 3.5, boxstyle="round,pad=0.04,rounding_size=0.05", facecolor="#F7FAFC", edgecolor="#2B6CB0", linewidth=2.2, linestyle='--', zorder=1)
    ax.add_patch(big_phone)
    ax.text(0.8, 5.25, "SMARTPHONE ANDROID (Cerebro Superior On-Device)", fontsize=11, fontweight='bold', color='#2B6CB0')

    # Módulos internos de Android
    create_box(ax, 0.8, 3.8, 2.4, 1.2, "Voz & Conversación", "SpeechRecognizer + SSE Gemini\nSentence Chunking + Barge-In", bg_color="#EBF8FF", border_color="#3182CE")
    create_box(ax, 3.8, 3.8, 2.4, 1.2, "Rostro Emocional", "Compose SVG (60 FPS)\nParpadeo + Lip-Sync Reactivo", bg_color="#FEFCBF", border_color="#D69E2E")
    create_box(ax, 6.8, 3.8, 2.4, 1.2, "Percepción & Visión", "CameraX (320x320 @ 10fps)\nML Kit Face + Pinhole 3D", bg_color="#C6F6D5", border_color="#38A169")
    
    create_box(ax, 1.8, 2.3, 3.0, 1.1, "Motor de Decisión Jev", "Campos de Potencial (APF)\nAtracción Meta / Repulsión Obstáculos", bg_color="#E2E8F0", border_color="#4A5568")
    create_box(ax, 5.4, 2.3, 3.8, 1.1, "Supervisor de Seguridad & USB Serial", "SafetySupervisor (Stop si Z < 0.40m)\nSerialDispatcher @ 10 Hz (115200 bps)", bg_color="#FED7D7", border_color="#E53E3E")

    # Cable USB-OTG
    create_arrow(ax, 5.0, 2.0, 5.0, 1.2, label="CABLE FÍSICO USB-OTG\nNAV:<angle>,<pwm>\\n | EMERGENCY_STOP", color="#805AD5", lw=2.5)

    # Gran Caja Cerebro Inferior (Arduino)
    big_arduino = patches.FancyBboxPatch((0.5, 0.1), 9.0, 1.05, boxstyle="round,pad=0.04,rounding_size=0.05", facecolor="#EDF2F7", edgecolor="#4A5568", linewidth=2.2, zorder=1)
    ax.add_patch(big_arduino)
    ax.text(0.8, 0.85, "BASE MOTRIZ / ARDUINO UNO (Cerebro Inferior)", fontsize=10.5, fontweight='bold', color='#2D3748')

    create_box(ax, 2.8, 0.25, 2.0, 0.65, "Driver L298N", "Puente H Motores DC", bg_color="#FFFFFF", border_color="#718096", fontsize_title=8.5, fontsize_sub=7)
    create_box(ax, 5.1, 0.25, 2.0, 0.65, "Ultrasonido HC-SR04", "Freno Físico (<25 cm)", bg_color="#FFFFFF", border_color="#E53E3E", fontsize_title=8.5, fontsize_sub=7)
    create_box(ax, 7.3, 0.25, 2.0, 0.65, "Servomotor Pin 3", "Barrido exploratorio", bg_color="#FFFFFF", border_color="#718096", fontsize_title=8.5, fontsize_sub=7)

    plt.tight_layout()
    path = os.path.join(OUTPUT_DIR, "diag1_arquitectura_general.png")
    plt.savefig(path, dpi=300)
    plt.close()
    print("Diag 1 generado:", path)

# ==========================================
# DIAGRAMA 2: PIPELINE VOZ, GEMINI SSE & BARGE-IN
# ==========================================
def generate_diagram_2():
    fig, ax = plt.subplots(figsize=(10, 5.8), dpi=300)
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 5.8)
    ax.axis('off')
    fig.patch.set_facecolor('#FFFFFF')

    ax.text(5, 5.5, "PIPELINE DE VOZ CONVERSACIONAL, STREAMING SSE Y BARGE-IN", ha='center', va='center', fontsize=13, fontweight='bold', color='#1A365D')
    ax.text(5, 5.15, "Latencia mínima y cancelación instantánea al detectar habla del usuario", ha='center', va='center', fontsize=9.5, color='#4A5568')

    # Componentes del flujo
    create_box(ax, 0.5, 3.5, 1.8, 1.1, "Usuario Habla", "Voz capturada por mic\nes-CL (SpeechRecognizer)", bg_color="#EBF8FF", border_color="#3182CE")
    create_box(ax, 2.9, 3.5, 2.0, 1.1, "SaraViewModel", "Máquina de Estados\nOrquestador Coroutines", bg_color="#EDF2F7", border_color="#4A5568")
    create_box(ax, 5.4, 3.5, 2.2, 1.1, "Gemini 2.5 Flash", "Streaming SSE (alt=sse)\nTokens línea por línea", bg_color="#FEFCBF", border_color="#D69E2E")
    create_box(ax, 8.0, 3.5, 1.6, 1.1, "Sentence\nChunker", "Corte por '.', '?', '!'\nTextUtils.stripMarkdown", bg_color="#C6F6D5", border_color="#38A169")

    create_arrow(ax, 2.3, 4.05, 2.9, 4.05, label="Texto")
    create_arrow(ax, 4.9, 4.05, 5.4, 4.05, label="Prompt")
    create_arrow(ax, 7.6, 4.05, 8.0, 4.05, label="Tokens")

    # Hacia el TTS
    create_box(ax, 8.0, 1.7, 1.6, 1.1, "TextToSpeech", "QUEUE_ADD\nAudio continuo", bg_color="#E9D8FD", border_color="#805AD5")
    create_arrow(ax, 8.8, 3.5, 8.8, 2.8, label="Frase completa")

    # Habla al anciano
    create_box(ax, 5.4, 1.7, 2.0, 1.1, "Parlante Celular", "Voz sintética fluida\nLatencia < 1.2s", bg_color="#EBF8FF", border_color="#3182CE")
    create_arrow(ax, 8.0, 2.25, 7.4, 2.25, label="Audio PCM")

    # BARGE-IN KILL SWITCH
    box_bargein = patches.FancyBboxPatch((0.5, 0.3), 7.0, 1.0, boxstyle="round,pad=0.03,rounding_size=0.04", facecolor="#FED7D7", edgecolor="#E53E3E", linewidth=2.0, zorder=2)
    ax.add_patch(box_bargein)
    ax.text(4.0, 0.95, "MECANISMO BARGE-IN (KILL SWITCH INMEDIATO)", ha='center', va='center', fontsize=10, fontweight='bold', color='#9B2C2C', zorder=3)
    ax.text(4.0, 0.55, "Si usuario habla durante SPEAKING -> 1. streamJob.cancel()  2. TTS.stop()  3. Estado = LISTENING", ha='center', va='center', fontsize=8.5, color='#742A2A', zorder=3)

    create_arrow(ax, 1.4, 3.5, 1.4, 1.3, label="Interrupción!", color="#E53E3E", lw=2.2)
    create_arrow(ax, 7.5, 0.8, 8.8, 1.7, label="Silenciar!", color="#E53E3E", lw=2.2)

    plt.tight_layout()
    path = os.path.join(OUTPUT_DIR, "diag2_pipeline_voz_bargein.png")
    plt.savefig(path, dpi=300)
    plt.close()
    print("Diag 2 generado:", path)

# ==========================================
# DIAGRAMA 3: PIPELINE DE VISIÓN Y SEGUIMIENTO FACIAL
# ==========================================
def generate_diagram_3():
    fig, ax = plt.subplots(figsize=(10, 5.5), dpi=300)
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 5.5)
    ax.axis('off')
    fig.patch.set_facecolor('#FFFFFF')

    ax.text(5, 5.2, "PIPELINE DE VISIÓN Y SEGUIMIENTO FACIAL MONOCULAR", ha='center', va='center', fontsize=13, fontweight='bold', color='#1A365D')
    ax.text(5, 4.85, "Captura eficiente en gama baja y cálculo de desviación angular para el chasis", ha='center', va='center', fontsize=9.5, color='#4A5568')

    create_box(ax, 0.5, 3.2, 1.8, 1.1, "CameraX", "320x320 @ 10 FPS\nDROP_LATEST", bg_color="#E2E8F0", border_color="#4A5568")
    create_box(ax, 2.9, 3.2, 2.0, 1.1, "FaceAnalyzer", "Google ML Kit (On-Device)\nBounding Box B(l,t,r,b)", bg_color="#C6F6D5", border_color="#38A169")
    create_box(ax, 5.5, 3.2, 2.3, 1.1, "Cálculo Angular", "θ_horiz = normX * (HFOV/2)\nϕ_vert = normY * (VFOV/2)", bg_color="#FEFCBF", border_color="#D69E2E")

    create_arrow(ax, 2.3, 3.75, 2.9, 3.75, label="Frame YUV")
    create_arrow(ax, 4.9, 3.75, 5.5, 3.75, label="Caja 2D")

    # Dos ramas de salida
    create_box(ax, 4.0, 1.3, 2.4, 1.2, "Rostro SVG Virtual", "Animación de Pupilas (60 FPS)\nContacto visual con la persona", bg_color="#EBF8FF", border_color="#3182CE")
    create_box(ax, 7.0, 1.3, 2.5, 1.2, "Orientación Chasis", "Si |θ| > 10° (Deadzone)\nComando giro al Arduino", bg_color="#FED7D7", border_color="#E53E3E")

    create_arrow(ax, 6.2, 3.2, 5.2, 2.5, label="Ángulo ϕ (Ojos)", color="#3182CE")
    create_arrow(ax, 7.2, 3.2, 8.2, 2.5, label="Ángulo θ (Yaw)", color="#E53E3E")

    plt.tight_layout()
    path = os.path.join(OUTPUT_DIR, "diag3_vision_seguimiento.png")
    plt.savefig(path, dpi=300)
    plt.close()
    print("Diag 3 generado:", path)

# ==========================================
# DIAGRAMA 4: PROYECCIÓN PINHOLE 3D Y MOTOR JEV
# ==========================================
def generate_diagram_4():
    fig, ax = plt.subplots(figsize=(10, 5.8), dpi=300)
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 5.8)
    ax.axis('off')
    fig.patch.set_facecolor('#FFFFFF')

    ax.text(5, 5.5, "PROYECCIÓN ESPACIAL 3D (PINHOLE) Y NAVEGACIÓN REACTIVA JEV", ha='center', va='center', fontsize=13, fontweight='bold', color='#1A365D')
    ax.text(5, 5.15, "Estimación métrica monocular y control por campos de potencial artificial (APF)", ha='center', va='center', fontsize=9.5, color='#4A5568')

    create_box(ax, 0.5, 3.5, 2.2, 1.1, "Detección Monocular", "YOLOv8 / ML Kit\nAltura píxeles: h_bbox", bg_color="#E2E8F0", border_color="#4A5568")
    create_box(ax, 3.3, 3.5, 2.8, 1.1, "SpatialProjector (Pinhole)", "Z = (f_y * H_real) / h_bbox\nX = ((x_c - c_x) * Z) / f_x", bg_color="#FEFCBF", border_color="#D69E2E")
    create_box(ax, 6.7, 3.5, 2.8, 1.1, "Motor Decisión Jev (APF)", "F_attr (Hacia el usuario)\nF_rep (Lejos de obstáculos)", bg_color="#C6F6D5", border_color="#38A169")

    create_arrow(ax, 2.7, 4.05, 3.3, 4.05, label="Caja 2D")
    create_arrow(ax, 6.1, 4.05, 6.7, 4.05, label="Coord (X, Z)")

    # Supervisor de seguridad
    create_box(ax, 6.7, 1.7, 2.8, 1.2, "SafetySupervisor", "Evaluación Crítica de Z:\n¿Z < 0.40m? -> STOP TOTAL", bg_color="#FED7D7", border_color="#E53E3E")
    create_arrow(ax, 8.1, 3.5, 8.1, 2.9, label="Distancia Z")

    # Salida Cinemática
    create_box(ax, 2.5, 1.7, 3.2, 1.2, "Vector Cinemático Salida", "NAV:<angle>,<pwm>\\n\no EMERGENCY_STOP", bg_color="#EBF8FF", border_color="#3182CE")
    create_arrow(ax, 6.7, 2.3, 5.7, 2.3, label="Comando Seguro", color="#3182CE")

    plt.tight_layout()
    path = os.path.join(OUTPUT_DIR, "diag4_pinhole_jev_navegacion.png")
    plt.savefig(path, dpi=300)
    plt.close()
    print("Diag 4 generado:", path)

# ==========================================
# DIAGRAMA 5: ENLACE SERIAL USB-OTG Y ARDUINO
# ==========================================
def generate_diagram_5():
    fig, ax = plt.subplots(figsize=(10, 5.2), dpi=300)
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 5.2)
    ax.axis('off')
    fig.patch.set_facecolor('#FFFFFF')

    ax.text(5, 4.9, "ENLACE SERIAL USB-OTG Y ARQUITECTURA DE CONTROL ARDUINO", ha='center', va='center', fontsize=13, fontweight='bold', color='#1A365D')
    ax.text(5, 4.55, "Comunicación 10 Hz a 115200 baudios y redundancia física por ultrasonido", ha='center', va='center', fontsize=9.5, color='#4A5568')

    create_box(ax, 0.5, 2.6, 2.4, 1.3, "Android Dispatcher", "SerialDispatcher.kt\nBucle temporizado a 10 Hz\nDriver CDC/CH340/FTDI", bg_color="#EBF8FF", border_color="#3182CE")
    create_box(ax, 3.9, 2.6, 2.2, 1.3, "Trama Serial USB", "115200 bps (8-N-1)\n'NAV:<ang>,<pwm>\\n'\n'EMERGENCY_STOP'", bg_color="#FEFCBF", border_color="#D69E2E")
    create_box(ax, 7.1, 2.6, 2.4, 1.3, "Arduino Uno", "prueba_sara.ino\nParser Serial + Timer\nControl Potencia Directa", bg_color="#EDF2F7", border_color="#4A5568")

    create_arrow(ax, 2.9, 3.25, 3.9, 3.25, label="USB-OTG", color="#805AD5", lw=2.2)
    create_arrow(ax, 6.1, 3.25, 7.1, 3.25, label="UART Rx", color="#805AD5", lw=2.2)

    # Actuadores de Arduino
    create_box(ax, 5.5, 0.7, 1.9, 1.1, "Driver L298N", "Pines in1..4, enA, enB\nTracción diferencial", bg_color="#FFFFFF", border_color="#718096", fontsize_title=8.5, fontsize_sub=7)
    create_box(ax, 7.7, 0.7, 1.9, 1.1, "Ultrasonido", "Sensor HC-SR04\nFreno HW (<25 cm)", bg_color="#FED7D7", border_color="#E53E3E", fontsize_title=8.5, fontsize_sub=7)

    create_arrow(ax, 7.8, 2.6, 6.7, 1.8, label="Control")
    create_arrow(ax, 8.6, 2.6, 8.6, 1.8, label="Trigger/Echo")

    plt.tight_layout()
    path = os.path.join(OUTPUT_DIR, "diag5_serial_arduino.png")
    plt.savefig(path, dpi=300)
    plt.close()
    print("Diag 5 generado:", path)

if __name__ == "__main__":
    generate_diagram_1()
    generate_diagram_2()
    generate_diagram_3()
    generate_diagram_4()
    generate_diagram_5()
    print("Todos los diagramas fueron generados con éxito.")


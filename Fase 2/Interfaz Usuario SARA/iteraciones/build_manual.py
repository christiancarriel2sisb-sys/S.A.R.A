# -*- coding: utf-8 -*-
import os
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import parse_xml, OxmlElement
from docx.oxml.ns import nsdecls, qn

def set_cell_background(cell, hex_color):
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{hex_color}"/>')
    cell._tc.get_or_add_tcPr().append(shd)

def set_cell_margins(cell, top=100, bottom=100, left=150, right=150):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(f'<w:tcMar {nsdecls("w")}><w:top w:w="{top}" w:type="dxa"/><w:bottom w:w="{bottom}" w:type="dxa"/><w:left w:w="{left}" w:type="dxa"/><w:right w:w="{right}" w:type="dxa"/></w:tcMar>')
    tcPr.append(tcMar)

def add_callout(doc, text, title="NOTA TÉCNICA", border_color="1B365D", bg_color="F0F4F8"):
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False
    tbl.columns[0].width = Inches(6.5)
    
    cell = tbl.cell(0, 0)
    set_cell_background(cell, bg_color)
    set_cell_margins(cell, top=140, bottom=140, left=200, right=200)
    
    tcPr = cell._tc.get_or_add_tcPr()
    tcBorders = parse_xml(f'<w:tcBorders {nsdecls("w")}><w:left w:val="single" w:sz="24" w:space="0" w:color="{border_color}"/><w:top w:val="none"/><w:right w:val="none"/><w:bottom w:val="none"/></w:tcBorders>')
    tcPr.append(tcBorders)
    
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.15
    run_title = p.add_run(f"[{title}] ")
    run_title.bold = True
    run_title.font.name = "Calibri"
    run_title.font.size = Pt(10)
    run_title.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D)
    
    run_text = p.add_run(text)
    run_text.font.name = "Calibri"
    run_text.font.size = Pt(9.5)
    run_text.font.color.rgb = RGBColor(0x2D, 0x37, 0x48)
    
    p_after = doc.add_paragraph()
    p_after.paragraph_format.space_before = Pt(0)
    p_after.paragraph_format.space_after = Pt(4)

def add_code_block(doc, code_str, language="Kotlin / C++"):
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False
    tbl.columns[0].width = Inches(6.5)
    
    cell = tbl.cell(0, 0)
    set_cell_background(cell, "F7FAFC")
    set_cell_margins(cell, top=120, bottom=120, left=180, right=180)
    
    tcPr = cell._tc.get_or_add_tcPr()
    tcBorders = parse_xml(f'<w:tcBorders {nsdecls("w")}><w:left w:val="single" w:sz="12" w:space="0" w:color="CBD5E0"/><w:top w:val="single" w:sz="6" w:space="0" w:color="E2E8F0"/><w:right w:val="single" w:sz="6" w:space="0" w:color="E2E8F0"/><w:bottom w:val="single" w:sz="6" w:space="0" w:color="E2E8F0"/></w:tcBorders>')
    tcPr.append(tcBorders)
    
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.05
    run = p.add_run(code_str.strip())
    run.font.name = "Consolas"
    run.font.size = Pt(8.5)
    run.font.color.rgb = RGBColor(0x1A, 0x20, 0x2C)
    
    p_after = doc.add_paragraph()
    p_after.paragraph_format.space_before = Pt(0)
    p_after.paragraph_format.space_after = Pt(4)

def format_heading(doc, text, level):
    h = doc.add_heading(text, level=level)
    h.paragraph_format.keep_with_next = True
    run = h.runs[0]
    run.font.name = "Calibri"
    if level == 1:
        run.font.size = Pt(17)
        run.bold = True
        run.font.color.rgb = RGBColor(0x1A, 0x36, 0x5D) # Navy
        h.paragraph_format.space_before = Pt(16)
        h.paragraph_format.space_after = Pt(6)
    elif level == 2:
        run.font.size = Pt(13.5)
        run.bold = True
        run.font.color.rgb = RGBColor(0x2B, 0x6C, 0xB0) # Slate Blue
        h.paragraph_format.space_before = Pt(12)
        h.paragraph_format.space_after = Pt(4)
    elif level == 3:
        run.font.size = Pt(11.5)
        run.bold = True
        run.font.color.rgb = RGBColor(0x2D, 0x37, 0x48) # Dark Charcoal
        h.paragraph_format.space_before = Pt(8)
        h.paragraph_format.space_after = Pt(3)
    return h

def add_body_p(doc, text, bold_prefix="", italic_prefix=""):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(5)
    p.paragraph_format.line_spacing = 1.15
    if bold_prefix:
        r_b = p.add_run(bold_prefix)
        r_b.bold = True
        r_b.font.name = "Calibri"
        r_b.font.size = Pt(10.5)
        r_b.font.color.rgb = RGBColor(0x1A, 0x20, 0x2C)
    if italic_prefix:
        r_i = p.add_run(italic_prefix)
        r_i.italic = True
        r_i.font.name = "Calibri"
        r_i.font.size = Pt(10.5)
        r_i.font.color.rgb = RGBColor(0x4A, 0x55, 0x68)
    r = p.add_run(text)
    r.font.name = "Calibri"
    r.font.size = Pt(10.5)
    r.font.color.rgb = RGBColor(0x2D, 0x37, 0x48)
    return p

def add_bullet_p(doc, text, bold_prefix=""):
    p = doc.add_paragraph(style='List Bullet')
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.15
    if bold_prefix:
        r_b = p.add_run(bold_prefix)
        r_b.bold = True
        r_b.font.name = "Calibri"
        r_b.font.size = Pt(10)
        r_b.font.color.rgb = RGBColor(0x1A, 0x20, 0x2C)
    r = p.add_run(text)
    r.font.name = "Calibri"
    r.font.size = Pt(10)
    r.font.color.rgb = RGBColor(0x2D, 0x37, 0x48)
    return p

def create_table(doc, headers, data, col_widths=None):
    tbl = doc.add_table(rows=len(data) + 1, cols=len(headers))
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False
    
    # Header
    hdr_cells = tbl.rows[0].cells
    for i, title in enumerate(headers):
        hdr_cells[i].text = title
        set_cell_background(hdr_cells[i], "1A365D")
        set_cell_margins(hdr_cells[i], top=100, bottom=100, left=120, right=120)
        p = hdr_cells[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        for run in p.runs:
            run.font.bold = True
            run.font.name = "Calibri"
            run.font.size = Pt(9.5)
            run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
            
    # Rows
    for row_idx, row_data in enumerate(data):
        row_cells = tbl.rows[row_idx + 1].cells
        bg_col = "FFFFFF" if row_idx % 2 == 0 else "F7FAFC"
        for col_idx, cell_value in enumerate(row_data):
            row_cells[col_idx].text = str(cell_value)
            set_cell_background(row_cells[col_idx], bg_col)
            set_cell_margins(row_cells[col_idx], top=80, bottom=80, left=120, right=120)
            p = row_cells[col_idx].paragraphs[0]
            for run in p.runs:
                run.font.name = "Calibri"
                run.font.size = Pt(9)
                run.font.color.rgb = RGBColor(0x2D, 0x37, 0x48)
                
    # Set borders
    for row in tbl.rows:
        for cell in row.cells:
            tcPr = cell._tc.get_or_add_tcPr()
            tcBorders = parse_xml(f'<w:tcBorders {nsdecls("w")}><w:top w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/><w:left w:val="none"/><w:right w:val="none"/></w:tcBorders>')
            tcPr.append(tcBorders)
            
    if col_widths:
        for row in tbl.rows:
            for i, w in enumerate(col_widths):
                row.cells[i].width = Inches(w)
                
    p_after = doc.add_paragraph()
    p_after.paragraph_format.space_before = Pt(0)
    p_after.paragraph_format.space_after = Pt(6)

def generate_full_manual():
    doc = docx.Document()
    
    # Page setup
    for sec in doc.sections:
        sec.top_margin = Inches(1.0)
        sec.bottom_margin = Inches(1.0)
        sec.left_margin = Inches(1.0)
        sec.right_margin = Inches(1.0)
        
    # ==========================================
    # PORTADA
    # ==========================================
    p_pre = doc.add_paragraph()
    p_pre.paragraph_format.space_before = Pt(36)
    p_pre.paragraph_format.space_after = Pt(12)
    p_pre.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_inst = p_pre.add_run("DUOC UC — SEDE PLAZA OESTE\nESCUELA DE INFORMÁTICA Y TELECOMUNICACIONES\nPROYECTO CAPSTONE (APT122)")
    r_inst.font.name = "Calibri"
    r_inst.font.size = Pt(11)
    r_inst.bold = True
    r_inst.font.color.rgb = RGBColor(0x71, 0x80, 0x96)
    
    p_title = doc.add_paragraph()
    p_title.paragraph_format.space_before = Pt(24)
    p_title.paragraph_format.space_after = Pt(10)
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_title = p_title.add_run("S.A.R.A")
    r_title.font.name = "Calibri"
    r_title.font.size = Pt(36)
    r_title.bold = True
    r_title.font.color.rgb = RGBColor(0x1A, 0x36, 0x5D)
    
    p_sub = doc.add_paragraph()
    p_sub.paragraph_format.space_before = Pt(0)
    p_sub.paragraph_format.space_after = Pt(30)
    p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_sub = p_sub.add_run("SISTEMA ASISTENTE ROBÓTICO DE APOYO\nManual Técnico de Arquitectura, Funcionamiento de Módulos e Integración Hardware/Software")
    r_sub.font.name = "Calibri"
    r_sub.font.size = Pt(14)
    r_sub.bold = True
    r_sub.font.color.rgb = RGBColor(0x2B, 0x6C, 0xB0)
    
    p_meta = doc.add_paragraph()
    p_meta.paragraph_format.space_before = Pt(60)
    p_meta.paragraph_format.space_after = Pt(20)
    p_meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_team = p_meta.add_run("Equipo de Desarrollo:\nChristian Carriel | Francisca Otárola | Kevin Castro\n\nDocente Guía:\nArturo Vargas\n\nNoviembre 2026 — Versión 2.0 (Fase 2)")
    r_team.font.name = "Calibri"
    r_team.font.size = Pt(11)
    r_team.font.color.rgb = RGBColor(0x2D, 0x37, 0x48)
    
    doc.add_page_break()
    
    # ==========================================
    # SECCIÓN 1: INTRODUCCIÓN Y FILOSOFÍA
    # ==========================================
    format_heading(doc, "1. Introducción y Filosofía del Robot SARA", level=1)
    
    add_body_p(doc, 
        "El proyecto S.A.R.A (Sistema Asistente Robótico de Apoyo) fue concebido como una respuesta tecnológica de alto impacto para abordar el aislamiento, la pérdida de autonomía y las necesidades de acompañamiento cotidiano de los adultos mayores en entornos geriátricos o domiciliarios. A diferencia de las soluciones convencionales de robótica que imponen pantallas táctiles densas o comandos de texto rígidos, SARA se basa en un paradigma de interacción 100% humanizada y multimodal: el usuario no tiene que tocar ninguna pantalla; simplemente le habla al robot como si se tratara de una persona, y el robot responde vocalmente mientras establece contacto visual mediante un rostro animado expresivo y orienta su base motriz hacia el interlocutor.")
    
    format_heading(doc, "1.1 El Cambio Arquitectónico Radical: De Web Distribuido a Android On-Device", level=2)
    add_body_p(doc,
        "Durante las etapas iniciales de la Fase 1 y Fase 2, el prototipo operaba bajo un stack distribuido compuesto por una aplicación web en Angular 18, un servidor proxy intermedio en Node.js/Express, el motor de transcripción Whisper de OpenAI y la API de Gemini. Aunque este enfoque permitió validar la lógica inicial, presentaba debilidades críticas incompatibles con un robot físico autónomo:")
    
    add_bullet_p(doc, "El flujo requería hasta tres saltos de red consecutivos (Angular -> Node -> Whisper -> Node -> Gemini -> Node -> Angular). Si la red Wi-Fi fluctuaba, la latencia entre la pregunta del anciano y la respuesta superaba los 6 a 8 segundos, arruinando la naturalidad conversacional.", "Latencia Intolerable: ")
    add_bullet_p(doc, "Dependía de que una laptop o servidor ejecutara el proceso Node.js en la misma red local. Si el robot se desplazaba a una zona ciega de la casa o clínica, quedaba completamente inoperante.", "Dependencia de Infraestructura Externa: ")
    add_bullet_p(doc, "Desde un navegador web (Chrome/Angular), acceder al puerto serial USB de un Arduino es sumamente inestable, dependiente de Web Serial API y propenso a desconexiones de permisos.", "Imposibilidad de Control Físico Determinista: ")
    
    add_callout(doc, 
        "La decisión de ingeniería definitiva fue migrar el 100% de la lógica a una aplicación nativa en Kotlin ejecutada directamente en el smartphone montado sobre el robot. El teléfono actúa como 'Cerebro Superior', procesando voz, visión, inteligencia artificial y comunicándose por cable USB-OTG directo con el Arduino ('Cerebro Inferior').",
        title="DECISIÓN ARQUITECTÓNICA CLAVE")
        
    format_heading(doc, "1.2 Arquitectura Jerárquica de Doble Cerebro (High-Level vs Low-Level)", level=2)
    add_body_p(doc,
        "La robótica asistencial moderna requiere desacoplar el procesamiento cognitivo de alto nivel del control de bajo nivel. SARA implementa esta separación de manera estricta:")
    
    headers_cerebro = ["Capa Robótica", "Hardware Responsable", "Tecnología / Stack", "Responsabilidades Críticas"]
    data_cerebro = [
        ["Cerebro Superior (High-Level)", "Smartphone Android montado en el robot", "Kotlin, Jetpack Compose, CameraX, ML Kit, OkHttp SSE", "Comprensión del lenguaje natural (LLM), visión artificial monocular, decisión probabilística, síntesis de voz y rostro expresivo."],
        ["Cerebro Inferior (Low-Level)", "Placa Arduino Uno en el chasis móvil", "C++ / Arduino Wiring, Driver L298N, Servomotor", "Control cinemático de motores DC, generación de señales de potencia, lectura de ultrasonido frontal a 50 Hz y parada de seguridad por hardware."]
    ]
    create_table(doc, headers_cerebro, data_cerebro, [1.5, 1.4, 1.6, 2.0])
    
    # ==========================================
    # SECCIÓN 2: MÓDULO DE VOZ Y CONVERSACIÓN
    # ==========================================
    format_heading(doc, "2. Módulo de Voz e Inteligencia Conversacional (Iteración 1)", level=1)
    
    add_body_p(doc,
        "El módulo de voz es la vía de interacción primaria de SARA. Está diseñado para ofrecer una experiencia conversacional sin fricción, con latencias ultra bajas y con capacidad de interrupción en tiempo real (Barge-In).")
        
    format_heading(doc, "2.1 Captura Continua de Voz (`VoiceInputManager.kt`)", level=2)
    add_body_p(doc,
        "La captura de audio no depende de servicios en la nube costosos ni de grabación por lotes. Se utiliza el servicio nativo android.speech.SpeechRecognizer del sistema operativo Android configurado con el locale de Chile ('es-CL') y fallback automático a 'es-ES':")
    
    add_bullet_p(doc, "El reconocedor se mantiene en bucle constante. Cuando el usuario termina una frase, emite inmediatamente el evento onResults(results).", "Escucha Infinita con Auto-recuperación: ")
    add_bullet_p(doc, "En caso de pausas largas o errores transitorios (código 6 por timeout de silencio o código 7 por no reconocimiento), el sistema no crashea ni muestra alertas invasivas; incrementa un contador de reintentos silenciosos y se reinicia en menos de 50 milisegundos.", "Manejo Silencioso de Errores: ")
    add_bullet_p(doc, "El callback onRmsChanged(rmsdB) expone el nivel de decibelios del micrófono en tiempo real, lo que permite alimentar visualmente la interfaz y sincronizar el estado reactivo del robot.", "Monitoreo Vúmetro RMS: ")
    
    format_heading(doc, "2.2 Consumo de Gemini 2.5 Flash mediante Streaming SSE (`GeminiRepository.kt`)", level=2)
    add_body_p(doc,
        "En un asistente conversacional para ancianos, esperar 4 o 5 segundos a que un LLM genere toda su respuesta causa confusión inmediata (el usuario piensa que el robot no lo escuchó). Para resolver esto, SARA se conecta al endpoint de streaming Server-Sent Events (SSE) de Gemini 2.5 Flash:")
    
    add_code_block(doc, 
"""// Conexión directa mediante OkHttp con Server-Sent Events (SSE)
val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=$apiKey"
val request = Request.Builder()
    .url(url)
    .post(requestBody)
    .build()

client.newCall(request).execute().use { response ->
    val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
    var line: String?
    while (reader.readLine().also { line = it } != null) {
        currentCoroutineContext().ensureActive() // Cooperación de cancelación inmediata
        if (line!!.startsWith("data: ")) {
            val jsonChunk = line!!.substring(6)
            val token = parseCandidateText(jsonChunk)
            token?.let { emit(it) } // Se emite token a token
        }
    }
}""", language="Kotlin")

    format_heading(doc, "2.3 El Algoritmo de Fragmentación de Oraciones (*Sentence Chunking*)", level=2)
    add_body_p(doc,
        "Existe un dilema técnico fundamental entre el LLM y el sintetizador de voz (TTS):", bold_prefix="El Problema: ")
    add_bullet_p(doc, "Si enviamos cada palabra individual al TTS apenas llega, el sintetizador habla como un robot tartamudo, sin entonación ni pausas naturales.", "Enfoque Palabra por Palabra: ")
    add_bullet_p(doc, "Si esperamos a que Gemini termine toda su respuesta (100 tokens), el usuario espera 4 segundos en silencio absoluto.", "Enfoque de Respuesta Completa: ")
    
    add_body_p(doc,
        "SARA implementa un algoritmo intermedio de acumulación de oraciones en streaming: a medida que los tokens llegan desde el socket HTTP, se depositan en un StringBuilder dinámico. Un analizador sintáctico busca activamente signos de puntuación terminadores ('.', '?', '!', '\\n'). En el momento exacto en que detecta el fin de la primera oración, esa oración se extrae, se limpia de formato Markdown con TextUtils.stripMarkdown() y se despacha de inmediato al motor TextToSpeech con la bandera TextToSpeech.QUEUE_ADD. Mientras el usuario ya está escuchando la primera frase con voz humana y fluida, Gemini sigue generando la segunda y tercera frase en segundo plano. La latencia percibida cae a menos de 1.2 segundos.", bold_prefix="La Solución (Sentence Chunking): ")

    format_heading(doc, "2.4 El Mecanismo de Interrupción Instantánea (*Barge-In*)", level=2)
    add_body_p(doc,
        "¿Qué ocurre si el robot está hablando pero el anciano quiere corregir su pregunta, decir 'espera' o hacer otra consulta? En la mayoría de los sistemas, el robot sigue hablando como un loro sordo. En SARA se programó un interruptor de corte en caliente (Kill Switch):")
    
    add_bullet_p(doc, "El bucle de descarga de red está anclado a un Job cancelable de Kotlin Coroutines (streamJob). Si VoiceInputManager detecta que el usuario comenzó a hablar (onBeginningOfSpeech), se dispara una señal de cancelación inmediata.", "1. Aborto de Red: ")
    add_bullet_p(doc, "streamJob?.cancel() rompe el socket TCP/HTTP con Gemini al instante, evitando consumo innecesario de tokens y ancho de banda.", "2. Cierre de Socket: ")
    add_bullet_p(doc, "Se llama a TextToSpeech.stop(), enmudeciendo el parlante del smartphone en menos de 5 milisegundos y vaciando cualquier oración en cola.", "3. Silenciamiento Inmediato de Audio: ")
    add_bullet_p(doc, "La máquina de estados pasa instantáneamente de SPEAKING a LISTENING, lista para capturar la nueva orden del usuario.", "4. Transición Reactiva: ")

    format_heading(doc, "2.5 Prompt Cognitivo y Seguridad Clínica Estricta (`SaraPrompt.kt`)", level=2)
    add_body_p(doc,
        "Por tratarse de un asistente para personas mayores, el robot tiene grabado en su núcleo cognitivo reglas de bioseguridad ética innegociables. El prompt del sistema prohíbe taxativamente dar diagnósticos médicos, recomendar fármacos o ajustar dosis. Si un anciano pregunta '¿cuántas gotas de clonazepam debo tomar?', SARA tiene la instrucción programada de rechazar la consulta de forma cálida pero firme, derivándolo inmediatamente a un médico profesional o familiar responsable.")

    # ==========================================
    # SECCIÓN 3: ROSTRO EMOCIONAL
    # ==========================================
    format_heading(doc, "3. Módulo de Expresión y Rostro Emocional Procedural", level=1)
    
    add_body_p(doc,
        "El rostro de SARA no es un video pregrabado ni un GIF estático. Es un componente vectorial procedural renderizado a 60 FPS mediante Canvas de Jetpack Compose (FaceComponent.kt y FaceAnimator.kt).")
        
    add_bullet_p(doc, "Un generador estocástico dispara eventos de parpadeo cada 2 a 5 segundos con una curva de animación sinusoidal en los párpados, simulando la fisiología humana para evitar el efecto 'mirada fija espeluznante' (uncanny valley).", "Parpadeo Autónomo Fisiológico: ")
    add_bullet_p(doc, "Durante los estados de reposo o escucha, las pupilas ejecutan pequeños desplazamientos aleatorios (micro-sacadas oculares), dando la impresión de que el robot está 'vivo' y atento a su entorno.", "Micro-sacadas Oculares: ")
    add_bullet_p(doc, "Cuando el robot verbaliza una respuesta, la altura y ancho de la boca SVG oscilan dinámicamente en sincronía con los eventos del TTS, modulando la apertura bucal de acuerdo al ritmo fonético de la palabra.", "Sincronía Labial Procedural (Lip-Sync): ")
    add_bullet_p(doc, "El rostro altera sutilmente el color de los iris y la curvatura de las cejas según el estado: azul/cian brillante en reposo y habla, amarillo/ámbar en procesamiento de pensamiento (THINKING), y verde esmeralda al detectar un rostro conocido.", "Colorimetría Emocional: ")

    # ==========================================
    # SECCIÓN 4: VISIÓN Y SEGUIMIENTO FACIAL
    # ==========================================
    format_heading(doc, "4. Módulo de Visión y Seguimiento Facial (Iteración 2)", level=1)
    
    add_body_p(doc,
        "Para que la interacción con el adulto mayor sea natural, el robot debe saber dónde está la persona y mirarla a los ojos. Este módulo implementa visión artificial monocular utilizando la cámara frontal del teléfono.")
        
    format_heading(doc, "4.1 Pipeline de Captura con CameraX (`CameraManager.kt`)", level=2)
    add_body_p(doc,
        "En un smartphone de gama baja o media, procesar video a 1080p a 30 FPS provocaría sobrecalentamiento instantáneo y cierre forzoso por memoria insuficiente (OOM). Por ello, el pipeline se configuró estratégicamente:")
    add_bullet_p(doc, "La cámara captura en un target optimizado de 320x320 píxeles a una tasa objetivo de 8 a 10 cuadros por segundo, suficiente para seguimiento humano y con un consumo de CPU inferior al 15%.", "Resolución Reducida y Frecuencia Estable: ")
    add_bullet_p(doc, "Se utiliza la estrategia ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST. Si el procesador está ocupado analizando un fotograma y llega otro, el fotograma nuevo reemplaza al anterior en memoria sin crear colas acumulativas.", "Backpressure DROP_LATEST: ")

    format_heading(doc, "4.2 Google ML Kit Face Detection y Proyección Angular Monocular", level=2)
    add_body_p(doc,
        "Cada fotograma se entrega a FaceAnalyzer.kt, que lo convierte a InputImage en espacio de color YUV_420_888 y lo procesa con Google ML Kit Face Detection en modo PERFORMANCE (100% on-device, sin internet).")
    
    add_body_p(doc,
        "Cuando se detecta un rostro, el modelo entrega la caja delimitadora (Bounding Box) con coordenadas en píxeles (left, top, right, bottom). El módulo calcula el centroide del rostro (x_c, y_c) y lo normaliza con respecto al centro óptico de la imagen:", bold_prefix="Cálculo Geométrico: ")

    add_code_block(doc,
"""// Cálculo de la desviación angular horizontal (Yaw) y vertical (Pitch)
val centerX = (face.boundingBox.left + face.boundingBox.right) / 2f
val centerY = (face.boundingBox.top + face.boundingBox.bottom) / 2f

// Normalización respecto al centro óptico [-1.0 a +1.0]
val normX = (centerX - (imageWidth / 2f)) / (imageWidth / 2f)
val normY = (centerY - (imageHeight / 2f)) / (imageHeight / 2f)

// Conversión a grados según el campo de visión horizontal (HFOV ≈ 60°)
val thetaHorizontal = normX * (HFOV / 2f) // Grados a corregir por el chasis
val phiVertical = normY * (VFOV / 2f)     // Grados para orientar las pupilas""", language="Kotlin")

    add_body_p(doc,
        "Este cálculo tiene una doble utilidad: en el plano visual, desplaza las pupilas de SARA en el canvas para que sigan a la persona; en el plano cinemático, si el ángulo horizontal supera una banda muerta de tolerancia (deadzone de ±10°), genera una consigna para que el chasis de Arduino gire suavemente sobre su propio eje y quede de frente al usuario.")

    # ==========================================
    # SECCIÓN 5: PERCEPCIÓN ESPACIAL Y PINHOLE 3D
    # ==========================================
    format_heading(doc, "5. Módulo de Percepción Espacial y Proyección 3D Monocular (Iteración 3)", level=1)
    
    add_body_p(doc,
        "Uno de los mayores retos de la robótica de bajo costo es estimar a qué distancia métrica se encuentran los objetos sin disponer de un sensor LIDAR o una cámara estereoscópica costosa. SARA resuelve este problema implementando el Modelo Matemático de Cámara Pinhole (SpatialProjector.kt).")
        
    format_heading(doc, "5.1 La Ecuación Óptica del Pinhole Camera Model", level=2)
    add_body_p(doc,
        "Basado en la óptica geométrica de semejanza de triángulos, si conocemos la altura física real promedio de una clase de objeto en el mundo real (H_real) y conocemos la distancia focal del sensor de la cámara en píxeles (f_y), la distancia métrica de profundidad (Z) se obtiene directamente a partir de la altura aparente del objeto en píxeles (h_bbox) medida por la red neuronal:")
    
    add_callout(doc,
        "Fórmula de Estimación de Profundidad Monocular:\n\n"
        "      Z = (f_y * H_real) / h_bbox\n"
        "      X = ((x_bbox_centro - c_x) * Z) / f_x\n\n"
        "Donde:\n"
        "• Z: Distancia métrica frontal en metros hacia el obstáculo o persona.\n"
        "• X: Desplazamiento métrico lateral en metros (+ derecha, - izquierda).\n"
        "• f_y, f_x: Longitud focal calibrada de la lente de la cámara en píxeles.\n"
        "• H_real: Altura de referencia física (ej. Persona de pie: 1.65m; Persona sentada: 1.20m; Silla: 0.85m; Cama: 0.65m).\n"
        "• h_bbox: Altura en píxeles del cuadro delimitador detectado por el modelo.",
        title="MODELO MATEMÁTICO PINHOLE")

    add_body_p(doc,
        "Gracias a esta relación geométrica simple pero rigurosa, el smartphone puede proyectar cualquier objeto detectado en un plano cartesiano 3D (X, Z) relativo al centro de gravedad del robot, sin consumir prácticamente tiempo de cálculo de la CPU.")

    # ==========================================
    # SECCIÓN 6: MOTOR DE NAVEGACIÓN JEV Y SEGURIDAD
    # ==========================================
    format_heading(doc, "6. Módulo de Decisión Reactiva y Navegación Jev (Iteración 3)", level=1)
    
    add_body_p(doc,
        "Con las coordenadas métricas (X, Z) de todos los obstáculos y personas en el entorno, el robot necesita decidir hacia dónde moverse. El módulo JevDecisionEngine.kt implementa el algoritmo de Campos de Potencial Artificial (Artificial Potential Fields - APF).")
        
    format_heading(doc, "6.1 Dinámica de Fuerzas: Atracción y Repulsión", level=2)
    add_body_p(doc,
        "El espacio navegable se modela como un campo de energía física imaginaria:")
    
    add_bullet_p(doc, "Si el robot tiene como meta acompañar a un anciano que camina por un pasillo, genera una fuerza atractiva proporcional a la distancia métrica hacia la persona. La meta atrae al robot suavemente.", "Fuerza Atractiva (Hacia el Objetivo): ")
    add_bullet_p(doc, "Cada obstáculo detectado (pared, silla de ruedas, camilla) genera una fuerza de rechazo repulsiva invisible. Esta fuerza es inversamente proporcional a la distancia: si el obstáculo está lejos, casi no influye; pero a medida que el robot se acerca, la fuerza repulsiva crece exponencialmente, obligando al robot a curvar su trayectoria.", "Fuerza Repulsiva (Frente a Obstáculos): ")
    add_bullet_p(doc, "El vector resultante de sumar todas las fuerzas define exactamente el ángulo de timón deseado (theta_cmd) y la velocidad recomendada (PWM_cmd).", "Vector Resultante de Navegación: ")

    format_heading(doc, "6.2 El Guardián Crítico: Supervisor de Seguridad (`SafetySupervisor.kt`)", level=2)
    add_body_p(doc,
        "Los algoritmos probabilísticos o de visión por computadora pueden sufrir oclusiones o retrasos esporádicos. En un entorno asistencial con ancianos frágiles, un choque accidental es inaceptable. Por ello, se implementó una capa de supervisión de seguridad independiente que tiene precedencia absoluta sobre el motor Jev:")
    
    headers_seguridad = ["Rango de Distancia (Z)", "Estado del Supervisor", "Acción Cinemática Ejecutada"]
    data_seguridad = [
        ["Z >= 1.50 metros", "ZONA DESPEJADA (SAFE)", "Velocidad de crucero normal (PWM 180 a 220) según el motor Jev."],
        ["0.80 m <= Z < 1.50 m", "ZONA DE PRECAUCIÓN (WARN)", "Reducción automática de potencia al 50% (PWM 100 a 130)."],
        ["0.40 m <= Z < 0.80 m", "ZONA CRÍTICA (CRITICAL)", "Velocidad mínima de maniobra (PWM 70 a 90) y giro evasivo forzado."],
        ["Z < 0.40 metros", "PARADA DE EMERGENCIA (EMERGENCY_STOP)", "ANULACIÓN TOTAL: Motores apagados de inmediato (PWM 0) y emisión de reversa preventiva."]
    ]
    create_table(doc, headers_seguridad, data_seguridad, [1.5, 2.0, 3.0])

    # ==========================================
    # SECCIÓN 7: COMUNICACIÓN SERIAL Y ARDUINO
    # ==========================================
    format_heading(doc, "7. Módulo de Actuación y Comunicación Serial USB-OTG", level=1)
    
    add_body_p(doc,
        "Este módulo es el puente físico que conecta el software de alto nivel con el chasis motriz.")
        
    format_heading(doc, "7.1 El Despachador Serial Android (`SerialDispatcher.kt`)", level=2)
    add_body_p(doc,
        "Mediante la biblioteca especializada usb-serial-for-android, la aplicación detecta el puerto USB del microcontrolador conectado por el cable OTG (soportando chips CH340, FTDI, CP2102 y CDC/ACM nativo):")
    
    add_bullet_p(doc, "115200 baudios, 8 bits de datos, 1 bit de parada, sin paridad (8-N-1).", "Parámetros de Enlace: ")
    add_bullet_p(doc, "Un bucle gobernado por corrutinas en Dispatchers.IO despacha tramas cinemáticas cada 100 milisegundos (10 Hz). Esta cadencia evita saturar el buffer UART del Arduino y asegura un control reactivo continuo.", "Frecuencia de Despacho (10 Hz): ")
    add_bullet_p(doc, "Formato simple y robusto delimitado por salto de línea: 'NAV:<angulo>,<pwm>\\n' (ejemplo: 'NAV:25,180\\n' para girar 25° a la derecha a velocidad media). Si el supervisor detecta peligro inminente, despacha la trama 'EMERGENCY_STOP\\n'.", "Protocolo de Tramas: ")

    format_heading(doc, "7.2 Firmware de Control en Arduino (`prueba_sara.ino`)", level=2)
    add_body_p(doc,
        "En la placa Arduino Uno corre el firmware que comanda físicamente los motores:")
    
    add_bullet_p(doc, "Gestiona las salidas in1, in2, in3, in4 para la dirección de las ruedas y enA, enB para la activación.", "Driver L298N de Doble Puente H: ")
    add_bullet_p(doc, "Al incluir la biblioteca <Servo.h> para el servomotor del sensor ultrasónico, el Timer 1 del ATmega328P queda secuestrado por el servo, lo que anula la capacidad PWM analógica tradicional (analogWrite) en el Pin 10 (enA). El código resolvió esto de forma brillante implementando activación digital directa a potencia calibrada, asegurando marcha simétrica sin conflictos de hardware.", "Resolución de Conflicto de Timers: ")
    add_bullet_p(doc, "El sensor ultrasónico HC-SR04 frontal (pines 11 y 12) realiza lecturas ultrasónicas continuas. Si detecta un objeto físico a menos de 25 cm, frena los motores a nivel de circuito independientemente de lo que ordene el teléfono.", "Redundancia Física de Ultrasonido: ")

    # ==========================================
    # SECCIÓN 8: INTERFACES Y TELEMETRÍA
    # ==========================================
    format_heading(doc, "8. Interfaces de Usuario y Telemetría Táctica", level=1)
    
    add_body_p(doc,
        "La aplicación incluye múltiples pantallas diseñadas tanto para el usuario final como para la ingeniería y diagnóstico en laboratorio:")
    
    add_bullet_p(doc, "Es la pantalla principal que ve el adulto mayor. Muestra únicamente el rostro animado reactivo a pantalla completa, sin menús distractores ni teclados.", "SaraScreen (Pantalla Principal de Asistencia): ")
    add_bullet_p(doc, "Muestra el flujo de video en vivo con la caja delimitadora (Bounding Box) en verde, las coordenadas en píxeles, y los ángulos de compensación calculados en tiempo real.", "FaceDetectionScreen (Visión y Seguimiento): ")
    add_bullet_p(doc, "Pantalla táctica con un radar polar concéntrico (0.5m, 1.0m, 2.0m), la posición proyectada de personas y obstáculos, el vector de fuerza resultante del motor Jev y el estado del supervisor de seguridad.", "NavigationScreen (HUD de Navegación): ")
    add_bullet_p(doc, "Consola flotante que almacena en memoria los últimos 100 eventos clasificados por nivel de severidad (DEBUG, INFO, WARN, ERROR), permitiendo diagnosticar tiempos de respuesta y tramas seriales sin conectar el celular a la PC.", "LogPanelDialog (Consola de Ingeniería): ")

    # ==========================================
    # SECCIÓN 9: PREGUNTAS DE EXAMEN / CAPSTONE
    # ==========================================
    format_heading(doc, "9. Guía de Preguntas Clave para la Comisión Evaluadora (Capstone)", level=1)
    
    add_body_p(doc,
        "A continuación se detallan las preguntas técnicas más rigurosas que la comisión docente suele formular en la defensa de título, junto con sus fundamentos de respuesta técnica sólida:")
        
    q_list = [
        ("¿Por qué no utilizaron WebSockets o WebRTC en lugar de migrar a una aplicación Android nativa?",
         "Porque en un entorno asistencial móvil, la latencia de ida y vuelta a un servidor proxy web introduce demoras inaceptables de hasta 6 segundos, además de que los navegadores web no tienen acceso nativo robusto a los puertos USB físicos del smartphone para comandar hardware mediante OTG. La solución nativa en Kotlin corre 100% en el dispositivo, garantiza cero saltos de red innecesarios y despacha órdenes motrices a 10 Hz por USB directo."),
         
        ("¿Cómo garantizan que la API Key de Gemini no sea vulnerada si alguien roba el teléfono?",
         "La credencial nunca se guarda en texto plano ni en SharedPreferences tradicionales. Se almacena mediante la librería de AndroidX Security 'EncryptedSharedPreferences', la cual delega la generación y almacenamiento de la clave criptográfica maestra al 'Android Keystore' del hardware del teléfono mediante cifrado simétrico AES-256 GCM."),
         
        ("¿Por qué usan una cámara monocular y la ecuación Pinhole en lugar de un sensor LIDAR o una cámara estéreo?",
         "Por balance de costos, peso y presupuesto de energía. Un sensor LIDAR de barrido 360° o una cámara estereoscópica duplican el costo total del robot y consumen energía excesiva de las baterías. La cámara monocular del smartphone, asistida por el modelo geométrico de Pinhole y las clases estándar de redes neuronales, permite derivar profundidad métrica en metros con un margen de error menor al 8%, suficiente para navegación en interiores asistenciales."),
         
        ("¿Qué sucede si el modelo de visión falla y no detecta un obstáculo a tiempo?",
         "El sistema cuenta con triple redundancia de seguridad: primero, el Supervisor de Software por visión; segundo, la parada de emergencia si Z < 0.40m; y tercero, una barrera física independiente por hardware: el sensor ultrasónico HC-SR04 conectado directamente a las interrupciones del microcontrolador Arduino, que detiene el puente H L298N de inmediato sin depender del sistema operativo del smartphone.")
    ]
    
    for i, (q, a) in enumerate(q_list, 1):
        add_body_p(doc, q, bold_prefix=f"Pregunta {i}: ")
        add_body_p(doc, a, italic_prefix="Respuesta Técnica: ")
        
    # Guardar documento
    output_path = r"A:\IU Sara\S.A.R.A\Fase 2\Interfaz Usuario SARA\iteraciones\Manual_Tecnico_Explicativo_SARA.docx"
    doc.save(output_path)
    print(f"Documento generado exitosamente en: {output_path}")

if __name__ == "__main__":
    generate_full_manual()

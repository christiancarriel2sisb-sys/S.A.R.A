# Módulo: Movimiento, Robótica y Hardware del Robot SARA

> **Evolución del Módulo:** De pruebas base de tracción y evasión (Etapa 1) a **Firmware con Telemetría, Protecciones de Seguridad y Calibración en Vivo vía USB-OTG (Etapa 2 y Validación Final)**.

---

## 🧭 Visión del Módulo

El objetivo de este módulo es dotar al robot SARA de movilidad autónoma segura, control motriz reactivo y supervisión física de hardware, operando de forma sincronizada con la aplicación Android nativa mediante comunicación serial USB-OTG a `115200 bps`.

El desarrollo de Robótica y Hardware se ha estructurado en tres etapas progresivas:

1. **Etapa 1 — Movimiento, Detección Visual, Seguimiento e Interacción:** Arranque de periféricos y canal serie con el microcontrolador a `115200 bps` (**CU-19**), control de motores de tracción para patrullaje y desplazamiento continuo con frenado de emergencia por sensores de proximidad (**CU-14**), recepción de órdenes de giro/avance para orientar y mover el chasis hacia las coordenadas del objetivo (**CU-18**) y salida/amplificación física del subsistema de audio/parlantes (**CU-2**).
2. **Etapa 2 — Monitoreo de Estado, Telemetría y Calibración:** Lectura en tiempo real del voltaje interno y lógica de corte/ahorro a motores al caer la batería a $\le 10\%$ (**CU-17**), firmware e interfaz técnica para calibrar en vivo la sensibilidad de los sensores de proximidad y límites de tracción (**CU-23**), y detección física por hardware/bus serie de desconexión o falla en cámara, placa de audio o actuadores (**CU-21**).
3. **Etapa 3 — Ajuste Fino Mecánico y Resiliencia de Bus:** Calibración de respuesta motriz en diferentes tipos de piso doméstico (cerámica, piso flotante y alfombra) y validación de desconexiones forzadas en caliente (*hot-plug*) de periféricos para comprobar resiliencia.

---

## 📁 Organización de Carpetas

```text
Movimiento Sara/
├── prueba_sara_v4.ino   # ⭐ CÓDIGO PRINCIPAL Y ACTIVO: Firmware completo en Arduino (C++)
└── README.md            # Documentación técnica, arquitectura de hardware, pinout y protocolo serial
```

---

## ⚡ Stack Tecnológico y Hardware Actual (Arduino Uno)

| Área | Tecnología / Componente | Función Principal |
| :--- | :--- | :--- |
| **Microcontrolador** | Arduino Uno (ATmega328P) | Ejecución en tiempo real del firmware de control y telemetría |
| **Lenguaje** | C++ (Arduino Core) | Lógica de bajo nivel, control de pines y lectura de registros internos |
| **Tracción Motriz** | Driver Puente H `L298N` + 2 Motores DC | Desplazamiento bidireccional, giros diferenciales y control PWM (**CU-14**, **CU-18**) |
| **Sensor de Proximidad** | Ultrasónico `HC-SR04` (4 pines) | Detección de obstáculos bajos y frenado/evasión reactiva (**CU-14**) |
| **Actuador de Barrido** | Servomotor + librería `<Servo.h>` | Escaneo horizontal ($140^\circ$, $90^\circ$, $40^\circ$) para maniobras de esquive |
| **Subsistema de Audio** | Salida/amplificación física | Soporte de salida de audio y parlantes integrado al chasis (**CU-2**) |
| **Seguridad Energética** | Registro `ADMUX` ($1.1\text{ V}$) + Trama `B<pct>` | Lectura de voltaje interno y bloqueo físico de motores con batería $\le 10\%$ (**CU-17**) |
| **Resiliencia de Bus** | *Watchdog* Serial (`3000 ms`) | Detención automática ante desconexión física del cable USB-OTG (**CU-21**) |
| **Diagnóstico Físico** | Validador de ecos `HC-SR04` | Detección de cable suelto o falla del sensor tras 5 lecturas inválidas (**CU-21**) |
| **Calibración en Vivo** | Comandos `D<cm>` y `T<pwm>` | Ajuste dinámico de distancia de seguridad y límite de tracción según piso (**CU-23**) |
| **Comunicación Serial** | Puerto Serie `115200 bps` (`8N1`) | Enlace USB-OTG con `SerialDispatcher.kt` (`usb-serial-for-android`) a $10\text{ Hz}$ (**CU-19**) |

---

## 🔌 Esquema de Conexiones (Pinout)

| Componente | Pines Físicos del Módulo | Pines Arduino Uno | Función |
| :--- | :--- | :--- | :--- |
| **Sensor Ultrasónico HC-SR04 (4 pines)** | `VCC` / `TRIG` / `ECHO` / `GND` | `5V` / Pin `11` / Pin `12` / `GND` | Alimentación, disparo de pulso y recepción de eco ultrasónico |
| **Puente H L298N (Motor Izquierdo)** | `ENA` / `IN1` / `IN2` | Pin `10` / Pin `9` / Pin `8` | Habilitación digital\* y sentido de giro izquierdo |
| **Puente H L298N (Motor Derecho)** | `ENB` / `IN3` / `IN4` | Pin `5` / Pin `7` / Pin `6` | Control de potencia PWM (`analogWrite`) y sentido de giro derecho |
| **Servomotor (Cuello, 3 pines)** | `VCC` / `Señal` / `GND` | `5V` / Pin `3` / `GND` | Alimentación y orientación de escaneo ($40^\circ$, $90^\circ$, $140^\circ$) |
| **Tierra Común** | `GND` | `GND` | Referencia común entre Arduino, L298N, HC-SR04, Servomotor y baterías |

> \*Nota técnica: `<Servo.h>` utiliza el `Timer1` de Arduino Uno, lo que deshabilita PWM en los pines `9` y `10`. Por ello el control PWM de tracción se aplica en el pin `5` (`enB`) y el pin `10` (`enA`) opera en modo digital. El driver `L298N` recibe alimentación externa de las baterías del chasis compartiendo `GND` común con la placa Arduino.

---

## 📡 Protocolo de Comunicación Serial (`115200 bps`)

### Comandos entrantes (Android → Arduino)

| Trama | Acción / Caso de Uso |
| :--- | :--- |
| `0` / `1` | Modo autónomo / Avanzar con evasión ultrasónica activa (**CU-14**, **CU-18**) |
| `2` / `3` | Giro izquierda / Giro derecha hacia el objetivo (**CU-18**) |
| `5` / `6` | Retroceso / Parada inmediata y freno de emergencia (**CU-14**) |
| `7` / `8` / `9` | Orientación del servomotor: izquierda ($140^\circ$), derecha ($40^\circ$) y centro ($90^\circ$) |
| `D<10-100>` | Calibra distancia de seguridad del sensor en centímetros, ej. `D30` (**CU-23**) |
| `T<0-255>` | Calibra límite máximo de tracción PWM, ej. `T180` (**CU-23**) |
| `B<0-100>` | Sincroniza porcentaje de batería; activa corte de motores si es $\le 10\%$ (**CU-17**) |

### Respuestas y alertas salientes (Arduino → Android)

* `SYS:SARA_READY` — Confirmación de arranque de periféricos y canal serie (**CU-19**).
* `ACK:CU23_DIST_CM=` / `ACK:CU23_TRAC_PWM=` — Confirmación de parámetros de proximidad o tracción calibrados (**CU-23**).
* `ERR:CU17_CORTE_MOTORES_BAT=` — Corte de motores activado con lectura de voltaje interno en `mV` (**CU-17**).
* `ERR:CU21_FALLA_SENSOR_PROXIMIDAD` — Alerta física por cable suelto o falla del sensor `HC-SR04` (**CU-21**).
* `ERR:CU21_DESCONEXION_BUS_SERIE_ACTUADORES` — Alerta de *watchdog* por pérdida de señal USB-OTG (**CU-21**).

---

## 🚀 Cómo Empezar con la Versión Actual

Para cargar y operar el firmware oficial del chasis de SARA:

1. Abre el archivo `prueba_sara_v4.ino` en **Arduino IDE**.
2. Selecciona la placa `Arduino Uno` y el puerto `COM` correspondiente, luego presiona **Subir** (no requiere librerías externas adicionales a `<Servo.h>`).
3. *(Opcional)* Verifica el arranque abriendo el **Monitor Serie** a `115200 bps` hasta visualizar `SYS:SARA_READY`.
4. Desconecta el Arduino del PC, conéctalo mediante cable **USB-OTG** al teléfono con la aplicación de `../Interfaz Usuario SARA/android/` y acepta el permiso USB en pantalla.

---

© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
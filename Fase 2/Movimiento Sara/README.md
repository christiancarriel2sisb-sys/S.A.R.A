# Módulo: Movimiento, Actuadores y Hardware del Robot SARA

> **Evolución del Módulo:** De pruebas iniciales de tracción básica (`prueba_sara.ino`) a **Firmware Reactivo Integrado con Android vía USB-OTG (`prueba_sara_v4.ino`)**.

---

## 🧭 Visión del Módulo

El objetivo de este módulo es dotar al chasis físico del robot SARA de un control motriz seguro, fluido y resiliente para entornos domésticos, integrando evasión reactiva de obstáculos bajos, supervisión energética y diagnóstico de hardware en tiempo real.

El módulo ha pasado por dos grandes etapas arquitectónicas:

1. **Etapa 1 (Control Motriz Base y Evasión Inicial):** Implementación del control de motores DC mediante puente H L298N, barrido de servomotor y lectura básica del sensor ultrasónico HC-SR04.
2. **Etapa 2 (Integración Nativa USB-OTG y Casos de Uso de Hardware):** Sincronización bidireccional a `115200 bps` con `SerialDispatcher.kt` en Android, incorporando corte de motores por batería crítica (**CU-17**), detección física de fallas y *watchdog* de bus serie (**CU-21**) y calibración dinámica de proximidad y tracción (**CU-23**).

---

## 📁 Organización de Carpetas

```text
Movimiento Sara/
├── prueba_sara_v4.ino   # ⭐ CÓDIGO PRINCIPAL Y ACTIVO: Firmware Arduino Uno (Tracción, Evasión, CU-17, CU-21 y CU-23)
└── README.md            # Documentación técnica, arquitectura de hardware, pinout y protocolo serial
```

---

## ⚡ Stack Tecnológico y Hardware Actual (Arduino Uno)

| Área | Tecnología / Componente | Función Principal |
| :--- | :--- | :--- |
| **Microcontrolador** | Arduino Uno (ATmega328P) | Ejecución en tiempo real del firmware de control y telemetría |
| **Lenguaje** | C++ (Arduino Core) | Lógica de bajo nivel, control de pines y lectura de registros internos |
| **Tracción Motriz** | Driver Puente H `L298N` + 2 Motores DC | Desplazamiento bidireccional, giros diferenciales y control PWM |
| **Sensor de Proximidad** | Ultrasónico `HC-SR04` | Medición de obstáculos bajos y evasión reactiva en tiempo real |
| **Actuador de Barrido** | Servomotor + librería `Servo.h` | Escaneo horizontal (izquierda, centro, derecha) al detectar obstáculos |
| **Seguridad Energética** | Registro `ADMUX` + Trama `B<pct>` (**CU-17**) | Lectura de voltaje interno y bloqueo de motores al caer la batería a $\le 10\%$ |
| **Resiliencia de Bus** | *Watchdog* Serial `3000 ms` (**CU-21**) | Detención automática de seguridad ante desconexión física del cable USB |
| **Diagnóstico Físico** | Validador de ecos `HC-SR04` (**CU-21**) | Detección de cable suelto o falla del sensor tras 5 lecturas inválidas |
| **Calibración en Vivo** | Comandos `D<cm>` y `T<pwm>` (**CU-23**) | Ajuste dinámico de distancia de seguridad y límite de tracción según tipo de piso |
| **Comunicación Serial** | Puerto Serie `115200 bps` (`8N1`) | Enlace USB-OTG con `SerialDispatcher.kt` (`usb-serial-for-android`) a 10 Hz |

---

## 🔌 Esquema de Conexiones (Pinout)

| Componente | Pin del Módulo | Pin Arduino | Función |
| :--- | :--- | :--- | :--- |
| **Sensor HC-SR04** | `TRIG` / `ECHO` | `11` / `12` | Disparo de pulso y recepción de eco ultrasónico |
| **Motor Izquierdo (L298N)** | `ENA` / `IN1` / `IN2` | `10` / `9` / `8` | Habilitación digital* y sentido de giro izquierdo |
| **Motor Derecho (L298N)** | `ENB` / `IN3` / `IN4` | `5` / `7` / `6` | Control de potencia PWM (`analogWrite`) y sentido derecho |
| **Servomotor** | `Señal` | `3` | Orientación de escaneo (`40°`, `90°`, `140°`) |

> *Nota técnica:* `Servo.h` utiliza el `Timer1` de Arduino Uno, deshabilitando PWM en los pines `9` y `10`. Por ello el control PWM de tracción se aplica en el pin `5` (`enB`) y el pin `10` (`enA`) opera en modo digital.

---

## 📡 Protocolo de Comunicación Serial

| Trama | Dirección | Función |
| :--- | :--- | :--- |
| `0`, `1`, `2`, `3`, `5`, `6` | Android $\rightarrow$ Arduino | Comandos motrices: avanzar (`0`/`1`), izquierda (`2`), derecha (`3`), retroceder (`5`) y detener (`6`) |
| `7`, `8`, `9` | Android $\rightarrow$ Arduino | Posición del servomotor: izquierda (`7`), derecha (`8`) y centro (`9`) |
| `D<10-100>` / `T<0-255>` | Android $\rightarrow$ Arduino | Calibración de distancia de seguridad en cm y límite de tracción PWM (**CU-23**) |
| `B<0-100>` | Android $\rightarrow$ Arduino | Sincronización de porcentaje de batería; activa bloqueo de motores si es $\le 10\%$ (**CU-17**) |
| `SYS:SARA_READY` | Arduino $\rightarrow$ Android | Confirmación de inicialización de periféricos (**CU-19**) |
| `ACK:CU23_*` | Arduino $\rightarrow$ Android | Confirmación de parámetros de proximidad o tracción calibrados |
| `ERR:CU17_*` / `ERR:CU21_*` | Arduino $\rightarrow$ Android | Alertas de corte por batería crítica, falla de sensor ultrasónico o desconexión de bus serie |

---

## 🚀 Cómo Empezar con la Versión Actual

Para cargar y operar el firmware oficial del chasis de SARA:

1. Abre el archivo `prueba_sara_v4.ino` en **Arduino IDE**.
2. Selecciona la placa `Arduino Uno` y el puerto `COM` correspondiente, luego presiona **Subir**.
3. *(Opcional)* Verifica el arranque abriendo el **Monitor Serie** a `115200 bps` hasta recibir `SYS:SARA_READY`.
4. Desconecta el Arduino del PC y conéctalo mediante cable **USB-OTG** al teléfono con la app ubicada en `../Interfaz Usuario SARA/android/`.

---

© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
#include <Servo.h>

// PINES FÍSICOS DE SARA
#define TRIG_PIN 11
#define ECHO_PIN 12
#define enA 10
#define in1 9
#define in2 8
#define in3 7
#define in4 6
#define enB 5
#define servoPin 3 

// CAMBIO ETAPA 2 - CU-23: VARIABLES DE CALIBRACIÓN DE SENSORES Y TRACCIÓN
// distanciaSeguridadCm: Calibra la sensibilidad del sensor ultrasónico (comando 'D').
// limiteTraccionPwm: Calibra el límite de potencia de tracción (comando 'T').
int distanciaSeguridadCm = 20; 
int limiteTraccionPwm = 255;   
const int TIEMPO_GIRO_ESQUIVE = 280; 
const int TIEMPO_RETROCESO = 250;

// CAMBIO ETAPA 2 - CU-17: VARIABLES DE BATERÍA Y CORTE A MOTORES (<= 10%)
// porcentajeBateria: Almacena el % actual sincronizado por el bus serie.
// corteBateriaBaja: Cuando es true (batería <= 10%), apaga y bloquea los motores.
int porcentajeBateria = 100;
bool corteBateriaBaja = false; 

// =========================================================================
// CAMBIO ETAPA 2 - CU-21: VARIABLES DE DETECCIÓN FÍSICA DE FALLAS
// lecturasInvalidasSensor: Cuenta fallos seguidos del pin ECHO para detectar cable suelto.
// ultimoLatidoSerialMs: Registra la última vez que hubo comunicación por el cable USB.
int lecturasInvalidasSensor = 0;
bool fallaSensorUltrasonico = false;
unsigned long ultimoLatidoSerialMs = 0;
const unsigned long TIMEOUT_BUS_SERIE_MS = 3000; // 3 segundos máximo sin señal USB
bool fallaBusSerieReportada = false;

// Estado actual ordenado por la App Android ('6' = Detenido por defecto)
char estadoActual = '6'; 
unsigned long ultimaMedicionMs = 0;

// CLASE MOTOR (Actualizada con corte CU-17 y límite de tracción CU-23)
class Motor {
    int enablePin;
    int directionPin1;
    int directionPin2;

  public:
    Motor(int ENPin, int dPin1, int dPin2) {
      enablePin = ENPin;
      directionPin1 = dPin1;
      directionPin2 = dPin2;
    }

    void init() {
      pinMode(enablePin, OUTPUT);
      pinMode(directionPin1, OUTPUT);
      pinMode(directionPin2, OUTPUT);
      stop();
    }

    //CAMBIO ETAPA 2 - CU-23: Aplica el límite de tracción calibrado a los motores.
    // Nota técnica: Servo.h usa el Timer1 (inhabilita PWM en pines 9 y 10 de Arduino Uno).
    // Por eso en pin 5 (enB) se aplica analogWrite PWM y en pin 10 (enA) digitalWrite.
    void aplicarTraccionCalibrada() {
      if (enablePin == 10) {
        digitalWrite(enablePin, (limiteTraccionPwm > 0) ? HIGH : LOW);
      } else {
        analogWrite(enablePin, limiteTraccionPwm);
      }
    }

    void forward() {
      //CAMBIO ETAPA 2 - CU-17: Si la batería cayó a <= 10%, corta la energía a los motores
      if (corteBateriaBaja) {
        stop();
        return;
      }
      digitalWrite(directionPin1, LOW);
      digitalWrite(directionPin2, HIGH);
      aplicarTraccionCalibrada(); // [CU-23]
    }

    void backward() {
      //CAMBIO ETAPA 2 - CU-17: Si la batería cayó a <= 10%, corta la energía a los motores
      if (corteBateriaBaja) {
        stop();
        return;
      }
      digitalWrite(directionPin1, HIGH);
      digitalWrite(directionPin2, LOW);
      aplicarTraccionCalibrada(); // [CU-23]
    }

    void stop() {
      digitalWrite(directionPin1, LOW);
      digitalWrite(directionPin2, LOW);
      digitalWrite(enablePin, LOW);
    }
};

Motor leftMotor = Motor(enA, in1, in2);
Motor rightMotor = Motor(enB, in3, in4);
Servo myservo;

void setup() {
  // Velocidad 115200 bps sincronizada con SerialDispatcher.kt
  Serial.begin(115200);
  Serial.setTimeout(15);

  pinMode(TRIG_PIN, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  pinMode(ECHO_PIN, INPUT);

  myservo.attach(servoPin);
  myservo.write(90); // Mirar al frente al iniciar

  leftMotor.init();
  rightMotor.init();

  ultimoLatidoSerialMs = millis(); //CU-21: Inicia reloj de supervisión del bus serie
  Serial.println("SYS:SARA_READY");
}

// CAMBIO ETAPA 2 - CU-17: LECTURA DE VOLTAJE INTERNO DEL MICROCONTROLADOR
// Mide el voltaje real de la placa Arduino (mV) usando la referencia interna de 1.1V
// sin necesidad de cables externos en los pines analógicos.
long leerVoltajePlacaMv() {
  ADMUX = _BV(REFS0) | _BV(MUX3) | _BV(MUX2) | _BV(MUX1);
  delay(2);
  ADCSRA |= _BV(ADSC);
  while (bit_is_set(ADCSRA, ADSC));
  long adc = ADCL;
  adc |= ADCH << 8;
  if (adc == 0) return 5000;
  return 1125300L / adc;
}

// MEDICIÓN ULTRASÓNICA + CAMBIO ETAPA 2 - CU-21: DETECCIÓN DE FALLA EN SENSOR
long medirDistanciaCm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  unsigned long tInicio = micros();
  long duracion = pulseIn(ECHO_PIN, HIGH, 15000);
  unsigned long tTranscurrido = micros() - tInicio;

  //CAMBIO ETAPA 2 - CU-21: Si pulseIn devuelve 0 casi al instante (< 500 us),
  // significa que el cable ECHO/TRIG está desconectado o el sensor ultrasónico falló.
  // Al acumular 5 lecturas fallidas seguidas, envía alerta de hardware una sola vez.
  if (duracion == 0 && tTranscurrido < 500) {
    lecturasInvalidasSensor++;
    if (lecturasInvalidasSensor >= 5 && !fallaSensorUltrasonico) {
      fallaSensorUltrasonico = true;
      Serial.println("ERR:CU21_FALLA_SENSOR_PROXIMIDAD");
    }
    return 999;
  }

  // Si el sensor respondió bien, limpia el estado de falla
  lecturasInvalidasSensor = 0;
  fallaSensorUltrasonico = false;

  if (duracion == 0) return 999; // Camino despejado (> 2.5 metros)
  return duracion / 58;
}

// GESTO DE ESCANEO CON SERVOMOTOR 
void miraIzqDer() {
  myservo.write(140);
  delay(250);
  myservo.write(40);
  delay(250);
  myservo.write(90);
}

// EVASIÓN REACTIVA DE OBSTÁCULOS BAJOS (Protegida con corte de batería CU-17)
void esquivarObstaculoInferior() {
  // 1. Frenar de inmediato
  leftMotor.stop();
  rightMotor.stop();
  delay(150);

  // 2. Escanear izquierda con el servomotor
  myservo.write(140);
  delay(300);
  long distIzq = medirDistanciaCm();

  // 3. Escanear derecha con el servomotor
  myservo.write(40);
  delay(350);
  long distDer = medirDistanciaCm();

  // 4. Volver cabeza al frente
  myservo.write(90);
  delay(150);

  // Verificar si durante el escaneo la app Android envió '6' (Parada de emergencia)
  if (Serial.available() > 0 && Serial.peek() == '6') {
    return; 
  }

  //CAMBIO ETAPA 2 - CU-17: Si hay corte por batería <= 10%, no mover las ruedas
  if (corteBateriaBaja) {
    leftMotor.stop();
    rightMotor.stop();
    return;
  }

  // 5. Retroceso de seguridad y giro hacia el lado más despejado
  leftMotor.backward();
  rightMotor.backward();
  delay(TIEMPO_RETROCESO);

  if (distIzq >= distDer) {
    leftMotor.backward();
    rightMotor.forward();
    delay(TIEMPO_GIRO_ESQUIVE);
  } else {
    leftMotor.forward();
    rightMotor.backward();
    delay(TIEMPO_GIRO_ESQUIVE);
  }

  // 6. Retomar el estado de avance si no hay corte de batería
  if ((estadoActual == '1' || estadoActual == '0') && !corteBateriaBaja) {
    leftMotor.forward();
    rightMotor.forward();
  } else {
    leftMotor.stop();
    rightMotor.stop();
  }
}

//CAMBIO ETAPA 2 - CU-23 y CU-17: PROCESADOR DE COMANDOS DE HARDWARE
// - 'D' + valor (ej: "D30\n"): Calibra sensibilidad de proximidad a 30 cm (CU-23).
// - 'T' + valor (ej: "T180\n"): Calibra límite de tracción PWM a 180 (CU-23).
// - 'B' + valor (ej: "B10\n"): Recibe % de batería y corta motores si es <= 10% (CU-17).
void procesarComandoHardware(char tipo) {
  int valor = Serial.parseInt();

  if (tipo == 'D') {
    //CU-23: Calibración de sensibilidad de sensor ultrasónico (10 cm a 100 cm)
    distanciaSeguridadCm = constrain(valor, 10, 100);
    Serial.print("ACK:CU23_DIST_CM=");
    Serial.println(distanciaSeguridadCm);
  } 
  else if (tipo == 'T') {
    //CU-23: Calibración de límite de tracción de motores (0 a 255 PWM)
    limiteTraccionPwm = constrain(valor, 0, 255);
    Serial.print("ACK:CU23_TRAC_PWM=");
    Serial.println(limiteTraccionPwm);
  } 
  else if (tipo == 'B') {
    //CU-17: Evaluación de nivel de batería y corte de motores al caer a <= 10%
    porcentajeBateria = constrain(valor, 0, 100);
    if (porcentajeBateria <= 10) {
      corteBateriaBaja = true;
      estadoActual = '6';
      leftMotor.stop();
      rightMotor.stop();
      Serial.print("ERR:CU17_CORTE_MOTORES_BAT=");
      Serial.print(porcentajeBateria);
      Serial.print("%,MV=");
      Serial.println(leerVoltajePlacaMv());
    } else {
      corteBateriaBaja = false;
    }
  }
}

void loop() {
  // 1. RECEPCIÓN DE COMANDOS DESDE ANDROID (Protocolo 1-9 + Comandos D, T, B)
  if (Serial.available() > 0) {
    char comando = Serial.read();

    //CAMBIO ETAPA 2 - CU-21: Cada byte recibido confirma que el bus serie USB sigue vivo
    ultimoLatidoSerialMs = millis();
    fallaBusSerieReportada = false;

    if (comando != '\n' && comando != '\r' && comando != ' ') {
      switch (comando) {
        case '0': // Modo autónomo
        case '1': // Avanzar 
          if (corteBateriaBaja) {
            leftMotor.stop();
            rightMotor.stop();
          } else {
            estadoActual = comando;
            myservo.write(90);
            leftMotor.forward();
            rightMotor.forward();
          }
          break;

        case '2': // Giro Izquierda
          if (!corteBateriaBaja) {
            estadoActual = '2';
            leftMotor.backward();
            rightMotor.forward();
          }
          break;

        case '3': // Giro Derecha
          if (!corteBateriaBaja) {
            estadoActual = '3';
            leftMotor.forward();
            rightMotor.backward();
          }
          break;

        case '5': // Retroceso
          if (!corteBateriaBaja) {
            estadoActual = '5';
            leftMotor.backward();
            rightMotor.backward();
          }
          break;

        case '6': // Parada inmediata/freno de emergencia
          estadoActual = '6';
          leftMotor.stop();
          rightMotor.stop();
          break;

        case '7': // Orientar cuello a la izquierda
          myservo.write(140);
          break;

        case '8': // Orientar cuello a la derecha
          myservo.write(40);
          break;

        case '9': // Centrar cuello
          myservo.write(90);
          break;

        //CAMBIO ETAPA 2: Comandos de calibración ('D', 'T') y batería ('B') (CU-23 y CU-17)
        case 'D':
        case 'T':
        case 'B':
          procesarComandoHardware(comando);
          break;
      }
    }
  }
  // CAMBIO ETAPA 2 CU-21: DETECCIÓN FÍSICA DE DESCONEXIÓN DEL BUS SERIE
  // Si el robot se estaba moviendo y deja de recibir tramas del celular por más de
  // 3 segundos (cable desconectado), detiene los actuadores por seguridad.
  if (estadoActual != '6' && (millis() - ultimoLatidoSerialMs > TIMEOUT_BUS_SERIE_MS)) {
    estadoActual = '6';
    leftMotor.stop();
    rightMotor.stop();
    if (!fallaBusSerieReportada) {
      fallaBusSerieReportada = true;
      Serial.println("ERR:CU21_DESCONEXION_BUS_SERIE_ACTUADORES");
    }
  }
  // 2. SUPERVISIÓN FÍSICA CONTINUA (Usa distanciaSeguridadCm calibrada en CU-23)
  if ((estadoActual == '1' || estadoActual == '0') && !corteBateriaBaja && (millis() - ultimaMedicionMs >= 80)) {
    ultimaMedicionMs = millis();
    long cm = medirDistanciaCm();

    if (cm <= distanciaSeguridadCm) {
      esquivarObstaculoInferior();
    }
  }
}
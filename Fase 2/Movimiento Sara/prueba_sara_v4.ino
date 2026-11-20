#include <Servo.h>

/*
 * Firmware de control motriz y seguridad - Robot SARA (115200 bps por USB-OTG)
 *
 * Comandos seriales aceptados:
 *   0 / 1 : Avanzar (con freno anticolisión activo)
 *   2 / 3 : Girar sobre su eje (Izquierda / Derecha)
 *   4     : Rutina de calibración y esquive (barre servo, mide izq/der y maniobra)
 *   5     : Retroceder
 *   6     : Parada de emergencia / Detener (estado por defecto)
 *   7/8/9 : Orientar cuello/servo (140° izq / 40° der / 90° centro)
 *   D<n>  : Calibrar distancia de freno ultrasónico (10-100 cm)
 *   T<n>  : Calibrar límite de tracción PWM (0-255)
 *   B<n>  : Informar % de batería (<= 10% bloquea motores)
 */

// Mapeo físico de pines (HC-SR04, Puente H L298N y microservo)
#define TRIG_PIN 11
#define ECHO_PIN 12
#define enA 10
#define in1 9
#define in2 8
#define in3 7
#define in4 6
#define enB 5
#define servoPin 3

// Parámetros de calibración y tiempos de esquive (ms)
int distanciaSeguridadCm = 20;
int limiteTraccionPwm = 255;
const int TIEMPO_GIRO_ESQUIVE = 280;
const int TIEMPO_RETROCESO = 250;

// Corte de seguridad por batería crítica (CU-17)
int porcentajeBateria = 100;
bool corteBateriaBaja = false;

// Supervisión de fallas de hardware y desconexión USB (CU-21)
int lecturasInvalidasSensor = 0;
bool fallaSensorUltrasonico = false;
unsigned long ultimoLatidoSerialMs = 0;
const unsigned long TIMEOUT_BUS_SERIE_MS = 3000; // Frena si pasa 3 s sin recibir señal USB
bool fallaBusSerieReportada = false;

char estadoActual = '6';
unsigned long ultimaMedicionMs = 0;

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

    // Servo.h usa Timer1 e inhabilita PWM en pines 9 y 10 del Arduino Uno;
    // por eso enA (pin 10) usa señal digital y enB (pin 5) aplica PWM.
    void aplicarTraccionCalibrada() {
      if (enablePin == 10) {
        digitalWrite(enablePin, (limiteTraccionPwm > 0) ? HIGH : LOW);
      } else {
        analogWrite(enablePin, limiteTraccionPwm);
      }
    }

    void forward() {
      if (corteBateriaBaja) {
        stop();
        return;
      }
      digitalWrite(directionPin1, LOW);
      digitalWrite(directionPin2, HIGH);
      aplicarTraccionCalibrada();
    }

    void backward() {
      if (corteBateriaBaja) {
        stop();
        return;
      }
      digitalWrite(directionPin1, HIGH);
      digitalWrite(directionPin2, LOW);
      aplicarTraccionCalibrada();
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

// Mueve el servo y libera el pin con detach() para evitar zumbidos y consumo en reposo
void moverServo(int angulo) {
  myservo.attach(servoPin);
  myservo.write(angulo);
  delay(250);
  myservo.detach();
}

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(15);

  pinMode(TRIG_PIN, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  pinMode(ECHO_PIN, INPUT);

  moverServo(90);

  leftMotor.init();
  rightMotor.init();

  ultimoLatidoSerialMs = millis();
  Serial.println("SYS:SARA_READY");
}

// Estima el voltaje interno de la placa (mV) usando la referencia de 1.1 V del ATmega328P
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

// Mide distancia en cm (999 = camino libre). Reporta falla si ECHO falla 5 veces seguidas
long medirDistanciaCm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  unsigned long tInicio = micros();
  long duracion = pulseIn(ECHO_PIN, HIGH, 15000);
  unsigned long tTranscurrido = micros() - tInicio;

  if (duracion == 0 && tTranscurrido < 500) {
    lecturasInvalidasSensor++;
    if (lecturasInvalidasSensor >= 5 && !fallaSensorUltrasonico) {
      fallaSensorUltrasonico = true;
      Serial.println("ERR:CU21_FALLA_SENSOR_PROXIMIDAD");
    }
    return 999;
  }

  lecturasInvalidasSensor = 0;
  fallaSensorUltrasonico = false;

  if (duracion == 0) return 999;
  return duracion / 58;
}

void miraIzqDer() {
  myservo.attach(servoPin);
  myservo.write(140);
  delay(250);
  myservo.write(40);
  delay(250);
  myservo.write(90);
  delay(200);
  myservo.detach();
}

// Rutina del comando '4': escanea izq/der, imprime lecturas y gira hacia el lado más despejado
void esquivarObstaculoInferior() {
  leftMotor.stop();
  rightMotor.stop();
  delay(150);

  myservo.attach(servoPin);
  myservo.write(140);
  delay(300);
  long distIzq = medirDistanciaCm();
  Serial.print("CAL_IZQ_CM=");
  Serial.println(distIzq);

  myservo.write(40);
  delay(350);
  long distDer = medirDistanciaCm();
  Serial.print("CAL_DER_CM=");
  Serial.println(distDer);

  myservo.write(90);
  delay(150);
  myservo.detach();

  if (Serial.available() > 0 && Serial.peek() == '6') {
    return;
  }

  if (corteBateriaBaja) {
    leftMotor.stop();
    rightMotor.stop();
    return;
  }

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

  if ((estadoActual == '1' || estadoActual == '0') && !corteBateriaBaja) {
    leftMotor.forward();
    rightMotor.forward();
  } else {
    leftMotor.stop();
    rightMotor.stop();
  }
}

// Procesa parámetros dinámicos enviados por Android: 'D' (distancia), 'T' (PWM) y 'B' (batería)
void procesarComandoHardware(char tipo) {
  int valor = Serial.parseInt();

  if (tipo == 'D') {
    distanciaSeguridadCm = constrain(valor, 10, 100);
    Serial.print("ACK:CU23_DIST_CM=");
    Serial.println(distanciaSeguridadCm);
  }
  else if (tipo == 'T') {
    limiteTraccionPwm = constrain(valor, 0, 255);
    Serial.print("ACK:CU23_TRAC_PWM=");
    Serial.println(limiteTraccionPwm);
  }
  else if (tipo == 'B') {
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
  if (Serial.available() > 0) {
    char comando = Serial.read();

    ultimoLatidoSerialMs = millis();
    fallaBusSerieReportada = false;

    if (comando != '\n' && comando != '\r' && comando != ' ') {
      switch (comando) {
        case '0': // Autónomo
        case '1': // Avanzar
          if (corteBateriaBaja) {
            leftMotor.stop();
            rightMotor.stop();
          } else {
            estadoActual = comando;
            leftMotor.forward();
            rightMotor.forward();
          }
          break;

        case '2': // Giro izquierda
          if (!corteBateriaBaja) {
            estadoActual = '2';
            leftMotor.backward();
            rightMotor.forward();
          }
          break;

        case '3': // Giro derecha
          if (!corteBateriaBaja) {
            estadoActual = '3';
            leftMotor.forward();
            rightMotor.backward();
          }
          break;

        case '4': // Calibración y esquive
          esquivarObstaculoInferior();
          ultimoLatidoSerialMs = millis();
          break;

        case '5': // Retroceder
          if (!corteBateriaBaja) {
            estadoActual = '5';
            leftMotor.backward();
            rightMotor.backward();
          }
          break;

        case '6': // Parada de emergencia
          estadoActual = '6';
          leftMotor.stop();
          rightMotor.stop();
          break;

        case '7': // Servo izquierda (140°)
          moverServo(140);
          break;

        case '8': // Servo derecha (40°)
          moverServo(40);
          break;

        case '9': // Servo centro (90°)
          moverServo(90);
          break;

        case 'D':
        case 'T':
        case 'B':
          procesarComandoHardware(comando);
          break;
      }
    }
  }

  // Parada de seguridad si se pierde comunicación USB durante más de 3 s
  if (estadoActual != '6' && (millis() - ultimoLatidoSerialMs > TIMEOUT_BUS_SERIE_MS)) {
    estadoActual = '6';
    leftMotor.stop();
    rightMotor.stop();
    if (!fallaBusSerieReportada) {
      fallaBusSerieReportada = true;
      Serial.println("ERR:CU21_DESCONEXION_BUS_SERIE_ACTUADORES");
    }
  }

  // Freno anticolisión cada 80 ms mientras avanza
  if ((estadoActual == '1' || estadoActual == '0') && !corteBateriaBaja && (millis() - ultimaMedicionMs >= 80)) {
    ultimaMedicionMs = millis();
    long cm = medirDistanciaCm();

    if (cm > 0 && cm <= distanciaSeguridadCm) {
      estadoActual = '6';
      leftMotor.stop();
      rightMotor.stop();
      Serial.print("ACK:OBS_FRENO_CM=");
      Serial.println(cm);
    }
  }
}
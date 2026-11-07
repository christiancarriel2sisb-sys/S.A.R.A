#include <Servo.h>

// PINES FÍSICOS REALES DE SARA
#define TRIG_PIN 11
#define ECHO_PIN 12
#define enA 10
#define in1 9
#define in2 8
#define in3 7
#define in4 6
#define enB 5

#define servoPin 3 

const int DISTANCIA_SEGURIDAD_CM = 20; 
const int TIEMPO_GIRO_ESQUIVE = 280; 
const int TIEMPO_RETROCESO = 250;

// ==========================================
// CLASE MOTOR (Compatible con Servo.h en Timer1)
// ==========================================
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

    void forward() {
      digitalWrite(directionPin1, LOW);
      digitalWrite(directionPin2, HIGH);
      digitalWrite(enablePin, HIGH);
    }

    void backward() {
      digitalWrite(directionPin1, HIGH);
      digitalWrite(directionPin2, LOW);
      digitalWrite(enablePin, HIGH);
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

// Estado actual ordenado por la App Android (Jev / SafetySupervisor)
char estadoActual = '6'; 
unsigned long ultimaMedicionMs = 0;

void setup() {
  // 115200 bps estandarizado en Iteración 3 y 5 (SerialDispatcher.kt)
  Serial.begin(115200);
  Serial.setTimeout(10);

  pinMode(TRIG_PIN, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  pinMode(ECHO_PIN, INPUT);

  myservo.attach(servoPin);
  myservo.write(90); // Mirar al frente al iniciar

  leftMotor.init();
  rightMotor.init();

  Serial.println("SARA_READY");
}

// ==========================================
// MEDICIÓN ULTRASÓNICA SIN BLOQUEO
// ==========================================
long medirDistanciaCm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  // Timeout corto (15ms) para responder al instante si la app envía '6' (Stop)
  long duracion = pulseIn(ECHO_PIN, HIGH, 15000);
  if (duracion == 0) return 999; 
  return duracion / 58;
}

// ==========================================
// GESTO DE ESCANEO (Requerimiento código base)
// ==========================================
void miraIzqDer() {
  myservo.write(140);
  delay(250);
  myservo.write(40);
  delay(250);
  myservo.write(90);
}

// ==========================================
// CU: EVASIÓN REACTIVA DE OBSTÁCULOS BAJOS
// ==========================================
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

  // 5. Retroceso de seguridad y giro hacia el lado despejado
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

  // 6. Retomar el estado de avance que tenía la app Android
  if (estadoActual == '1' || estadoActual == '0') {
    leftMotor.forward();
    rightMotor.forward();
  } else {
    leftMotor.stop();
    rightMotor.stop();
  }
}

void loop() {
  // 1. RECEPCIÓN DE COMANDOS DESDE ANDROID (Protocolo 1-6 Iteración 5)
  if (Serial.available() > 0) {
    char comando = Serial.read();

    if (comando != '\n' && comando != '\r' && comando != ' ') {
      switch (comando) {
        case '0': // Modo autónomo de prueba o saludo con cabeza
          estadoActual = '0';
          myservo.write(90);
          leftMotor.forward();
          rightMotor.forward();
          break;

        case '1': // CU: Avanzar hacia el objetivo (JevDecisionEngine)
          estadoActual = '1';
          myservo.write(90);
          leftMotor.forward();
          rightMotor.forward();
          break;

        case '2': // CU: Corregir trayectoria a la Izquierda
          estadoActual = '2';
          leftMotor.backward();
          rightMotor.forward();
          break;

        case '3': // CU: Corregir trayectoria a la Derecha
          estadoActual = '3';
          leftMotor.forward();
          rightMotor.backward();
          break;

        case '5': // CU: Retroceso de seguridad (SafetySupervisor)
          estadoActual = '5';
          leftMotor.backward();
          rightMotor.backward();
          break;

        case '6': // CU: Parada inmediata (SafetySupervisor / Idle)
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
      }
    }
  }

  // 2. SUPERVISIÓN FÍSICA CONTINUA (Solo cuando el robot se desplaza hacia adelante)
  if ((estadoActual == '1' || estadoActual == '0') && (millis() - ultimaMedicionMs >= 80)) {
    ultimaMedicionMs = millis();
    long cm = medirDistanciaCm();

    if (cm <= DISTANCIA_SEGURIDAD_CM) {
      esquivarObstaculoInferior();
    }
  }
}
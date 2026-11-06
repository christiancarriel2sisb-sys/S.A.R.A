#include <Servo.h>

// ==========================================
// PINES REALES DE SARA
// ==========================================
#define TRIG_PIN 11
#define ECHO_PIN 12

#define enA 10
#define in1 9
#define in2 8
#define in3 7
#define in4 6
#define enB 5

#define servoPin 3 

//distancia en cm para que el robot frene antes de chocar, antes lo tenia en 20 pero decidi dejarlo en 15 pq si
const int DISTANCIA_SEGURA = 15; 

//tiempo en milisegundos para que los giros no sean tan bruscos como dijo christian
const int TIEMPO_GIRO = 260; 

// CLASE MOTOR (CON VELOCIDAD MODERADA)
// Al principio pense en usar analogWrite para la velocidad, pero al agregar
// la libreria Servo.h el pin 10 (enA) fallo y la rueda izquierda no avanzaba.
// Para arreglarlo sin tocar cables, deje la potencia fija con digitalWrite
// y regule la fuerza con pulsos cortos (softStep). un dolor de cabeza
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

    // Avanzar hacia el frente
    void forward() {
      digitalWrite(directionPin1, LOW);
      digitalWrite(directionPin2, HIGH);
      digitalWrite(enablePin, HIGH);
    }

    // Retroceder
    void backward() {
      digitalWrite(directionPin1, HIGH);
      digitalWrite(directionPin2, LOW);
      digitalWrite(enablePin, HIGH);
    }

    // Detener motor
    void stop() {
      digitalWrite(directionPin1, LOW);
      digitalWrite(directionPin2, LOW);
      digitalWrite(enablePin, LOW);
    }

    // Pequeña pausa para que el avance no sea tan rapido
    void softStep() {
      digitalWrite(enablePin, LOW);
      delayMicroseconds(600);
      digitalWrite(enablePin, HIGH);
    }
};

Motor leftMotor = Motor(enA, in1, in2);
Motor rightMotor = Motor(enB, in3, in4);
Servo myservo;

//aqui guarda que movimiento esta haciendo (0: parado, 1: avanzando, 5: retrocediendo)
int modoMovimiento = 0; 

void setup() {
  // Velocidad rapida para conectar con la app del celular
  Serial.begin(115200); 

  pinMode(TRIG_PIN, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  pinMode(ECHO_PIN, INPUT);

  myservo.attach(servoPin);
  myservo.write(90); // Cabeza mirando al centro

  leftMotor.init();
  rightMotor.init();

  Serial.println("--- SARA LISTA Y CALIBRADA ---");
}

void loop() {
  // 1. REVISAR SI LLEGA UNA ORDEN DESDE EL CELULAR O PC
  if (Serial.available() > 0) {
    int comando = Serial.parseInt();

    switch (comando) {
      case 1: // Avanzar derecho
        Serial.println("-> 1: Avanzar");
        myservo.write(90);
        leftMotor.forward();
        rightMotor.forward();
        modoMovimiento = 1;
        break;

      case 2: // Doblar a la izquierda
        Serial.println("-> 2: Giro Izquierda");
        myservo.write(140);
        leftMotor.backward();
        rightMotor.forward();
        delay(TIEMPO_GIRO); //gira solo un momento y frena
        leftMotor.stop();
        rightMotor.stop();
        modoMovimiento = 0;
        break;

      case 3: // Doblar a la derecha
        Serial.println("-> 3: Giro Derecha");
        myservo.write(40);
        leftMotor.forward();
        rightMotor.backward();
        delay(TIEMPO_GIRO); //gira solo un momento y frena
        leftMotor.stop();
        rightMotor.stop();
        modoMovimiento = 0;
        break;

      case 4: // Mover la cabeza a los lados (saludo)
        Serial.println("-> 4: Mirar lados");
        leftMotor.stop();
        rightMotor.stop();
        modoMovimiento = 0;
        myservo.write(40);
        delay(400);
        myservo.write(140);
        delay(600);
        myservo.write(90);
        break;

      case 5: // Retroceder
        Serial.println("-> 5: Retroceder");
        leftMotor.backward();
        rightMotor.backward();
        modoMovimiento = 5;
        break;

      case 6: // Frenar todo
        Serial.println("-> 6: Detener");
        leftMotor.stop();
        rightMotor.stop();
        modoMovimiento = 0;
        break;

      case 7: // Mover cabeza a la izquierda
        myservo.write(140);
        break;

      case 8: // Mover cabeza a la derecha
        myservo.write(40);
        break;

      case 9: // Centrar cabeza
        myservo.write(90);
        break;

      default:
        break;
    }
  }

  // 2. FRENAR SI HAY ALGO CERCA MIENTRAS AVANZA
  if (modoMovimiento == 1) {
    int dist = medirDistancia();
    if (dist > 0 && dist <= DISTANCIA_SEGURA) {
      //aqui frena en seco si ve algo a 15 cm o menos
      leftMotor.stop();
      rightMotor.stop();
      modoMovimiento = 0;
      Serial.println("EMERGENCY_STOP: Obstaculo detectado");
    } else {
      leftMotor.softStep();
      rightMotor.softStep();
      delay(2);
    }
  } else if (modoMovimiento == 5) {
    leftMotor.softStep();
    rightMotor.softStep();
    delay(2);
  }
}

//funcion para medir la distancia con el sensor de ultrasonido
int medirDistancia() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duracion = pulseIn(ECHO_PIN, HIGH, 25000);
  if (duracion == 0) return 100; //si no rebota nada, asume que el camino esta libre
  return (duracion * 0.034 / 2);
}
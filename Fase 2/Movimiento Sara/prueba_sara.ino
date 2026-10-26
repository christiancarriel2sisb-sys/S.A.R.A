#include <Servo.h>

// ==========================================
// PINES DE SARA
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

// Esta es la constante en milisegundos como un parametro de calibracion 
// para que los giros no sean tan bruscos igualmente podemos ir ajustando
const int TIEMPO_GIRO = 260; 

// ==========================================
// CLASE MOTOR (CON VELOCIDAD MODERADA ESTABLE)
// ==========================================

// al principio pensaba en usar modulacion PWM analogica con analogWrite pero al momento
// de integrar la libreria <Servo.h> el timer 1 del microcontrolador entra en conflicto con el pin 10
// (enA) lo que impedia que la rueda izquierda avanzara
// para resolver esto sin cambiar nada de los cables configure la potencia de forma directa
// digitalWrite(enablePin, HIGH)
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

    // Marcha adelante a velocidad controlada 
    void forward() {
      digitalWrite(directionPin1, LOW);
      digitalWrite(directionPin2, HIGH);
      digitalWrite(enablePin, HIGH);
    }

    // Marcha atrás a velocidad controlada
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

    // Pulsos breves para reducir la agresividad en el avance
    void softStep() {
      digitalWrite(enablePin, LOW);
      delayMicroseconds(600); // Pequeña pausa
      digitalWrite(enablePin, HIGH);
    }
};

Motor leftMotor = Motor(enA, in1, in2);
Motor rightMotor = Motor(enB, in3, in4);
Servo myservo;

// Variable de estado para controlar la marcha 
int modoMovimiento = 0; // 0 = Parado, 1 = Avanzando, 5 = Retrocediendo

void setup() {
  Serial.begin(9600);

  pinMode(TRIG_PIN, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  pinMode(ECHO_PIN, INPUT);

  myservo.attach(servoPin);
  myservo.write(90); // Mirar al centro

  leftMotor.init();
  rightMotor.init();

  Serial.println("--- SARA CALIBRADA ---");
}

void loop() {
  // LECTURA DE COMANDOS
  if (Serial.available() > 0) {
    int comando = Serial.parseInt();

    switch (comando) {
      case 1: // AVANZAR
        Serial.println("-> 1: Avanzar");
        myservo.write(90);
        leftMotor.forward();
        rightMotor.forward();
        modoMovimiento = 1;
        break;

      case 2: // GIRO CONTROLADO A LA IZQUIERDA
        Serial.println("-> 2: Giro Izquierda");
        myservo.write(140);
        leftMotor.backward();
        rightMotor.forward();
        delay(TIEMPO_GIRO); // gira solo el tiempo necesario
        leftMotor.stop();
        rightMotor.stop();
        modoMovimiento = 0;
        break;

      case 3: // GIRO CONTROLADO A LA DERECHA
        Serial.println("-> 3: Giro Derecha");
        myservo.write(40);
        leftMotor.forward();
        rightMotor.backward();
        delay(TIEMPO_GIRO); // gira solo el tiempo necesario
        leftMotor.stop();
        rightMotor.stop();
        modoMovimiento = 0;
        break;

      case 4: // SALUDO DE CABEZA
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

      case 5: // RETROCEDER
        Serial.println("-> 5: Retroceder");
        leftMotor.backward();
        rightMotor.backward();
        modoMovimiento = 5;
        break;

      case 6: // DETENER
        Serial.println("-> 6: Detener");
        leftMotor.stop();
        rightMotor.stop();
        modoMovimiento = 0;
        break;

      case 7: // MIRAR IZQUIERDA
        myservo.write(140);
        break;

      case 8: // MIRAR DERECHA
        myservo.write(40);
        break;

      case 9: // CENTRAR MIRADA
        myservo.write(90);
        break;

      default:
        break;
    }
  }

  // 2.CALIBRACION SUAVE DE VELOCIDAD DURANTE EL AVANZAR/RETROCEDER DE SARA
  if (modoMovimiento == 1 || modoMovimiento == 5) {
    leftMotor.softStep();
    rightMotor.softStep();
    delay(2);
  }
}
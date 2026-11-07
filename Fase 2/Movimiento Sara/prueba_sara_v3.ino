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

const int TIEMPO_GIRO = 260; 

// ==========================================
// CLASE MOTOR
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

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(10);

  pinMode(TRIG_PIN, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  pinMode(ECHO_PIN, INPUT);

  myservo.attach(servoPin);
  myservo.write(90); // Mirar al centro

  leftMotor.init();
  rightMotor.init();

  Serial.println("--- SARA LISTA (JOYSTICK) ---");
}

void loop() {
  // LECTURA DE COMANDOS EN TIEMPO REAL
  if (Serial.available() > 0) {
    char comando = Serial.read();
    
    // Ignorar saltos de línea
    if (comando != '\n' && comando != '\r') {
      switch (comando) {
        case '1': // AVANZAR
          leftMotor.forward();
          rightMotor.forward();
          break;

        case '2': // IZQUIERDA
          leftMotor.backward();
          rightMotor.forward();
          break;

        case '3': // DERECHA
          leftMotor.forward();
          rightMotor.backward();
          break;

        case '5': // RETROCEDER
          leftMotor.backward();
          rightMotor.backward();
          break;

        case '6': // DETENERSE (OBLIGATORIO AL SOLTAR)
          leftMotor.stop();
          rightMotor.stop();
          break;

        case '7': // Mover servo izquierda
          myservo.write(140);
          break;

        case '8': // Mover servo derecha
          myservo.write(40);
          break;

        case '9': // Centrar servo
          myservo.write(90);
          break;

        default:
          break;
      }
    }
  }
}
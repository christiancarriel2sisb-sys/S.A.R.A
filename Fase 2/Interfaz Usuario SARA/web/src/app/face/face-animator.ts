export interface FaceAnimState {
  state: string;
  mouthOpen: number;
}

export class FaceAnimator {
  private gazeX = 0;
  private gazeY = 0;
  private targetGazeX = 0;
  private targetGazeY = 0;
  
  private blinkScaleY = 1;
  private isBlinking = false;
  private blinkTimer = 0;
  private nextBlinkInterval = 3000;
  private doubleBlinkCount = 0;

  private currentMouthOpen = 0;
  private targetMouthOpen = 0;
  private mouthTimer = 0;
  private lastMicromoveTime = 0;

  constructor() {
    this.scheduleNextBlink();
  }

  private scheduleNextBlink() {
    this.nextBlinkInterval = 2500 + Math.random() * 3500;
  }

  public update(dt: number, animState: FaceAnimState) {
    const now = performance.now();
    const isReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (animState.state === 'THINKING') {
      this.targetGazeX = 14;
      this.targetGazeY = -9;
    } else if (animState.state === 'LISTENING') {
      if (now - this.lastMicromoveTime > 2000) {
        this.lastMicromoveTime = now;
        const limit = isReducedMotion ? 4 : 10;
        this.targetGazeX = (Math.random() - 0.5) * limit;
        this.targetGazeY = (Math.random() - 0.5) * limit;
      }
    } else if (animState.state === 'SPEAKING') {
      if (now - this.lastMicromoveTime > 1500) {
        this.lastMicromoveTime = now;
        const limit = isReducedMotion ? 2 : 6;
        this.targetGazeX = (Math.random() - 0.5) * limit;
        this.targetGazeY = (Math.random() - 0.5) * limit;
      }
    } else if (animState.state === 'ERROR') {
      this.targetGazeX = 0;
      this.targetGazeY = 8;
    } else {
      this.targetGazeX = 0;
      this.targetGazeY = 0;
    }

    this.gazeX += (this.targetGazeX - this.gazeX) * 0.12;
    this.gazeY += (this.targetGazeY - this.gazeY) * 0.12;

    this.blinkTimer += dt;
    if (!this.isBlinking && this.blinkTimer >= this.nextBlinkInterval) {
      this.isBlinking = true;
      this.blinkTimer = 0;
    }

    if (this.isBlinking) {
      const blinkProgress = this.blinkTimer / 140;
      if (blinkProgress < 0.5) {
        this.blinkScaleY = 1 - (blinkProgress * 2) * 0.92;
      } else if (blinkProgress < 1.0) {
        this.blinkScaleY = 0.08 + ((blinkProgress - 0.5) * 2) * 0.92;
      } else {
        this.blinkScaleY = 1;
        this.isBlinking = false;
        this.blinkTimer = 0;
        if (Math.random() < 0.15 && this.doubleBlinkCount === 0) {
          this.doubleBlinkCount = 1;
          this.nextBlinkInterval = 200;
        } else {
          this.doubleBlinkCount = 0;
          this.scheduleNextBlink();
        }
      }
    } else {
      this.blinkScaleY = 1;
    }

    if (animState.state === 'SPEAKING') {
      this.mouthTimer += dt;
      if (this.mouthTimer > 100) {
        this.mouthTimer = 0;
        this.targetMouthOpen = 0.2 + Math.random() * 0.8;
      }
    } else {
      this.targetMouthOpen = 0;
    }

    this.currentMouthOpen += (this.targetMouthOpen - this.currentMouthOpen) * 0.35;

    let glowDeviation = 6;
    if (animState.state === 'LISTENING') {
      glowDeviation = 5 + Math.sin(now / 300) * 3;
    }
    if (isReducedMotion) glowDeviation = 6;

    let dimOpacity = 1;
    if (animState.state === 'IDLE') dimOpacity = 0.45;
    if (animState.state === 'ERROR') dimOpacity = 0.35;

    return {
      gazeX: this.gazeX,
      gazeY: this.gazeY,
      blinkScaleY: this.blinkScaleY,
      mouthOpen: Number(this.currentMouthOpen.toFixed(2)),
      glowDeviation,
      dimOpacity
    };
  }
}

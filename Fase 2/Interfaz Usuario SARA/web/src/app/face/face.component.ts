import { Component, ElementRef, Input, NgZone, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FaceAnimator } from './face-animator';

@Component({
  selector: 'app-face',
  standalone: true,
  imports: [CommonModule],
  template: `
    <svg #faceSvg
         xmlns="http://www.w3.org/2000/svg"
         viewBox="0 0 800 480"
         preserveAspectRatio="xMidYMid meet"
         [attr.data-state]="state"
         aria-label="Cara de Sara"
         style="width: 100%; height: 100%; display: block; background: #000;">
      
      <defs>
        <filter id="glow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur #blurFilter stdDeviation="6" result="coloredBlur"/>
          <feMerge>
            <feMergeNode in="coloredBlur"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
      </defs>

      <rect width="800" height="480" fill="#000"/>
      <rect x="20" y="20" width="760" height="440" rx="90" fill="#05050A" stroke="#14142A" stroke-width="2"/>

      <g #faceGroup [style.opacity]="dimOpacity">
        <g #leftEyeGroup transform="translate(290, 190)">
          <circle cx="0" cy="0" r="52" fill="none" stroke="#6B5BFF" stroke-width="22" filter="url(#glow)"/>
        </g>
        <g #rightEyeGroup transform="translate(510, 190)">
          <circle cx="0" cy="0" r="52" fill="none" stroke="#6B5BFF" stroke-width="22" filter="url(#glow)"/>
        </g>
        <path #mouthPath
              d="M 310 338 Q 400 352 490 338 L 490 350 Q 400 364 310 350 Z"
              fill="#6B5BFF"
              filter="url(#glow)"
              [attr.data-mouth-open]="mouthOpenAttr"/>
      </g>
    </svg>
  `,
  styles: [`
    :host { display: block; width: 100%; height: 100%; background: #000; }
  `]
})
export class FaceComponent implements OnInit, OnDestroy {
  @Input() state: string = 'IDLE';

  @ViewChild('leftEyeGroup') leftEyeGroup!: ElementRef;
  @ViewChild('rightEyeGroup') rightEyeGroup!: ElementRef;
  @ViewChild('mouthPath') mouthPath!: ElementRef;
  @ViewChild('blurFilter') blurFilter!: ElementRef;
  @ViewChild('faceGroup') faceGroup!: ElementRef;

  public mouthOpenAttr: number = 0;
  public dimOpacity: number = 1;

  private animator = new FaceAnimator();
  private animFrameId: number = 0;
  private lastTime: number = 0;

  constructor(private ngZone: NgZone) {}

  ngOnInit() {
    this.ngZone.runOutsideAngular(() => {
      this.lastTime = performance.now();
      const loop = (time: number) => {
        const dt = time - this.lastTime;
        this.lastTime = time;

        const res = this.animator.update(dt, { state: this.state, mouthOpen: 0 });
        this.mouthOpenAttr = res.mouthOpen;
        this.dimOpacity = res.dimOpacity;

        if (this.leftEyeGroup && this.rightEyeGroup) {
          const leftTransform = `translate(290, 190) translate(${res.gazeX}, ${res.gazeY}) scale(1, ${res.blinkScaleY})`;
          const rightTransform = `translate(510, 190) translate(${res.gazeX}, ${res.gazeY}) scale(1, ${res.blinkScaleY})`;
          this.leftEyeGroup.nativeElement.setAttribute('transform', leftTransform);
          this.rightEyeGroup.nativeElement.setAttribute('transform', rightTransform);
        }

        if (this.mouthPath) {
          const o = res.mouthOpen;
          const w = 180 - 20 * o;
          const x1 = 400 - w / 2;
          const x2 = 400 + w / 2;
          const topCurveY = 338 - (8 * o);
          const topControlY = 352 - (4 * o);
          const botControlY = 364 + (55 * o);
          const botEdgeY = 350 + (12 * o);

          const d = `M ${x1} ${topCurveY} Q 400 ${topControlY} ${x2} ${topCurveY} L ${x2} ${botEdgeY} Q 400 ${botControlY} ${x1} ${botEdgeY} Z`;
          this.mouthPath.nativeElement.setAttribute('d', d);
        }

        if (this.blurFilter) {
          this.blurFilter.nativeElement.setAttribute('stdDeviation', res.glowDeviation);
        }

        this.animFrameId = requestAnimationFrame(loop);
      };
      this.animFrameId = requestAnimationFrame(loop);
    });
  }

  ngOnDestroy() {
    if (this.animFrameId) cancelAnimationFrame(this.animFrameId);
  }
}

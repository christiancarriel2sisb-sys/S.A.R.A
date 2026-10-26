import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LogService } from './log.service';

@Component({
  selector: 'app-pipeline-bar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="pipeline-bar">
      @for (step of logService.pipelineSteps(); track step.id) {
        <div class="pipeline-step" [ngClass]="step.status">
          <span class="step-indicator"></span>
          <span class="step-label">{{ step.label }}</span>
        </div>
      }
    </div>
  `,
  styles: [`
    .pipeline-bar {
      display: flex;
      gap: 12px;
      padding: 10px 16px;
      background: #1e1e2f;
      border-radius: 8px;
      color: #fff;
      font-size: 13px;
      align-items: center;
      flex-wrap: wrap;
      box-shadow: 0 4px 6px rgba(0,0,0,0.1);
      margin-bottom: 16px;
    }
    .pipeline-step {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 4px;
      background: #2a2a3d;
      border: 1px solid #3f3f5a;
      transition: all 0.3s ease;
    }
    .step-indicator {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: #888;
    }
    .pending { opacity: 0.6; }
    .pending .step-indicator { background: #aaa; }
    .active { border-color: #3b82f6; box-shadow: 0 0 8px rgba(59, 130, 246, 0.5); }
    .active .step-indicator { background: #3b82f6; animation: pulse 1.5s infinite; }
    .ok { border-color: #10b981; }
    .ok .step-indicator { background: #10b981; }
    .error { border-color: #ef4444; }
    .error .step-indicator { background: #ef4444; }
    @keyframes pulse {
      0% { transform: scale(0.95); opacity: 0.8; }
      50% { transform: scale(1.2); opacity: 1; }
      100% { transform: scale(0.95); opacity: 0.8; }
    }
  `]
})
export class PipelineBarComponent {
  logService = inject(LogService);
}

import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LogService, LogLevel } from './log.service';

@Component({
  selector: 'app-log-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="log-panel" [class.collapsed]="isCollapsed()">
      <div class="log-header" (click)="toggleCollapse()">
        <div class="header-title">
          <span>🛠️ Panel de Depuración y Logs</span>
          <span class="badge">{{ filteredLogs().length }}</span>
        </div>
        <button class="toggle-btn">{{ isCollapsed() ? '▲ Expandir' : '▼ Contraer' }}</button>
      </div>

      @if (!isCollapsed()) {
        <div class="log-controls">
          <div class="filter-group">
            <label>Nivel:</label>
            <select [(ngModel)]="selectedLevel">
              <option value="ALL">Todos</option>
              <option value="DEBUG">DEBUG</option>
              <option value="INFO">INFO</option>
              <option value="WARN">WARN</option>
              <option value="ERROR">ERROR</option>
            </select>
          </div>

          <div class="filter-group">
            <label>Módulo:</label>
            <input type="text" [(ngModel)]="moduleFilter" placeholder="Filtrar por módulo..." />
          </div>

          <div class="actions">
            <button class="btn-action" (click)="logService.clearLogs()">Limpiar</button>
            <button class="btn-action" (click)="downloadLogs()">Descargar</button>
          </div>
        </div>

        <div class="log-list">
          @for (log of filteredLogs(); track log.id) {
            <div class="log-item" [ngClass]="log.level.toLowerCase()">
              <span class="log-time">{{ log.timestamp }}</span>
              <span class="log-level-badge" [ngClass]="log.level.toLowerCase()">{{ log.level }}</span>
              <span class="log-module">[{{ log.module }}]</span>
              <span class="log-msg">{{ log.message }}</span>
            </div>
          } @empty {
            <div class="no-logs">No hay registros de log disponibles.</div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .log-panel {
      background: #181825;
      border: 1px solid #313244;
      border-radius: 8px;
      color: #cdd6f4;
      font-family: monospace;
      font-size: 12px;
      margin-top: 20px;
      overflow: hidden;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
    }
    .log-header {
      background: #1e1e2e;
      padding: 10px 16px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      cursor: pointer;
      user-select: none;
      font-weight: bold;
    }
    .header-title {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .badge {
      background: #45475a;
      padding: 2px 6px;
      border-radius: 10px;
      font-size: 11px;
    }
    .toggle-btn {
      background: transparent;
      border: none;
      color: #bac2de;
      cursor: pointer;
      font-size: 12px;
    }
    .log-controls {
      display: flex;
      gap: 16px;
      padding: 10px 16px;
      background: #11111b;
      border-bottom: 1px solid #313244;
      align-items: center;
      flex-wrap: wrap;
    }
    .filter-group {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .filter-group select, .filter-group input {
      background: #181825;
      border: 1px solid #45475a;
      color: #cdd6f4;
      padding: 4px 8px;
      border-radius: 4px;
    }
    .actions {
      display: flex;
      gap: 8px;
      margin-left: auto;
    }
    .btn-action {
      background: #313244;
      border: 1px solid #45475a;
      color: #cdd6f4;
      padding: 4px 10px;
      border-radius: 4px;
      cursor: pointer;
    }
    .btn-action:hover {
      background: #45475a;
    }
    .log-list {
      max-height: 250px;
      overflow-y: auto;
      padding: 8px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .log-item {
      display: flex;
      gap: 8px;
      padding: 4px 8px;
      border-radius: 4px;
      background: #1e1e2e;
      align-items: flex-start;
      word-break: break-all;
    }
    .log-time { color: #6c7086; min-width: 70px; }
    .log-level-badge {
      padding: 1px 5px;
      border-radius: 3px;
      font-weight: bold;
      font-size: 10px;
      min-width: 45px;
      text-align: center;
    }
    .log-level-badge.debug { background: #45475a; color: #bac2de; }
    .log-level-badge.info { background: #1e66f5; color: #fff; }
    .log-level-badge.warn { background: #df8e1d; color: #11111b; }
    .log-level-badge.error { background: #d20f39; color: #fff; }
    .log-module { color: #f9e2af; min-width: 90px; }
    .log-msg { flex: 1; color: #cdd6f4; }
    .no-logs { padding: 16px; text-align: center; color: #6c7086; }
  `]
})
export class LogPanelComponent {
  logService = inject(LogService);
  isCollapsed = signal<boolean>(false);
  selectedLevel = signal<string>('ALL');
  moduleFilter = signal<string>('');

  filteredLogs = computed(() => {
    const level = this.selectedLevel();
    const mod = this.moduleFilter().toLowerCase();
    return this.logService.logs().filter(log => {
      const matchLevel = level === 'ALL' || log.level === level;
      const matchModule = !mod || log.module.toLowerCase().includes(mod);
      return matchLevel && matchModule;
    });
  });

  toggleCollapse() {
    this.isCollapsed.update(val => !val);
  }

  downloadLogs() {
    const logsJson = JSON.stringify(this.logService.logs(), null, 2);
    const blob = new Blob([logsJson], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `sara-logs-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    this.logService.info('LogPanel', 'Logs descargados correctamente');
  }
}

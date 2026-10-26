import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <nav class="sara-navbar">
      <div class="nav-brand">🤖 SARA Project</div>
      <div class="nav-links">
        <a routerLink="/sara" routerLinkActive="active">Módulo 3: Sara con Rostro</a>
        <a routerLink="/gemini" routerLinkActive="active">Módulo 2: Gemini Chat</a>
        <a routerLink="/dictado" routerLinkActive="active">Módulo 1: Dictado Local</a>
      </div>
    </nav>
    <main class="sara-main">
      <router-outlet></router-outlet>
    </main>
  `,
  styles: [`
    :host {
      display: block;
      background: #090a0f;
      min-height: 100vh;
      color: #cdd6f4;
      font-family: system-ui, -apple-system, sans-serif;
    }
    .sara-navbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 24px;
      background: #11111b;
      border-bottom: 1px solid #313244;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
      position: relative;
      z-index: 1001;
    }
    .nav-brand {
      font-weight: bold;
      font-size: 18px;
      color: #f5e0dc;
    }
    .nav-links {
      display: flex;
      gap: 12px;
    }
    .nav-links a {
      color: #a6adc8;
      text-decoration: none;
      padding: 6px 14px;
      border-radius: 6px;
      font-size: 13px;
      font-weight: bold;
      transition: all 0.2s;
    }
    .nav-links a:hover {
      background: #181825;
      color: #cdd6f4;
    }
    .nav-links a.active {
      background: #3b82f6;
      color: #fff;
    }
    .sara-main {
      padding: 0;
    }
  `]
})
export class AppComponent {
  title = 'SARA';
}

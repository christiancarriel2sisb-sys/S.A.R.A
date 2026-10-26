import { Routes } from '@angular/router';
import { DictationComponent } from './dictation/dictation.component';
import { GeminiComponent } from './gemini/gemini.component';
import { SaraComponent } from './sara/sara.component';

export const routes: Routes = [
  { path: 'dictado', component: DictationComponent },
  { path: 'gemini', component: GeminiComponent },
  { path: 'sara', component: SaraComponent },
  { path: '', redirectTo: 'sara', pathMatch: 'full' },
  { path: '**', redirectTo: 'sara' }
];

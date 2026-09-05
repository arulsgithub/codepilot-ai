import { Routes } from '@angular/router';
import { ChatPageComponent } from './features/chat/components/chat-page/chat-page.component';

export const routes: Routes = [
  { path: '', component: ChatPageComponent },
  { path: '**', redirectTo: '' },
];

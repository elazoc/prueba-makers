import { Routes } from '@angular/router';
import { UsersActions } from './users.actions';
import { UsersPage } from './users.page';
import { UsersStore } from './users.store';

export const USERS_ROUTES: Routes = [
  { path: '', providers: [UsersStore, UsersActions], component: UsersPage },
];

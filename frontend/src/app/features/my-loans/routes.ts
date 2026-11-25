import { Routes } from '@angular/router';
import { LoanDetailRoute } from './loan-detail.route';
import { MyLoansActions } from './my-loans.actions';
import { MyLoansPage } from './my-loans.page';
import { MyLoansStore } from './my-loans.store';

/** El store y las acciones viven en la ruta: se crean al entrar y se descartan al salir. */
export const MY_LOANS_ROUTES: Routes = [
  {
    path: '',
    providers: [MyLoansStore, MyLoansActions],
    component: MyLoansPage,
    children: [{ path: ':id', component: LoanDetailRoute }],
  },
];

import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TestBed } from '@angular/core/testing';
import { appError } from '../../core/auth/testing';
import { LoanFormDrawer } from './loan-form.drawer';
import { MyLoansStore } from './my-loans.store';
import { loan } from './testing';

describe('LoanFormDrawer', () => {
  let store: { request: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn> };
  let ref: { close: ReturnType<typeof vi.fn> };

  async function render(data: { loan?: ReturnType<typeof loan> } = {}) {
    store = { request: vi.fn().mockResolvedValue(loan('nuevo')), update: vi.fn().mockResolvedValue(loan('l1')) };
    ref = { close: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: MyLoansStore, useValue: store },
        { provide: DialogRef, useValue: ref },
        { provide: DIALOG_DATA, useValue: data },
      ],
    });
    const fixture = TestBed.createComponent(LoanFormDrawer);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const amount = el.querySelector<HTMLInputElement>('input[prMoneyInput]')!;
    const term = el.querySelector<HTMLInputElement>('input[type="number"]')!;
    const button = (name: string) => [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === name)!;
    const type = (i: HTMLInputElement, v: string) => {
      i.value = v;
      i.dispatchEvent(new Event('input'));
    };
    const submit = async () => {
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
      await new Promise((r) => setTimeout(r, 0));
      await fixture.whenStable();
    };
    return { fixture, el, amount, term, button, type, submit };
  }

  it('sends a new request with the normalized amount and closes', async () => {
    const { amount, term, type, submit } = await render();
    type(amount, '1500000');
    type(term, '12');
    await submit();
    expect(store.request).toHaveBeenCalledWith({ amount: '1500000.00', termMonths: 12 });
    expect(ref.close).toHaveBeenCalled();
  });

  it('summarizes the request when it is valid', async () => {
    const { fixture, el, amount, term, type } = await render();
    type(amount, '1500000');
    type(term, '12');
    await fixture.whenStable();
    expect(el.textContent).toContain('Pides $\u00a01.500.000 a 12 meses.');
  });

  it('sets the term with the shortcuts', async () => {
    const { fixture, term, button } = await render();
    button('24 meses').click();
    await fixture.whenStable();
    expect(term.value).toBe('24');
  });

  it('keeps the drawer open with the data and shows the server message on 422', async () => {
    const { el, amount, term, type, submit } = await render();
    store.request.mockRejectedValue(appError(422, 'LOAN_POLICY_VIOLATION', 'Ya tienes 3 solicitudes pendientes.'));
    type(amount, '1500000');
    type(term, '12');
    await submit();
    expect(ref.close).not.toHaveBeenCalled();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Ya tienes 3 solicitudes pendientes.');
    expect(amount.value).toBe('1.500.000');
  });

  it('edits an existing loan', async () => {
    const { amount, submit } = await render({ loan: loan('l1') });
    expect(amount.value).toBe('1.500.000');
    await submit();
    expect(store.update).toHaveBeenCalledWith('l1', { amount: '1500000.00', termMonths: 12 });
  });

  it('does not send an invalid form', async () => {
    const { el, submit } = await render();
    await submit();
    expect(store.request).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Completa este campo.');
  });
});

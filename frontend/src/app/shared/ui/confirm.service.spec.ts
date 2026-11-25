import { TestBed } from '@angular/core/testing';
import { ConfirmService } from './confirm.service';

const settle = () => new Promise((r) => setTimeout(r, 0));
const buttonNamed = (name: string) =>
  [...document.querySelectorAll<HTMLButtonElement>('.cdk-overlay-container button')].find(
    (b) => b.textContent?.trim() === name,
  )!;

describe('ConfirmService', () => {
  afterEach(() => document.querySelector('.cdk-overlay-container')?.replaceChildren());

  it('resolves true only when the person confirms', async () => {
    const promise = TestBed.inject(ConfirmService).confirm({
      title: 'Aprobar préstamo',
      message: 'Vas a aprobar $ 1.500.000 a 12 meses para Usuario Demo. Es definitivo.',
      confirmLabel: 'Aprobar',
    });
    await settle();

    const dialog = document.querySelector('[role="alertdialog"], [role="dialog"]')!;
    expect(dialog.textContent).toContain('Aprobar préstamo');
    expect(dialog.textContent).toContain('Es definitivo.');

    buttonNamed('Aprobar').click();
    expect(await promise).toBe(true);
  });

  it('resolves false when the person goes back', async () => {
    const promise = TestBed.inject(ConfirmService).confirm({
      title: 'Cancelar solicitud',
      message: 'No se puede deshacer.',
      confirmLabel: 'Cancelar solicitud',
      tone: 'danger',
    });
    await settle();
    expect(buttonNamed('Cancelar solicitud').classList).toContain('pr-btn--danger');

    buttonNamed('Volver').click();
    expect(await promise).toBe(false);
  });

  it('resolves false on Escape', async () => {
    const promise = TestBed.inject(ConfirmService).confirm({
      title: 'Desactivar cuenta',
      message: 'No podrá ingresar.',
      confirmLabel: 'Desactivar',
      tone: 'danger',
    });
    await settle();
    // Como en un navegador real: el CDK revisa keyCode 27.
    document.body.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true } as KeyboardEventInit),
    );
    expect(await promise).toBe(false);
  });
});

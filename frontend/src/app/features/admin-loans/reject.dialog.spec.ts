import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TestBed } from '@angular/core/testing';
import { loan } from '../my-loans/testing';
import { RejectDialog } from './reject.dialog';

describe('RejectDialog', () => {
  async function render() {
    const ref = { close: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: DialogRef, useValue: ref },
        {
          provide: DIALOG_DATA,
          useValue: { loan: { ...loan('l1'), applicantEmail: 'usuario@test.com', applicantName: 'Usuario Demo' } },
        },
      ],
    });
    const fixture = TestBed.createComponent(RejectDialog);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const textarea = el.querySelector('textarea')!;
    const type = (v: string) => {
      textarea.value = v;
      textarea.dispatchEvent(new Event('input'));
    };
    const submit = async () => {
      [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Rechazar')!.click();
      await fixture.whenStable();
    };
    return { fixture, el, ref, type, submit };
  }

  it('says what is being rejected and that the applicant will see the reason', async () => {
    const { el } = await render();
    expect(el.textContent).toContain('Usuario Demo');
    expect(el.textContent).toContain('$\u00a01.500.000');
    expect(el.textContent).toContain('El solicitante verá este motivo.');
  });

  it('asks for at least 5 characters, ignoring spaces', async () => {
    const { el, ref, type, submit } = await render();
    type('  mal  ');
    await submit();
    expect(el.textContent).toContain('Usa al menos 5 caracteres.');
    expect(ref.close).not.toHaveBeenCalled();
  });

  it('returns the trimmed reason', async () => {
    const { ref, type, submit } = await render();
    type('  Ingresos insuficientes  ');
    await submit();
    expect(ref.close).toHaveBeenCalledWith('Ingresos insuficientes');
  });

  it('counts the characters', async () => {
    const { fixture, el, type } = await render();
    type('Ingresos insuficientes');
    await fixture.whenStable();
    expect(el.textContent).toContain('22/500');
  });
});

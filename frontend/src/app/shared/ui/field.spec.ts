import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { PrField } from './field';
import { PrInput } from './input.directive';

@Component({
  imports: [PrField, PrInput, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<pr-field label="Correo" hint="Usa tu correo personal." [control]="email">
    <input prInput type="email" [formControl]="email" />
  </pr-field>`,
})
class Host {
  email = new FormControl('', { nonNullable: true, validators: [Validators.required] });
}

describe('PrField', () => {
  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, input: el.querySelector('input')!, label: el.querySelector('label')! };
  }

  it('links the label and the hint to the input', async () => {
    const { el, input, label } = await render();
    expect(label.getAttribute('for')).toBe(input.id);
    const hint = el.querySelector(`#${input.getAttribute('aria-describedby')}`);
    expect(hint?.textContent).toContain('Usa tu correo personal.');
    expect(input.hasAttribute('aria-invalid')).toBe(false);
  });

  it('replaces the hint with the error after the field is touched', async () => {
    const { fixture, el, input } = await render();
    fixture.componentInstance.email.markAsTouched();
    await fixture.whenStable();

    expect(input.getAttribute('aria-invalid')).toBe('true');
    const message = el.querySelector(`#${input.getAttribute('aria-describedby')}`);
    expect(message?.textContent).toContain('Completa este campo.');
    expect(el.textContent).not.toContain('Usa tu correo personal.');
  });

  it('shows a server error set on the control', async () => {
    const { fixture, el } = await render();
    const email = fixture.componentInstance.email;
    email.setValue('ya@existe.com');
    email.markAsTouched();
    email.setErrors({ server: 'Ya hay una cuenta con este correo.' });
    await fixture.whenStable();
    expect(el.textContent).toContain('Ya hay una cuenta con este correo.');
  });
});

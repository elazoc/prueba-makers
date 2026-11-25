import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { errorMessage } from '../forms/error-messages';
import { PrMoneyInput } from './money-input.directive';

@Component({
  imports: [PrMoneyInput, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<input prMoneyInput [formControl]="amount" />`,
})
class Host {
  amount = new FormControl<string | null>(null);
}

describe('PrMoneyInput', () => {
  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const input = (fixture.nativeElement as HTMLElement).querySelector('input')!;
    const type = (text: string) => {
      input.value = text;
      input.dispatchEvent(new Event('input'));
    };
    return { fixture, input, type, control: fixture.componentInstance.amount };
  }

  it('shows grouped thousands and emits the normalized value', async () => {
    const { input, type, control } = await render();
    type('1500000');
    expect(input.value).toBe('1.500.000');
    expect(control.value).toBe('1500000.00');
    expect(control.errors).toBeNull();
  });

  it('keeps a decimal comma while typing', async () => {
    const { input, type, control } = await render();
    type('2500,5');
    expect(input.value).toBe('2.500,5');
    expect(control.value).toBe('2500.50');
  });

  it('flags the US format instead of guessing', async () => {
    const { input, type, control } = await render();
    type('1,500,000.00');
    expect(input.value).toBe('1,500,000.00');
    expect(control.value).toBeNull();
    expect(control.errors).toEqual({ moneyFormat: 'format' });
    expect(errorMessage(control.errors)).toBe(
      'Escribe el monto sin centavos o con coma decimal, por ejemplo 1.500.000.',
    );
  });

  it('writes model values in the local format', async () => {
    const { fixture, input, control } = await render();
    control.setValue('1500000.50');
    await fixture.whenStable();
    expect(input.value).toBe('1.500.000,50');
    control.setValue('800000.00');
    expect(input.value).toBe('800.000');
  });

  it('is a decimal keyboard without autocomplete', async () => {
    const { input } = await render();
    expect(input.getAttribute('inputmode')).toBe('decimal');
    expect(input.getAttribute('autocomplete')).toBe('off');
  });
});

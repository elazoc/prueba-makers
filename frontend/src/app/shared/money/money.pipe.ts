import { Pipe, PipeTransform } from '@angular/core';
import { formatMoney } from './money';

/** `{{ loan.amount | money }}` → "$ 1.500.000". */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: string | null | undefined): string {
    return amount ? formatMoney(amount) : '';
  }
}

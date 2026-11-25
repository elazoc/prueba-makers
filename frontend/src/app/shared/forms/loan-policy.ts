/**
 * Copia de la política del backend (loans.policy.*). El backend manda: si responde 422,
 * se muestra su mensaje aunque el frente haya dejado pasar el dato.
 */
export const LOAN_POLICY = {
  minAmount: '100.00',
  maxAmount: '50000000.00',
  minTermMonths: 1,
  maxTermMonths: 120,
  maxPending: 3,
} as const;

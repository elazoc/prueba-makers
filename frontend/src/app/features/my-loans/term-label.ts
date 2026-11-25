/** «1 mes», «12 meses». */
export const termLabel = (months: number): string => `${months} ${months === 1 ? 'mes' : 'meses'}`;

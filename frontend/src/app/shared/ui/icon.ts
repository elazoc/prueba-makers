import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Renderer2,
  effect,
  inject,
  input,
} from '@angular/core';
import { ICONS, IconName } from './icons';

const SVG_NS = 'svg';

/** Ícono de Lucide con el trazo de la marca (1.75). Decorativo salvo que reciba `label`. */
@Component({
  selector: 'pr-icon',
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'pr-icon',
    '[class.pr-icon--sm]': "size() === 'sm'",
    '[class.pr-icon--lg]': "size() === 'lg'",
    '[attr.aria-hidden]': "label() ? null : 'true'",
    '[attr.role]': "label() ? 'img' : null",
    '[attr.aria-label]': 'label() ?? null',
  },
})
export class PrIcon {
  readonly name = input.required<IconName>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly label = input<string>();

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const r = inject(Renderer2);

    effect(() => {
      const svg = r.createElement('svg', SVG_NS) as SVGElement;
      const attrs: Record<string, string> = {
        viewBox: '0 0 24 24',
        fill: 'none',
        stroke: 'currentColor',
        'stroke-width': '1.75',
        'stroke-linecap': 'round',
        'stroke-linejoin': 'round',
        focusable: 'false',
        'aria-hidden': 'true',
      };
      Object.entries(attrs).forEach(([k, v]) => r.setAttribute(svg, k, v));
      for (const [tag, props] of ICONS[this.name()]) {
        const child = r.createElement(tag, SVG_NS);
        Object.entries(props).forEach(([k, v]) => r.setAttribute(child, k, String(v)));
        r.appendChild(svg, child);
      }
      host.replaceChildren(svg);
    });
  }
}

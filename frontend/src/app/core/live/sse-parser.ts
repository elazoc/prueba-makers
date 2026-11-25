export interface SseMessage {
  event?: string;
  id?: string;
  data: string;
}

/**
 * Lector incremental de Server-Sent Events. Recibe trozos de texto (pueden partir un evento a la
 * mitad) y entrega cada mensaje completo. Ignora los comentarios (`: ping`), que son el latido.
 */
export function createSseParser(onMessage: (m: SseMessage) => void): (chunk: string) => void {
  let pending = '';
  let event: string | undefined;
  let id: string | undefined;
  let data: string[] = [];

  const dispatch = () => {
    if (data.length) {
      onMessage({ ...(event ? { event } : {}), ...(id ? { id } : {}), data: data.join('\n') });
    }
    event = undefined;
    id = undefined;
    data = [];
  };

  return (chunk: string) => {
    pending += chunk;
    const lines = pending.split(/\r\n|\n|\r/);
    pending = lines.pop() ?? '';

    for (const line of lines) {
      if (line === '') {
        dispatch();
        continue;
      }
      if (line.startsWith(':')) continue;

      const colon = line.indexOf(':');
      const field = colon === -1 ? line : line.slice(0, colon);
      const value = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '');
      if (field === 'event') event = value;
      else if (field === 'id') id = value;
      else if (field === 'data') data.push(value);
    }
  };
}

/**
 * Advanced: a pasted or edited SPARQL query, shareable through the address. The query travels as
 * `?upit=<base64url of UTF-8>`, so any text survives the address bar and a chat message.
 */

const PARAMETER = 'upit';

const toBase64Url = (text: string): string => {
  const bytes = new TextEncoder().encode(text);
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

const fromBase64Url = (value: string): string | null => {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) return null;
  try {
    const binary = atob(value.replace(/-/g, '+').replace(/_/g, '/'));
    return new TextDecoder('utf-8', { fatal: true }).decode(Uint8Array.from(binary, (c) => c.charCodeAt(0)));
  } catch {
    return null;
  }
};

/** The query carried by the address, or null when there is none or it is not valid base64url. */
export function readQueryAddress(search: string): string | null {
  const value = new URLSearchParams(search).get(PARAMETER);
  return value ? fromBase64Url(value) : null;
}

/** The search string with the query written in, or taken out when `sparql` is null; other parameters stay. */
export function writeQueryAddress(search: string, sparql: string | null): string {
  const params = new URLSearchParams(search);
  params.delete(PARAMETER);
  if (sparql) params.set(PARAMETER, toBase64Url(sparql));
  const text = params.toString();
  return text ? `?${text}` : '';
}

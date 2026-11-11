import { describe, expect, it } from 'vitest';
import { readQueryAddress, writeQueryAddress } from './advanced';

describe('the query in the address', () => {
  it('round-trips any text, including non-ASCII and newlines, through ?upit= and leaves other parameters alone', () => {
    const sparql = 'SELECT ?s WHERE {\n  ?s rdfs:label "Ćirilica — čšđž" .\n}';
    const search = writeQueryAddress('?lang=sr&focus=ana', sparql);
    expect(search).toMatch(/^\?lang=sr&focus=ana&upit=[A-Za-z0-9_-]+$/);
    expect(readQueryAddress(search)).toBe(sparql);
  });

  it('is absent when there is no parameter, and removed by writing null', () => {
    expect(readQueryAddress('?lang=sr')).toBeNull();
    expect(readQueryAddress('')).toBeNull();
    expect(writeQueryAddress('?lang=sr&upit=abc', null)).toBe('?lang=sr');
    expect(writeQueryAddress('?upit=abc', null)).toBe('');
  });

  it('ignores a value that is not valid base64url', () => {
    expect(readQueryAddress('?upit=%%%')).toBeNull();
    expect(readQueryAddress('?upit=!!!')).toBeNull();
  });
});

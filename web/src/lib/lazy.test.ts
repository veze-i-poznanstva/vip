import { describe, expect, it } from 'vitest';
import { loadPart } from './lazy';

describe('loadPart', () => {
  it('gives the part when its file loads', async () => {
    expect(await loadPart(async () => 'panel')).toBe('panel');
  });

  it('gives null when the file is gone, as after a deploy that replaced it while the page was open', async () => {
    const gone = () => Promise.reject(new TypeError('Failed to fetch dynamically imported module'));
    expect(await loadPart(gone)).toBeNull();
  });
});

import { describe, expect, it } from 'vitest';
import { get } from 'svelte/store';
import { dictionaries, language, resolveLanguage, supportedLanguages, t, translate } from './i18n';

describe('translate', () => {
  it('returns the text for a key in the given dictionary', () => {
    expect(translate({ title: 'VIP Graph Visualizer' }, 'title')).toBe('VIP Graph Visualizer');
  });
});

describe('translate with a missing key', () => {
  it('returns the key itself so the gap is visible', () => {
    expect(translate({}, 'legend.people')).toBe('legend.people');
  });
});

describe('translate with parameters', () => {
  it('replaces every named placeholder', () => {
    expect(translate({ loaded: 'Loaded {count} elements from {file}' }, 'loaded', { count: 20, file: 'graph.json' }))
      .toBe('Loaded 20 elements from graph.json');
  });
});

describe('resolveLanguage', () => {
  const supported = ['en', 'sr'];

  it('takes the first candidate that is supported', () => {
    expect(resolveLanguage(supported, ['sr', 'en'])).toBe('sr');
  });

  it('ignores the region of a candidate', () => {
    expect(resolveLanguage(supported, ['sr-RS'])).toBe('sr');
  });

  it('skips unsupported, empty and missing candidates', () => {
    expect(resolveLanguage(supported, ['de-DE', '', null, undefined, 'sr'])).toBe('sr');
  });

  it('falls back to the first supported language when nothing matches', () => {
    expect(resolveLanguage(supported, ['de'])).toBe('en');
  });
});

describe('dictionaries', () => {
  it('every language has exactly the keys of the English dictionary', () => {
    const english = Object.keys(dictionaries.en).sort();
    for (const language of supportedLanguages) {
      expect(Object.keys(dictionaries[language]).sort(), language).toEqual(english);
    }
  });
});

describe('language store', () => {
  it('t renders in the current language and follows a switch', () => {
    language.set('en');
    expect(get(t)('legend.people')).toBe('People');
    language.set('sr');
    expect(get(t)('legend.people')).toBe('Osobe');
  });
});

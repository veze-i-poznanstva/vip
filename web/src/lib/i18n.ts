import en from '../locales/en.json';
import sr from '../locales/sr.json';
import { derived, writable } from 'svelte/store';

export type Dictionary = Record<string, string>;
export type Params = Record<string, string | number>;

/** Text for a key, with `{name}` placeholders filled from params; an unknown key is returned as is so the gap shows. */
export function translate(dictionary: Dictionary, key: string, params: Params = {}): string {
  const text = dictionary[key] ?? key;
  return text.replace(/\{(\w+)\}/g, (match, name) => (name in params ? String(params[name]) : match));
}

/**
 * The first candidate (a URL parameter, a stored choice, the browser's languages) whose
 * language part is supported; the first supported language when none matches.
 */
export function resolveLanguage(supported: string[], candidates: (string | null | undefined)[]): string {
  for (const candidate of candidates) {
    const language = (candidate ?? '').toLowerCase().split('-')[0];
    if (language && supported.includes(language)) return language;
  }
  return supported[0];
}

/** Languages the viewer speaks; the first is the default. Serbian is in Latin script, as the data. */
export const supportedLanguages = ['sr', 'en'] as const;
export type Language = (typeof supportedLanguages)[number];

export const dictionaries: Record<Language, Dictionary> = { en, sr };

/** The language the viewer is showing. Components read `$t`, which follows it. */
export const language = writable<Language>(supportedLanguages[0]);

/** A translator for the current language; a text missing in it comes from the English dictionary. */
export const t = derived(language, (current) => (key: string, params: Params = {}) =>
  translate({ ...dictionaries.en, ...dictionaries[current] }, key, params));

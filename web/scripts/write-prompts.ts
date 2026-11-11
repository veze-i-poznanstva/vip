// Publishes the assistant brief in every viewer language as dist/prompt.<language>.txt: the same text
// the Ask panel builds, minus the question, so a link can tell an assistant to read it instead of
// carrying fourteen thousand characters in the address. Run after `vite build`, with vite-node.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { buildBrief, summarizeVocabulary, usedTerms } from '../src/lib/ask';
import { loadTurtle } from '../src/lib/query';
import { dictionaries, supportedLanguages, translate } from '../src/lib/i18n';

const published = (name: string) => readFileSync(new URL(`../public/${name}`, import.meta.url), 'utf8');
const dataset = loadTurtle(published('data.ttl') + '\n' + published('schema.ttl'));
const rules = JSON.parse(published('rules.json'));
const used = usedTerms(dataset);
mkdirSync('dist', { recursive: true });
for (const language of supportedLanguages) {
  const brief = buildBrief(summarizeVocabulary(dataset, language, used), rules, (key, params) => translate(dictionaries[language], key, params));
  writeFileSync(`dist/prompt.${language}.txt`, brief);
  console.log(`write-prompts: dist/prompt.${language}.txt, ${brief.length} characters`);
}

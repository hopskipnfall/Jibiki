import {japaneseTransforms} from './language/ja/japanese-transforms.js';
const special = /[.*+?()[\]{}|\\]/;
const conditions = {};
for (const [k, v] of Object.entries(japaneseTransforms.conditions)) {
  conditions[k] = {isDictionaryForm: !!v.isDictionaryForm, sub: v.subConditions ?? null};
}
const transforms = [];
let bad = 0;
for (const [id, t] of Object.entries(japaneseTransforms.transforms)) {
  const rules = t.rules.map((r) => {
    let src = r.isInflected.source;
    let pattern;
    if (r.type === 'suffix') { pattern = src.replace(/\$$/, ''); }
    else if (r.type === 'prefix') { pattern = src.replace(/^\^/, ''); }
    else if (r.type === 'wholeWord') { pattern = src.replace(/^\^/, '').replace(/\$$/, ''); }
    if (special.test(pattern)) bad++;
    const out = r.type === 'suffix' ? r.deinflected : r.type === 'prefix' ? r.deinflect('') : r.deinflect('');
    return {type: r.type, in: pattern, out, ci: r.conditionsIn, co: r.conditionsOut};
  });
  transforms.push({id, name: t.name, rules});
}
console.error('transforms', transforms.length, 'rules', transforms.reduce((a, t) => a + t.rules.length, 0), 'regex-special', bad, 'types', [...new Set(transforms.flatMap(t => t.rules.map(r => r.type)))]);
process.stdout.write(JSON.stringify({conditions, transforms}));

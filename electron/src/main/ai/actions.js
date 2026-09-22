'use strict';

const VALID_TYPES = new Set(['create', 'modify', 'delete', 'rename']);

function isSafeJavaName(name) {
  return typeof name === 'string'
    && name.endsWith('.java')
    && !name.includes('/')
    && !name.includes('\\')
    && !name.includes('..');
}

function collect(parsed, actions) {
  if (!parsed || !Array.isArray(parsed.actions)) return;
  for (const raw of parsed.actions) {
    if (!raw || typeof raw !== 'object') continue;
    const type = raw.type;
    const file = raw.file;
    if (!VALID_TYPES.has(type) || !isSafeJavaName(file)) continue;

    if (type === 'create' || type === 'modify') {
      if (typeof raw.content === 'string' && raw.content.trim()) {
        actions.push({ type, file, content: raw.content });
      }
    } else if (type === 'delete') {
      actions.push({ type, file });
    } else if (type === 'rename') {
      const newName = raw.newName || raw.new_name;
      if (isSafeJavaName(newName)) actions.push({ type, file, newName });
    }
  }
}

/** Extrae el objeto JSON completo que empieza en startIdx contando llaves. */
function extractBalanced(text, startIdx) {
  let depth = 0;
  let inString = false;
  let escape = false;
  for (let i = startIdx; i < text.length; i += 1) {
    const ch = text[i];
    if (escape) { escape = false; continue; }
    if (ch === '\\') { escape = true; continue; }
    if (ch === '"') { inString = !inString; continue; }
    if (inString) continue;
    if (ch === '{') depth += 1;
    else if (ch === '}') {
      depth -= 1;
      if (depth === 0) return text.slice(startIdx, i + 1);
    }
  }
  return null;
}

/**
 * Separa el mensaje legible de las acciones sobre archivos.
 * Acepta el bloque ```json ... ``` y también JSON suelto al final.
 */
function parseFileActions(responseText) {
  const actions = [];
  let clean = responseText || '';

  const fenced = /```json\s*(\{[\s\S]*?\})\s*```/gi;
  const blocks = [];
  let match;
  while ((match = fenced.exec(clean)) !== null) {
    if (match[1].includes('"actions"')) blocks.push(match[0]);
    try { collect(JSON.parse(match[1]), actions); } catch { /* no es un bloque de acciones */ }
  }
  if (actions.length) {
    for (const block of blocks) clean = clean.replace(block, '');
    return { message: clean.trim(), actions };
  }

  const idx = clean.search(/\{\s*"actions"/);
  if (idx !== -1) {
    const json = extractBalanced(clean, idx);
    if (json) {
      try {
        collect(JSON.parse(json), actions);
        if (actions.length) clean = clean.slice(0, idx).trim();
      } catch { /* JSON incompleto: probablemente cortado */ }
    }
  }

  return { message: clean.trim(), actions };
}

const CONTINUATION_HINTS = [
  'continuar con', 'siguiente archivo', 'próximo archivo', 'proximo archivo',
  'a continuación crearé', 'a continuacion creare', '¿quieres que continúe',
  'quieres que continue', 'faltan los archivos', 'siguiente clase',
  'continúo con', 'continuo con', 'dime si sigo'
];

function detectContinuation(message) {
  const lower = (message || '').toLowerCase();
  return CONTINUATION_HINTS.some((hint) => lower.includes(hint));
}

/** Aplica las acciones sobre el mapa de archivos y devuelve el resumen. */
function applyActions(files, actions) {
  const next = { ...files };
  const applied = [];
  for (const action of actions || []) {
    if (action.type === 'create' || action.type === 'modify') {
      const existed = Object.prototype.hasOwnProperty.call(next, action.file);
      next[action.file] = action.content;
      applied.push({ ...action, effective: existed ? 'modify' : 'create' });
    } else if (action.type === 'delete') {
      if (next[action.file] !== undefined) {
        delete next[action.file];
        applied.push({ ...action, effective: 'delete' });
      }
    } else if (action.type === 'rename') {
      if (next[action.file] !== undefined) {
        next[action.newName] = next[action.file];
        delete next[action.file];
        applied.push({ ...action, effective: 'rename' });
      }
    }
  }
  return { files: next, applied };
}

module.exports = { parseFileActions, detectContinuation, applyActions, isSafeJavaName };

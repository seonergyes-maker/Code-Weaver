'use strict';

const client = require('./client');
const P = require('./prompts');
const { parseFileActions, detectContinuation } = require('./actions');

const MAX_FILE_CHARS = 60000;

/** Contexto con el proyecto completo, recortando archivos enormes. */
function projectContext(files, { truncate = 0 } = {}) {
  const entries = Object.entries(files || {});
  if (!entries.length) return 'Proyecto vacío (todavía no hay archivos).';
  let context = '=== PROYECTO COMPLETO ===\n';
  for (const [name, code] of entries) {
    const limit = truncate || MAX_FILE_CHARS;
    const body = code.length > limit ? `${code.slice(0, limit)}\n// ... (archivo recortado)` : code;
    context += `\n--- ${name} ---\n\`\`\`java\n${body}\n\`\`\`\n`;
  }
  context += '=== FIN DEL PROYECTO ===\n';
  return context;
}

class Agent {
  constructor(name, systemPrompt) {
    this.name = name;
    this.systemPrompt = systemPrompt;
  }

  async run(userMessage, { context = '', maxTokens = 8192, history = [] } = {}) {
    const system = context ? `${this.systemPrompt}\n\n${context}` : this.systemPrompt;
    const messages = [...history, { role: 'user', content: userMessage }];
    if (messages[0].role !== 'user') {
      messages.unshift({ role: 'user', content: 'Hola, necesito ayuda con Java.' });
    }

    const { text, stopReason } = await client.complete({ system, messages, maxTokens });
    const { message, actions } = parseFileActions(text);

    let finalMessage = message;
    if (!actions.length && text.includes('"actions"')) {
      finalMessage += stopReason === 'max_tokens'
        ? '\n\n⚠️ La respuesta se cortó por longitud. Pide un solo archivo cada vez.'
        : '\n\n⚠️ No se pudo interpretar el bloque de acciones. Vuelve a pedirlo.';
    }

    return {
      response: finalMessage,
      actions,
      needsContinuation: detectContinuation(finalMessage),
      agent: this.name,
      stopReason
    };
  }
}

// ---------------------------------------------------------------- subagentes

const codeWriter = new Agent('CodeWriter', P.CODE_WRITER_PROMPT);
const errorFixer = new Agent('ErrorFixer', P.ERROR_FIXER_PROMPT);
const testGenerator = new Agent('TestGenerator', P.TEST_GENERATOR_PROMPT);
const docGenerator = new Agent('DocGenerator', P.DOC_GENERATOR_PROMPT);
const architect = new Agent('Architect', P.ARCHITECT_PROMPT);
const general = new Agent('General', P.GENERAL_PROMPT);

async function generateCode(description, files) {
  return codeWriter.run(description, { context: projectContext(files), maxTokens: 16384 });
}

async function fixError(errorMessage, files) {
  const message = `Corrige este error de compilación o ejecución:\n\n${errorMessage}`;
  return errorFixer.run(message, { context: projectContext(files), maxTokens: 16384 });
}

async function explainError(errorMessage, code) {
  const agent = new Agent('ErrorExplainer', `${P.ERROR_FIXER_PROMPT}

En este caso SOLO explica el error, no generes bloque de acciones.`);
  return agent.run(
    `Explica este error y cómo solucionarlo:\n\n${errorMessage}`,
    { context: code ? `Código actual:\n\`\`\`java\n${code}\n\`\`\`` : '', maxTokens: 4096 }
  );
}

async function generateTests(className, files) {
  const code = files[`${className}.java`] || files[className] || '';
  const message = `Genera tests JUnit 5 para la clase ${className}.\n\n\`\`\`java\n${code}\n\`\`\``;
  return testGenerator.run(message, { context: projectContext(files, { truncate: 8000 }), maxTokens: 16384 });
}

async function generateDocs(fileName, files) {
  const code = files[fileName] || '';
  const message = `Añade JavaDoc completo al archivo ${fileName} y devuélvelo entero.\n\n\`\`\`java\n${code}\n\`\`\``;
  return docGenerator.run(message, { maxTokens: 16384 });
}

function parsePlan(text) {
  const fenced = text.match(/```json\s*([\s\S]*?)```/i);
  const raw = fenced ? fenced[1] : text;
  try {
    return JSON.parse(raw);
  } catch {
    const start = raw.indexOf('{');
    const end = raw.lastIndexOf('}');
    if (start !== -1 && end > start) {
      try { return JSON.parse(raw.slice(start, end + 1)); } catch { /* sin plan estructurado */ }
    }
  }
  return null;
}

function formatPlan(plan) {
  if (!plan) return '';
  let out = '';
  if (plan.analysis) out += `**Análisis**\n${plan.analysis}\n\n`;
  if (Array.isArray(plan.files_needed) && plan.files_needed.length) {
    out += '**Archivos necesarios**\n';
    for (const f of plan.files_needed) out += `- \`${f.name}\` — ${f.purpose || ''}\n`;
    out += '\n';
  }
  if (Array.isArray(plan.implementation_plan) && plan.implementation_plan.length) {
    out += '**Plan de implementación**\n';
    for (const step of plan.implementation_plan) out += `${step.step}. ${step.description}\n`;
    out += '\n';
  }
  if (plan.estimated_complexity) out += `**Complejidad estimada:** ${plan.estimated_complexity}\n\n`;
  if (Array.isArray(plan.recommendations) && plan.recommendations.length) {
    out += '**Recomendaciones**\n';
    for (const rec of plan.recommendations) out += `- ${rec}\n`;
  }
  return out.trim();
}

async function planProject(description, files) {
  const result = await architect.run(description, {
    context: projectContext(files, { truncate: 6000 }),
    maxTokens: 8192
  });
  const plan = parsePlan(result.response);
  return {
    ...result,
    plan,
    response: plan ? formatPlan(plan) : result.response,
    agent: 'Architect'
  };
}

// ---------------------------------------------------------------- coordinador

const INTENT_AGENTS = {
  WRITE_CODE: 'code_writer',
  MODIFY_CODE: 'code_writer',
  FIX_ERROR: 'error_fixer',
  GENERATE_TESTS: 'test_generator',
  GENERATE_DOCS: 'doc_generator',
  ARCHITECT: 'architect',
  EXPLAIN: 'general',
  SEARCH: 'general',
  MULTI_STEP: 'multi_step'
};

async function analyzeIntent(userMessage, projectSummary) {
  try {
    const { text } = await client.complete({
      system: `${P.INTENT_CLASSIFIER_PROMPT}\n\nContexto: ${projectSummary}`,
      messages: [{ role: 'user', content: userMessage }],
      maxTokens: 300
    });
    const match = text.match(/\{[\s\S]*\}/);
    if (match) {
      const parsed = JSON.parse(match[0]);
      if (parsed.intent && INTENT_AGENTS[parsed.intent]) return parsed;
    }
  } catch (err) {
    if (err.code === 'CANCELLED' || err.code === 'NO_API_KEY') throw err;
    console.error('Clasificación de intención fallida:', err.message);
  }
  return { intent: 'WRITE_CODE', confidence: 0.4, details: 'clasificación por defecto' };
}

async function handleMultiStep(userMessage, files) {
  const planResult = await planProject(userMessage, files);
  const filesNeeded = planResult.plan?.files_needed || [];
  if (!filesNeeded.length) return planResult;

  const first = filesNeeded[0];
  const codeResult = await generateCode(
    `Crea ${first.name}: ${first.purpose || ''}`,
    files
  );
  return {
    response: `**Plan de implementación**\n\n${planResult.response}\n\n---\n\n**Primer archivo**\n\n${codeResult.response}`,
    actions: codeResult.actions,
    needsContinuation: filesNeeded.length > 1,
    remainingFiles: filesNeeded.slice(1),
    agent: 'MultiStep (Architect + CodeWriter)',
    plan: planResult.plan
  };
}

/**
 * Punto de entrada del modo agentes: clasifica la intención y delega
 * en el subagente correspondiente.
 */
async function route({ userMessage, files = {}, history = [], errorMessage = null, targetFile = null }) {
  const summary = Object.keys(files).length
    ? `Archivos: ${Object.keys(files).join(', ')}`
    : 'Proyecto vacío';

  const intentResult = errorMessage
    ? { intent: 'FIX_ERROR', confidence: 1, details: 'error explícito' }
    : await analyzeIntent(userMessage, summary);

  const intent = intentResult.intent;
  let result;

  switch (intent) {
    case 'FIX_ERROR':
      result = await fixError(errorMessage || userMessage, files);
      break;
    case 'WRITE_CODE':
    case 'MODIFY_CODE':
      result = await generateCode(userMessage, files);
      break;
    case 'GENERATE_TESTS':
      result = targetFile && files[targetFile]
        ? await generateTests(targetFile.replace(/\.java$/, ''), files)
        : await generateCode(`Genera tests JUnit 5 para: ${userMessage}`, files);
      break;
    case 'GENERATE_DOCS':
      result = targetFile && files[targetFile]
        ? await generateDocs(targetFile, files)
        : await generateCode(`Genera JavaDoc para: ${userMessage}`, files);
      break;
    case 'ARCHITECT':
      result = await planProject(userMessage, files);
      break;
    case 'MULTI_STEP':
      result = await handleMultiStep(userMessage, files);
      break;
    default:
      result = await general.run(userMessage, {
        context: projectContext(files, { truncate: 4000 }),
        history,
        maxTokens: 8192
      });
  }

  return { ...result, intent, intentConfidence: intentResult.confidence };
}

module.exports = {
  Agent, route, generateCode, fixError, explainError, generateTests,
  generateDocs, planProject, projectContext, analyzeIntent
};

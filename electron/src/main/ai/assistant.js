'use strict';

const client = require('./client');
const settings = require('../services/settings');
const agents = require('./agents');
const P = require('./prompts');
const { parseFileActions, detectContinuation } = require('./actions');

/** Chat directo, sin clasificación de intención (modo "simple"). */
async function directChat({ messages, files }) {
  const system = `${P.SYSTEM_PROMPT_WITH_ACTIONS}\n\n${agents.projectContext(files)}`;
  const formatted = messages.map((m) => ({ role: m.role, content: m.content }));
  if (formatted.length && formatted[0].role !== 'user') {
    formatted.unshift({ role: 'user', content: 'Hola, necesito ayuda con Java.' });
  }

  const { text, stopReason } = await client.complete({ system, messages: formatted, maxTokens: 16384 });
  const { message, actions } = parseFileActions(text);

  let finalMessage = message;
  if (!actions.length && text.includes('"actions"')) {
    finalMessage += stopReason === 'max_tokens'
      ? '\n\n⚠️ La respuesta se cortó por longitud. Pide un solo archivo cada vez.'
      : '\n\n⚠️ No se pudo interpretar el bloque de acciones.';
  }

  return {
    response: finalMessage,
    actions,
    needsContinuation: detectContinuation(finalMessage),
    agent: 'Claude',
    intent: null
  };
}

/**
 * Entrada principal del chat. Usa el sistema de agentes si está activado
 * en preferencias; si no, una conversación directa con el proyecto como contexto.
 */
async function chat({ messages = [], files = {}, errorMessage = null, targetFile = null }) {
  const last = messages[messages.length - 1];
  const userMessage = last ? last.content : '';

  if (settings.get('useAgents')) {
    const history = messages.slice(0, -1).slice(-8);
    return agents.route({ userMessage, files, history, errorMessage, targetFile });
  }
  return directChat({ messages, files });
}

/** Analiza la salida de una ejecución y sugiere qué hacer. */
async function analyzeOutput({ output, files, isError }) {
  const agent = new agents.Agent(
    'OutputAnalyst',
    `Eres un experto en Java analizando la salida de una ejecución.
${isError ? 'La ejecución ha fallado. Identifica la causa y propón la corrección.' : 'Revisa si la salida es la esperada y sugiere mejoras concretas.'}
Sé breve: máximo 10 líneas. No generes bloques de acciones.`
  );
  return agent.run(
    `Salida de la ejecución:\n\`\`\`\n${String(output).slice(-8000)}\n\`\`\``,
    { context: agents.projectContext(files, { truncate: 6000 }), maxTokens: 2048 }
  );
}

module.exports = {
  chat,
  analyzeOutput,
  fixError: (payload) => agents.fixError(payload.errorMessage, payload.files),
  explainError: (payload) => agents.explainError(payload.errorMessage, payload.code),
  generateTests: (payload) => agents.generateTests(payload.className, payload.files),
  generateDocs: (payload) => agents.generateDocs(payload.fileName, payload.files),
  plan: (payload) => agents.planProject(payload.description, payload.files),
  isConfigured: client.isConfigured,
  cancel: client.cancel
};

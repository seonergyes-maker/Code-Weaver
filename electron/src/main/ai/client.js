'use strict';

const Anthropic = require('@anthropic-ai/sdk');
const settings = require('../services/settings');

let cached = null;
let cachedKey = null;
let cachedUrl = null;

function apiKey() {
  return settings.get('apiKey') || process.env.ANTHROPIC_API_KEY || '';
}

function baseUrl() {
  return settings.get('baseUrl') || process.env.ANTHROPIC_BASE_URL || '';
}

function isConfigured() {
  return Boolean(apiKey());
}

function getClient() {
  const key = apiKey();
  if (!key) {
    const err = new Error('Falta la API key de Anthropic. Añádela en Preferencias para usar el asistente.');
    err.code = 'NO_API_KEY';
    throw err;
  }
  const url = baseUrl();
  if (!cached || cachedKey !== key || cachedUrl !== url) {
    const options = { apiKey: key, maxRetries: 0 };
    if (url) options.baseURL = url;
    const Ctor = Anthropic.Anthropic || Anthropic;
    cached = new Ctor(options);
    cachedKey = key;
    cachedUrl = url;
  }
  return cached;
}

function model() {
  return settings.get('model') || 'claude-sonnet-4-5';
}

function isRateLimit(error) {
  const status = error?.status || error?.statusCode;
  if (status === 429 || status === 529) return true;
  const text = String(error?.message || '');
  return /429|rate.?limit|overloaded|quota/i.test(text);
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

let controller = null;

function cancel() {
  if (controller) {
    controller.abort();
    controller = null;
    return true;
  }
  return false;
}

/** Extrae el texto de la respuesta ignorando bloques que no sean de texto. */
function extractText(response) {
  if (!response || !Array.isArray(response.content)) return '';
  return response.content
    .filter((block) => block && (block.type === 'text' || typeof block.text === 'string'))
    .map((block) => block.text || '')
    .join('\n')
    .trim();
}

/**
 * Llamada al modelo con reintentos exponenciales ante límites de tasa.
 * Devuelve { text, stopReason }.
 */
async function complete({ system, messages, maxTokens = 8192, temperature }) {
  const client = getClient();
  const body = {
    model: model(),
    max_tokens: maxTokens,
    system,
    messages
  };
  if (typeof temperature === 'number') body.temperature = temperature;

  let lastError = null;
  for (let attempt = 0; attempt < 5; attempt += 1) {
    controller = new AbortController();
    try {
      const response = await client.messages.create(body, { signal: controller.signal });
      controller = null;
      return { text: extractText(response), stopReason: response.stop_reason, usage: response.usage };
    } catch (err) {
      controller = null;
      if (err?.name === 'AbortError') {
        const cancelled = new Error('Consulta cancelada');
        cancelled.code = 'CANCELLED';
        throw cancelled;
      }
      lastError = err;
      if (!isRateLimit(err) || attempt === 4) break;
      await sleep(Math.min(2 ** attempt * 2000, 32000));
    }
  }
  throw lastError;
}

module.exports = { complete, isConfigured, cancel, model, extractText };

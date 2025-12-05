import os
from anthropic import Anthropic
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception

AI_INTEGRATIONS_ANTHROPIC_API_KEY = os.environ.get("AI_INTEGRATIONS_ANTHROPIC_API_KEY")
AI_INTEGRATIONS_ANTHROPIC_BASE_URL = os.environ.get("AI_INTEGRATIONS_ANTHROPIC_BASE_URL")

client = Anthropic(
    api_key=AI_INTEGRATIONS_ANTHROPIC_API_KEY,
    base_url=AI_INTEGRATIONS_ANTHROPIC_BASE_URL
)

import json
import re

SYSTEM_PROMPT = """Eres un experto desarrollador Java. Tu rol es ayudar a los usuarios a:
1. Escribir código Java limpio y eficiente
2. Explicar errores de compilación y cómo solucionarlos
3. Sugerir mejoras y buenas prácticas
4. Generar código nuevo basado en descripciones
5. Responder preguntas técnicas sobre Java

Siempre proporciona código bien formateado y comentado cuando sea relevante.
Responde en español a menos que el usuario escriba en otro idioma."""

SYSTEM_PROMPT_WITH_ACTIONS = """Eres un experto desarrollador Java trabajando en un IDE multi-archivo. 

CONTEXTO DEL PROYECTO:
Siempre recibirás el código COMPLETO del proyecto (todos los archivos .java). Tu trabajo es:
1. Entender la arquitectura completa del proyecto
2. Hacer cambios coherentes que consideren TODOS los archivos
3. Mantener la consistencia entre clases relacionadas
4. Sugerir mejoras considerando el proyecto como un todo

Tu rol:
1. Escribir código Java limpio y eficiente
2. Explicar errores de compilación y cómo solucionarlos
3. Sugerir mejoras y buenas prácticas
4. Generar código nuevo basado en descripciones
5. Modificar archivos existentes cuando se solicite

IMPORTANTE: Cuando el usuario te pida crear o modificar código, DEBES incluir un bloque de acciones al final de tu respuesta.

El formato es:
```json
{"actions": [
  {"type": "create", "file": "NombreClase.java", "content": "código completo aquí"}
]}
```

Tipos de acción:
- "create": Crea un nuevo archivo .java
- "modify": Reemplaza el contenido de un archivo existente

Reglas CRÍTICAS:
- SOLO UN ARCHIVO POR RESPUESTA para evitar cortes
- Si necesitas modificar múltiples archivos, crea/modifica el primero y AUTOMÁTICAMENTE ofrece continuar con los demás
- El nombre del archivo DEBE terminar en .java
- El contenido debe ser código Java completo y funcional
- Cuando modifiques un archivo, asegúrate de que sea compatible con los demás archivos del proyecto
- Para preguntas o explicaciones, NO incluyas el bloque de acciones
- Mantén las clases concisas (máximo 500 líneas)

Responde en español a menos que el usuario escriba en otro idioma."""


def is_rate_limit_error(exception: BaseException) -> bool:
    error_msg = str(exception)
    return (
        "429" in error_msg
        or "RATELIMIT_EXCEEDED" in error_msg
        or "quota" in error_msg.lower()
        or "rate limit" in error_msg.lower()
        or (hasattr(exception, "status_code") and exception.status_code == 429)
    )


@retry(
    stop=stop_after_attempt(5),
    wait=wait_exponential(multiplier=1, min=2, max=64),
    retry=retry_if_exception(is_rate_limit_error),
    reraise=True
)
def chat_with_claude(messages: list, current_code: str = "") -> str:
    context = ""
    if current_code.strip():
        context = f"\n\nCódigo Java actual del usuario:\n```java\n{current_code}\n```"
    
    system_message = SYSTEM_PROMPT + context
    
    formatted_messages = []
    for msg in messages:
        formatted_messages.append({
            "role": msg["role"],
            "content": msg["content"]
        })
    
    if formatted_messages and formatted_messages[0]["role"] != "user":
        formatted_messages.insert(0, {
            "role": "user",
            "content": "Hola, necesito ayuda con Java."
        })
    
    response = client.messages.create(
        model="claude-sonnet-4-5",
        max_tokens=8192,
        system=system_message,
        messages=formatted_messages
    )
    
    return response.content[0].text


@retry(
    stop=stop_after_attempt(5),
    wait=wait_exponential(multiplier=1, min=2, max=64),
    retry=retry_if_exception(is_rate_limit_error),
    reraise=True
)
def generate_java_code(description: str) -> str:
    response = client.messages.create(
        model="claude-sonnet-4-5",
        max_tokens=8192,
        system="""Eres un experto generador de código Java. 
Genera código Java completo, funcional y bien comentado basado en la descripción del usuario.
Solo devuelve el código Java, sin explicaciones adicionales.
El código debe incluir la clase principal con método main si es una aplicación ejecutable.""",
        messages=[{
            "role": "user",
            "content": description
        }]
    )
    
    text = response.content[0].text
    
    if "```java" in text:
        start = text.find("```java") + 7
        end = text.find("```", start)
        if end != -1:
            return text[start:end].strip()
    elif "```" in text:
        start = text.find("```") + 3
        end = text.find("```", start)
        if end != -1:
            return text[start:end].strip()
    
    return text.strip()


@retry(
    stop=stop_after_attempt(5),
    wait=wait_exponential(multiplier=1, min=2, max=64),
    retry=retry_if_exception(is_rate_limit_error),
    reraise=True
)
def explain_error(error_message: str, code: str) -> str:
    response = client.messages.create(
        model="claude-sonnet-4-5",
        max_tokens=4096,
        system="""Eres un experto en depuración de Java. 
Explica el error de compilación de forma clara y proporciona una solución específica.
Incluye el código corregido si es posible.""",
        messages=[{
            "role": "user",
            "content": f"""Error de compilación:
{error_message}

Código con error:
```java
{code}
```

Por favor explica qué causó este error y cómo solucionarlo."""
        }]
    )
    
    return response.content[0].text


@retry(
    stop=stop_after_attempt(5),
    wait=wait_exponential(multiplier=1, min=2, max=64),
    retry=retry_if_exception(is_rate_limit_error),
    reraise=True
)
def suggest_improvements(code: str) -> str:
    response = client.messages.create(
        model="claude-sonnet-4-5",
        max_tokens=4096,
        system="""Eres un experto revisor de código Java.
Analiza el código y sugiere mejoras en:
1. Rendimiento
2. Legibilidad
3. Buenas prácticas
4. Seguridad
5. Estructura

Proporciona ejemplos de código mejorado cuando sea relevante.""",
        messages=[{
            "role": "user",
            "content": f"""Revisa este código Java y sugiere mejoras:
```java
{code}
```"""
        }]
    )
    
    return response.content[0].text


@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=1, max=16),
    retry=retry_if_exception(is_rate_limit_error),
    reraise=True
)
def get_code_completions(code: str, cursor_line: int, cursor_col: int) -> list:
    lines = code.split('\n')
    current_line = lines[cursor_line] if cursor_line < len(lines) else ""
    context_start = max(0, cursor_line - 10)
    context_end = min(len(lines), cursor_line + 5)
    context = '\n'.join(lines[context_start:context_end])
    
    response = client.messages.create(
        model="claude-haiku-4-5",
        max_tokens=1024,
        system="""Eres un asistente de autocompletado de código Java.
Dado el contexto del código y la posición del cursor, sugiere completaciones relevantes.
Responde SOLO con un JSON array de objetos con formato:
[{"label": "nombreMétodo()", "insertText": "nombreMétodo()", "detail": "descripción breve"}]
Máximo 5 sugerencias. Sin explicaciones adicionales, solo el JSON.""",
        messages=[{
            "role": "user",
            "content": f"""Código Java (línea actual: {cursor_line + 1}, columna: {cursor_col}):
```java
{context}
```
Línea actual: `{current_line}`
Cursor en columna {cursor_col}.

Dame sugerencias de autocompletado para esta posición."""
        }]
    )
    
    import json
    text = response.content[0].text.strip()
    
    if text.startswith('['):
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            pass
    
    start = text.find('[')
    end = text.rfind(']') + 1
    if start != -1 and end > start:
        try:
            return json.loads(text[start:end])
        except json.JSONDecodeError:
            pass
    
    return []


@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=1, max=16),
    retry=retry_if_exception(is_rate_limit_error),
    reraise=True
)
def complete_line(code: str, partial_line: str) -> str:
    response = client.messages.create(
        model="claude-haiku-4-5",
        max_tokens=512,
        system="""Eres un asistente de autocompletado de código Java.
Completa la línea de código de forma inteligente basándote en el contexto.
Responde SOLO con el texto que completa la línea, sin explicaciones.
Si la línea está completa, responde con una cadena vacía.""",
        messages=[{
            "role": "user",
            "content": f"""Contexto del código:
```java
{code[-1500:] if len(code) > 1500 else code}
```

Línea parcial a completar: `{partial_line}`

Completa esta línea de forma inteligente:"""
        }]
    )
    
    completion = response.content[0].text.strip()
    if completion.startswith('`') and completion.endswith('`'):
        completion = completion[1:-1]
    
    return completion


def parse_file_actions(response_text: str) -> tuple:
    """
    Parse Claude's response to extract file actions and clean message.
    Returns: (clean_message, actions_list)
    """
    actions = []
    clean_message = response_text
    json_text = None
    
    json_fenced_pattern = r'```json\s*(\{[\s\S]*?"actions"[\s\S]*?\})\s*```'
    matches = re.findall(json_fenced_pattern, response_text, re.IGNORECASE)
    
    if matches:
        for match in matches:
            _parse_action_json(match, actions)
        clean_message = re.sub(json_fenced_pattern, '', response_text, flags=re.IGNORECASE).strip()
    else:
        start_idx = response_text.find('{"actions"')
        if start_idx == -1:
            start_idx = response_text.find('{ "actions"')
        
        if start_idx != -1:
            remaining = response_text[start_idx:]
            depth = 0
            end_idx = -1
            in_string = False
            escape_next = False
            
            for i, char in enumerate(remaining):
                if escape_next:
                    escape_next = False
                    continue
                if char == '\\':
                    escape_next = True
                    continue
                if char == '"' and not escape_next:
                    in_string = not in_string
                    continue
                if in_string:
                    continue
                if char == '{':
                    depth += 1
                elif char == '}':
                    depth -= 1
                    if depth == 0:
                        end_idx = i + 1
                        break
            
            if end_idx > 0:
                json_text = remaining[:end_idx]
                _parse_action_json(json_text, actions)
                if actions:
                    clean_message = response_text[:start_idx].strip()
    
    return clean_message, actions


def _parse_action_json(json_str: str, actions: list):
    """Helper to parse action JSON and append valid actions to list."""
    try:
        data = json.loads(json_str)
        if "actions" in data and isinstance(data["actions"], list):
            for action in data["actions"]:
                if isinstance(action, dict):
                    action_type = action.get("type", "")
                    filename = action.get("file", "")
                    content = action.get("content", "")
                    
                    if action_type in ["create", "modify"] and filename.endswith(".java") and content:
                        actions.append({
                            "type": action_type,
                            "file": filename,
                            "content": content
                        })
    except json.JSONDecodeError:
        pass


@retry(
    stop=stop_after_attempt(5),
    wait=wait_exponential(multiplier=1, min=2, max=64),
    retry=retry_if_exception(is_rate_limit_error),
    reraise=True
)
def chat_with_actions(messages: list, current_code: str = "", project_files: dict = None) -> tuple:
    """
    Chat with Claude and get file actions.
    Returns: (response_text, actions_list, needs_continuation)
    """
    context = ""
    
    if project_files and len(project_files) > 0:
        context = "\n\n=== PROYECTO COMPLETO (todos los archivos) ===\n"
        for fname, code in project_files.items():
            context += f"\n--- {fname} ---\n```java\n{code}\n```\n"
        context += "=== FIN DEL PROYECTO ===\n"
    elif current_code.strip():
        context = f"\n\nCódigo Java actual:\n```java\n{current_code}\n```"
    
    system_message = SYSTEM_PROMPT_WITH_ACTIONS + context
    
    formatted_messages = []
    for msg in messages:
        formatted_messages.append({
            "role": msg["role"],
            "content": msg["content"]
        })
    
    if formatted_messages and formatted_messages[0]["role"] != "user":
        formatted_messages.insert(0, {
            "role": "user",
            "content": "Hola, necesito ayuda con Java."
        })
    
    response = client.messages.create(
        model="claude-sonnet-4-5",
        max_tokens=16384,
        system=system_message,
        messages=formatted_messages
    )
    
    response_text = response.content[0].text
    clean_message, actions = parse_file_actions(response_text)
    
    if not actions and '{"actions"' in response_text:
        if response.stop_reason == "max_tokens":
            clean_message = "La respuesta fue cortada por ser muy larga. Por favor, pide crear un archivo a la vez."
        else:
            clean_message += "\n\n⚠️ No se pudo procesar la acción. Intenta pedir el archivo de nuevo."
    
    needs_continuation = detect_continuation(clean_message)
    
    return clean_message, actions, needs_continuation


def detect_continuation(message: str) -> bool:
    """
    Detecta si Claude indica que hay más archivos por crear.
    """
    continuation_patterns = [
        "continuar con",
        "siguiente archivo",
        "próximo archivo",
        "crear el siguiente",
        "crear los demás",
        "crear los otros",
        "¿creo el siguiente",
        "¿quieres que cree",
        "¿deseas que cree",
        "¿procedo con",
        "falta crear",
        "faltan crear",
        "ahora crearé",
        "a continuación",
        "el siguiente será",
        "también necesitamos",
        "también necesitas",
    ]
    
    message_lower = message.lower()
    for pattern in continuation_patterns:
        if pattern in message_lower:
            return True
    return False



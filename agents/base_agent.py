"""
Base agent class for all subagents.
"""
import os
import json
import re
from anthropic import Anthropic
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception

AI_INTEGRATIONS_ANTHROPIC_API_KEY = os.environ.get("AI_INTEGRATIONS_ANTHROPIC_API_KEY")
AI_INTEGRATIONS_ANTHROPIC_BASE_URL = os.environ.get("AI_INTEGRATIONS_ANTHROPIC_BASE_URL")

client = Anthropic(
    api_key=AI_INTEGRATIONS_ANTHROPIC_API_KEY,
    base_url=AI_INTEGRATIONS_ANTHROPIC_BASE_URL
)


def is_rate_limit_error(exception: BaseException) -> bool:
    error_msg = str(exception)
    return (
        "429" in error_msg
        or "RATELIMIT_EXCEEDED" in error_msg
        or "quota" in error_msg.lower()
        or "rate limit" in error_msg.lower()
        or (hasattr(exception, "status_code") and exception.status_code == 429)
    )


class BaseAgent:
    """Base class for all agents."""
    
    def __init__(self, name: str, system_prompt: str, model: str = "claude-sonnet-4-5"):
        self.name = name
        self.system_prompt = system_prompt
        self.model = model
    
    @retry(
        stop=stop_after_attempt(5),
        wait=wait_exponential(multiplier=1, min=2, max=64),
        retry=retry_if_exception(is_rate_limit_error),
        reraise=True
    )
    def run(self, user_message: str, context: str = "", max_tokens: int = 8192) -> dict:
        """
        Execute the agent with the given message and context.
        
        Returns:
            dict with {response, actions, needs_continuation, confidence}
        """
        full_system = self.system_prompt
        if context:
            full_system += f"\n\n{context}"
        
        response = client.messages.create(
            model=self.model,
            max_tokens=max_tokens,
            system=full_system,
            messages=[{"role": "user", "content": user_message}]
        )
        
        response_text = response.content[0].text
        clean_message, actions = self.parse_actions(response_text)
        needs_continuation = self.detect_continuation(clean_message)
        
        return {
            "response": clean_message,
            "actions": actions,
            "needs_continuation": needs_continuation,
            "agent": self.name
        }
    
    def parse_actions(self, response_text: str) -> tuple:
        """Parse file actions from response."""
        actions = []
        clean_message = response_text
        
        json_pattern = r'```json\s*(\{[\s\S]*?"actions"[\s\S]*?\})\s*```'
        matches = re.findall(json_pattern, response_text, re.IGNORECASE)
        
        if matches:
            for match in matches:
                self._parse_action_json(match, actions)
            clean_message = re.sub(json_pattern, '', response_text, flags=re.IGNORECASE).strip()
        else:
            start_idx = response_text.find('{"actions"')
            if start_idx == -1:
                start_idx = response_text.find('{ "actions"')
            
            if start_idx != -1:
                remaining = response_text[start_idx:]
                end_idx = self._find_json_end(remaining)
                
                if end_idx > 0:
                    json_text = remaining[:end_idx]
                    self._parse_action_json(json_text, actions)
                    if actions:
                        clean_message = response_text[:start_idx].strip()
        
        return clean_message, actions
    
    def _find_json_end(self, text: str) -> int:
        """Find the end of a JSON object."""
        depth = 0
        in_string = False
        escape_next = False
        
        for i, char in enumerate(text):
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
                    return i + 1
        return -1
    
    def _parse_action_json(self, json_str: str, actions: list):
        """Parse action JSON and append valid actions."""
        try:
            data = json.loads(json_str)
            if "actions" in data and isinstance(data["actions"], list):
                for action in data["actions"]:
                    if isinstance(action, dict):
                        action_type = action.get("type", "")
                        filename = action.get("file", "")
                        
                        if not filename.endswith(".java"):
                            continue
                        if "/" in filename or "\\" in filename:
                            continue
                        
                        if action_type in ["create", "modify"]:
                            content = action.get("content", "")
                            if content:
                                actions.append({
                                    "type": action_type,
                                    "file": filename,
                                    "content": content
                                })
                        elif action_type == "delete":
                            actions.append({"type": "delete", "file": filename})
                        elif action_type == "rename":
                            new_name = action.get("newName", "")
                            if new_name and new_name.endswith(".java"):
                                actions.append({
                                    "type": "rename",
                                    "file": filename,
                                    "newName": new_name
                                })
        except json.JSONDecodeError:
            pass
    
    def detect_continuation(self, text: str) -> bool:
        """Detect if more files need to be created."""
        continuation_patterns = [
            r'siguiente archivo',
            r'próximo archivo',
            r'continuar con',
            r'falta crear',
            r'ahora crearé',
            r'necesitamos crear',
            r'también necesitamos',
            r'siguiente paso',
            r'continuaré con',
            r'more files',
            r'next file',
        ]
        
        text_lower = text.lower()
        for pattern in continuation_patterns:
            if re.search(pattern, text_lower):
                return True
        return False

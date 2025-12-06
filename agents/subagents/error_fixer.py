"""
ErrorFixer subagent - Fixes compilation and runtime errors.
"""
from agents.base_agent import BaseAgent
from agents.prompts import ERROR_FIXER_PROMPT, ACTION_FORMAT


class ErrorFixerAgent(BaseAgent):
    """Agent specialized in fixing Java errors."""
    
    def __init__(self):
        super().__init__(
            name="ErrorFixer",
            system_prompt=ERROR_FIXER_PROMPT + ACTION_FORMAT
        )
    
    def fix_error(self, error_message: str, project_files: dict) -> dict:
        """
        Analyze and fix an error in the project.
        
        Args:
            error_message: The compilation/runtime error
            project_files: All project files
        
        Returns:
            dict with {response, actions, needs_continuation}
        """
        context = "=== PROYECTO COMPLETO ===\n"
        for fname, code in project_files.items():
            context += f"\n--- {fname} ---\n```java\n{code}\n```\n"
        context += "=== FIN DEL PROYECTO ===\n"
        
        user_message = f"Error de compilación/ejecución:\n{error_message}\n\nCorrige el error."
        
        return self.run(user_message, context=context, max_tokens=16384)
    
    def explain_error(self, error_message: str, code: str) -> dict:
        """
        Explain an error without necessarily fixing it.
        
        Args:
            error_message: The error to explain
            code: The code with the error
        
        Returns:
            dict with explanation
        """
        context = f"```java\n{code}\n```"
        user_message = f"Explica este error (sin corregirlo aún):\n{error_message}"
        
        return self.run(user_message, context=context, max_tokens=4096)

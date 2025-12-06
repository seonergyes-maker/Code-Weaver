"""
CodeWriter subagent - Generates new Java code.
"""
from agents.base_agent import BaseAgent
from agents.prompts import CODE_WRITER_PROMPT, ACTION_FORMAT


class CodeWriterAgent(BaseAgent):
    """Agent specialized in writing new Java code."""
    
    def __init__(self):
        super().__init__(
            name="CodeWriter",
            system_prompt=CODE_WRITER_PROMPT + ACTION_FORMAT
        )
    
    def generate_code(self, description: str, project_files: dict = None) -> dict:
        """
        Generate new Java code based on description.
        
        Args:
            description: What to create
            project_files: Existing project files for context
        
        Returns:
            dict with {response, actions, needs_continuation}
        """
        context = ""
        if project_files:
            context = "=== PROYECTO EXISTENTE ===\n"
            for fname, code in project_files.items():
                context += f"\n--- {fname} ---\n```java\n{code}\n```\n"
            context += "=== FIN DEL PROYECTO ===\n"
            context += "\nConsidera el código existente para mantener consistencia."
        
        return self.run(description, context=context, max_tokens=16384)

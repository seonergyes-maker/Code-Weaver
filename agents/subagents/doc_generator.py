"""
DocGenerator subagent - Creates JavaDoc documentation.
"""
from agents.base_agent import BaseAgent
from agents.prompts import DOC_GENERATOR_PROMPT, ACTION_FORMAT


class DocGeneratorAgent(BaseAgent):
    """Agent specialized in generating JavaDoc."""
    
    def __init__(self):
        super().__init__(
            name="DocGenerator",
            system_prompt=DOC_GENERATOR_PROMPT + ACTION_FORMAT
        )
    
    def generate_docs(self, filename: str, code: str) -> dict:
        """
        Generate JavaDoc for a Java file.
        
        Args:
            filename: Name of the file
            code: Code to document
        
        Returns:
            dict with {response, actions}
        """
        context = f"Archivo: {filename}"
        user_message = f"Genera JavaDoc profesional para este código:\n```java\n{code}\n```"
        
        return self.run(user_message, context=context, max_tokens=16384)

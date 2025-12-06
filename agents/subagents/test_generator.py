"""
TestGenerator subagent - Creates JUnit tests.
"""
from agents.base_agent import BaseAgent
from agents.prompts import TEST_GENERATOR_PROMPT, ACTION_FORMAT


class TestGeneratorAgent(BaseAgent):
    """Agent specialized in generating JUnit tests."""
    
    def __init__(self):
        super().__init__(
            name="TestGenerator",
            system_prompt=TEST_GENERATOR_PROMPT + ACTION_FORMAT
        )
    
    def generate_tests(self, class_name: str, class_code: str, project_files: dict = None) -> dict:
        """
        Generate JUnit 5 tests for a class.
        
        Args:
            class_name: Name of the class to test
            class_code: Code of the class
            project_files: Other project files for context
        
        Returns:
            dict with {response, actions}
        """
        context = f"=== CLASE A TESTEAR ===\n```java\n{class_code}\n```\n"
        
        if project_files:
            context += "\n=== OTRAS CLASES DEL PROYECTO ===\n"
            for fname, code in project_files.items():
                if fname != f"{class_name}.java":
                    context += f"\n--- {fname} ---\n```java\n{code[:1000]}...\n```\n"
        
        user_message = f"Genera tests JUnit 5 completos para la clase {class_name}."
        
        return self.run(user_message, context=context, max_tokens=8192)

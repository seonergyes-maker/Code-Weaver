"""
Architect subagent - Plans and designs projects.
"""
import json
import re
from agents.base_agent import BaseAgent
from agents.prompts import ARCHITECT_PROMPT


class ArchitectAgent(BaseAgent):
    """Agent specialized in project architecture and planning."""
    
    def __init__(self):
        super().__init__(
            name="Architect",
            system_prompt=ARCHITECT_PROMPT
        )
    
    def analyze_and_plan(self, description: str, project_files: dict = None) -> dict:
        """
        Analyze requirements and create implementation plan.
        
        Args:
            description: What the user wants to build
            project_files: Existing project files
        
        Returns:
            dict with {analysis, files_needed, implementation_plan, etc.}
        """
        context = ""
        if project_files:
            context = "=== PROYECTO ACTUAL ===\n"
            for fname, code in project_files.items():
                truncated = code[:2000] + '...' if len(code) > 2000 else code
                context += f"\n--- {fname} ---\n```java\n{truncated}\n```\n"
            context += "=== FIN DEL PROYECTO ===\n"
        
        result = self.run(description, context=context, max_tokens=4096)
        
        plan = self._parse_plan(result["response"])
        result["plan"] = plan
        
        return result
    
    def _parse_plan(self, response_text: str) -> dict:
        """Parse the architecture plan from response."""
        json_match = re.search(r'```json\s*(\{[\s\S]*?\})\s*```', response_text)
        if json_match:
            try:
                return json.loads(json_match.group(1))
            except json.JSONDecodeError:
                pass
        
        try:
            start = response_text.find('{')
            end = response_text.rfind('}') + 1
            if start != -1 and end > start:
                return json.loads(response_text[start:end])
        except json.JSONDecodeError:
            pass
        
        return {
            "analysis": response_text,
            "files_needed": [],
            "implementation_plan": [],
            "estimated_complexity": "desconocida",
            "recommendations": []
        }
    
    def suggest_improvements(self, project_files: dict) -> dict:
        """
        Suggest architectural improvements for the project.
        
        Args:
            project_files: All project files
        
        Returns:
            dict with suggestions
        """
        context = "=== PROYECTO A ANALIZAR ===\n"
        for fname, code in project_files.items():
            context += f"\n--- {fname} ---\n```java\n{code}\n```\n"
        context += "=== FIN DEL PROYECTO ===\n"
        
        user_message = "Analiza este proyecto y sugiere mejoras arquitectónicas."
        
        return self.run(user_message, context=context, max_tokens=4096)

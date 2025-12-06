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
        
        # Format response for human readability
        result["response"] = self._format_plan_response(plan)
        
        return result
    
    def _format_plan_response(self, plan: dict) -> str:
        """Format the plan as a readable response for the user."""
        lines = []
        
        # Analysis section
        if plan.get("analysis"):
            lines.append("## 📋 Análisis del Proyecto\n")
            lines.append(plan["analysis"])
            lines.append("")
        
        # Files needed section
        if plan.get("files_needed"):
            lines.append("## 📁 Archivos a Crear\n")
            for i, f in enumerate(plan["files_needed"], 1):
                name = f.get("name", "Archivo")
                purpose = f.get("purpose", "")
                deps = f.get("dependencies", [])
                lines.append(f"**{i}. {name}**")
                if purpose:
                    lines.append(f"   - Propósito: {purpose}")
                if deps:
                    lines.append(f"   - Dependencias: {', '.join(deps)}")
            lines.append("")
        
        # Implementation plan section
        if plan.get("implementation_plan"):
            lines.append("## 🛠️ Plan de Implementación\n")
            for step in plan["implementation_plan"]:
                step_num = step.get("step", "")
                desc = step.get("description", "")
                files = step.get("files", [])
                lines.append(f"**Paso {step_num}:** {desc}")
                if files:
                    lines.append(f"   - Archivos: {', '.join(files)}")
            lines.append("")
        
        # Complexity
        if plan.get("estimated_complexity"):
            complexity = plan["estimated_complexity"]
            emoji = "🟢" if complexity == "baja" else "🟡" if complexity == "media" else "🔴"
            lines.append(f"**Complejidad estimada:** {emoji} {complexity.capitalize()}\n")
        
        # Recommendations
        if plan.get("recommendations"):
            lines.append("## 💡 Recomendaciones\n")
            for rec in plan["recommendations"]:
                lines.append(f"- {rec}")
            lines.append("")
        
        lines.append("\n---\n*Escribe 'comienza' para que empiece a crear los archivos.*")
        
        return "\n".join(lines) if lines else plan.get("analysis", "Plan generado.")
    
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

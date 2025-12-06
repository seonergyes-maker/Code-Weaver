"""
Coordinator Agent - Routes requests to specialized subagents.
"""
import json
import re
from typing import Optional
from agents.base_agent import BaseAgent, client, is_rate_limit_error
from agents.prompts import INTENT_CLASSIFIER_PROMPT, COORDINATOR_PROMPT
from agents.subagents import (
    CodeWriterAgent,
    ErrorFixerAgent,
    TestGeneratorAgent,
    DocGeneratorAgent,
    ArchitectAgent
)
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception


class CoordinatorAgent:
    """
    Main coordinator that analyzes user intent and routes to specialized subagents.
    """
    
    INTENTS = {
        'WRITE_CODE': 'code_writer',
        'MODIFY_CODE': 'code_writer',
        'FIX_ERROR': 'error_fixer',
        'GENERATE_TESTS': 'test_generator',
        'GENERATE_DOCS': 'doc_generator',
        'ARCHITECT': 'architect',
        'EXPLAIN': 'general',
        'SEARCH': 'general',
        'MULTI_STEP': 'multi_step'
    }
    
    def __init__(self):
        self.code_writer = CodeWriterAgent()
        self.error_fixer = ErrorFixerAgent()
        self.test_generator = TestGeneratorAgent()
        self.doc_generator = DocGeneratorAgent()
        self.architect = ArchitectAgent()
    
    def _extract_text_from_response(self, response) -> str:
        """Safely extract text from Anthropic response, handling various block types."""
        if not response.content:
            return ""
        
        text_parts = []
        for block in response.content:
            if hasattr(block, 'text'):
                text_parts.append(block.text)
            elif hasattr(block, 'type') and block.type == 'text':
                text_parts.append(getattr(block, 'text', ''))
            else:
                text_parts.append(str(block))
        
        return '\n'.join(text_parts) if text_parts else ""
    
    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=1, max=16),
        retry=retry_if_exception(is_rate_limit_error),
        reraise=True
    )
    def analyze_intent(self, user_message: str, project_context: str = "") -> dict:
        """
        Analyze user message to determine intent.
        
        Returns:
            dict with {intent, confidence, details}
        """
        default_intent = {
            "intent": "WRITE_CODE",
            "confidence": 0.5,
            "details": "Could not parse intent, defaulting to code writing"
        }
        
        context = ""
        if project_context:
            context = f"\n\nContexto del proyecto:\n{project_context}"
        
        try:
            response = client.messages.create(
                model="claude-haiku-4-5",
                max_tokens=512,
                system=INTENT_CLASSIFIER_PROMPT + context,
                messages=[{"role": "user", "content": user_message}]
            )
            
            response_text = self._extract_text_from_response(response)
            
            if not response_text:
                return default_intent
            
            if '{' in response_text:
                start = response_text.find('{')
                end = response_text.rfind('}') + 1
                if end > start:
                    return json.loads(response_text[start:end])
        except json.JSONDecodeError:
            pass
        except Exception:
            pass
        
        return default_intent
    
    def route_request(
        self,
        user_message: str,
        project_files: dict = None,
        chat_history: list = None,
        error_message: str = None,
        target_file: str = None
    ) -> dict:
        """
        Main entry point - analyzes intent and routes to appropriate subagent.
        
        Args:
            user_message: The user's request
            project_files: All project files {filename: code}
            chat_history: Previous chat messages
            error_message: If there's an error to fix
            target_file: Specific file to operate on
        
        Returns:
            dict with {response, actions, needs_continuation, agent_used, intent}
        """
        project_files = project_files or {}
        chat_history = chat_history or []
        
        # Check if user wants to start executing a previous plan
        if self._is_start_command(user_message):
            plan = self._extract_plan_from_history(chat_history)
            if plan and plan.get("files_needed"):
                return self._execute_plan(plan, project_files)
        
        project_summary = f"Archivos: {', '.join(project_files.keys())}" if project_files else "Proyecto vacío"
        
        if error_message:
            intent_result = {"intent": "FIX_ERROR", "confidence": 1.0, "details": "Error explícito"}
        else:
            intent_result = self.analyze_intent(user_message, project_summary)
        
        intent = intent_result.get("intent", "WRITE_CODE")
        
        result = self._execute_by_intent(
            intent=intent,
            user_message=user_message,
            project_files=project_files,
            error_message=error_message,
            target_file=target_file,
            chat_history=chat_history
        )
        
        result["intent"] = intent
        result["intent_confidence"] = intent_result.get("confidence", 0.5)
        
        return result
    
    def _execute_by_intent(
        self,
        intent: str,
        user_message: str,
        project_files: dict,
        error_message: str = None,
        target_file: str = None,
        chat_history: list = None
    ) -> dict:
        """Route to the appropriate subagent based on intent."""
        
        if intent == "FIX_ERROR":
            error_msg = error_message or user_message
            return self.error_fixer.fix_error(error_msg, project_files)
        
        elif intent in ["WRITE_CODE", "MODIFY_CODE"]:
            return self.code_writer.generate_code(user_message, project_files)
        
        elif intent == "GENERATE_TESTS":
            if target_file and target_file in project_files:
                class_name = target_file.replace('.java', '')
                return self.test_generator.generate_tests(
                    class_name,
                    project_files[target_file],
                    project_files
                )
            else:
                return self.code_writer.generate_code(
                    f"Genera tests JUnit 5 para: {user_message}",
                    project_files
                )
        
        elif intent == "GENERATE_DOCS":
            if target_file and target_file in project_files:
                return self.doc_generator.generate_docs(
                    target_file,
                    project_files[target_file]
                )
            else:
                return self.code_writer.generate_code(
                    f"Genera JavaDoc para: {user_message}",
                    project_files
                )
        
        elif intent == "ARCHITECT":
            return self.architect.analyze_and_plan(user_message, project_files)
        
        elif intent == "MULTI_STEP":
            return self._handle_multi_step(user_message, project_files, chat_history)
        
        else:
            return self._general_response(user_message, project_files, chat_history)
    
    def _handle_multi_step(
        self,
        user_message: str,
        project_files: dict,
        chat_history: list = None
    ) -> dict:
        """Handle complex multi-step tasks."""
        plan_result = self.architect.analyze_and_plan(user_message, project_files)
        
        plan = plan_result.get("plan", {})
        files_needed = plan.get("files_needed", [])
        
        if files_needed:
            first_file = files_needed[0]
            file_desc = f"Crea {first_file['name']}: {first_file.get('purpose', '')}"
            code_result = self.code_writer.generate_code(file_desc, project_files)
            
            response = f"**Plan de Implementación:**\n{plan_result['response']}\n\n"
            response += f"**Creando primer archivo:**\n{code_result['response']}"
            
            return {
                "response": response,
                "actions": code_result.get("actions", []),
                "needs_continuation": len(files_needed) > 1,
                "agent": "MultiStep(Architect+CodeWriter)",
                "remaining_files": files_needed[1:] if len(files_needed) > 1 else []
            }
        
        return plan_result
    
    def _general_response(
        self,
        user_message: str,
        project_files: dict,
        chat_history: list = None
    ) -> dict:
        """Handle general queries (explanations, searches, etc.)."""
        context = ""
        if project_files:
            context = "=== PROYECTO ===\n"
            for fname, code in project_files.items():
                truncated = code[:2000] + '...' if len(code) > 2000 else code
                context += f"\n--- {fname} ---\n```java\n{truncated}\n```\n"
        
        general_agent = BaseAgent(
            name="General",
            system_prompt="""Eres un experto desarrollador Java. Responde preguntas, 
explica conceptos y ayuda con el código. No generes acciones de archivo 
a menos que se te pida explícitamente crear o modificar código."""
        )
        
        return general_agent.run(user_message, context=context)
    
    def continue_generation(
        self,
        previous_response: str,
        project_files: dict,
        remaining_files: list = None
    ) -> dict:
        """
        Continue generating files when a multi-file task is in progress.
        
        Args:
            previous_response: The previous AI response
            project_files: Updated project files
            remaining_files: List of remaining files to create
        
        Returns:
            dict with next file's response and actions
        """
        if remaining_files and len(remaining_files) > 0:
            next_file = remaining_files[0]
            file_desc = f"Continúa creando {next_file['name']}: {next_file.get('purpose', '')}"
            result = self.code_writer.generate_code(file_desc, project_files)
            result["remaining_files"] = remaining_files[1:] if len(remaining_files) > 1 else []
            result["needs_continuation"] = len(remaining_files) > 1
            return result
        
        continuation_msg = "Continúa con el siguiente archivo del proyecto."
        return self.code_writer.generate_code(continuation_msg, project_files)
    
    def fix_compilation_error(self, error_message: str, project_files: dict) -> dict:
        """
        Direct method to fix compilation errors (for auto-fix feature).
        
        Args:
            error_message: The compilation error
            project_files: All project files
        
        Returns:
            dict with {response, actions, needs_continuation}
        """
        return self.error_fixer.fix_error(error_message, project_files)
    
    def generate_tests_for_class(self, class_name: str, project_files: dict) -> dict:
        """
        Direct method to generate tests for a specific class.
        
        Args:
            class_name: Name of the class (without .java)
            project_files: All project files
        
        Returns:
            dict with test code
        """
        filename = f"{class_name}.java"
        if filename in project_files:
            return self.test_generator.generate_tests(
                class_name,
                project_files[filename],
                project_files
            )
        return {
            "response": f"No se encontró la clase {class_name}",
            "actions": [],
            "agent": "TestGenerator"
        }
    
    def generate_javadoc(self, filename: str, project_files: dict) -> dict:
        """
        Direct method to generate JavaDoc for a file.
        
        Args:
            filename: The file to document
            project_files: All project files
        
        Returns:
            dict with documented code
        """
        if filename in project_files:
            return self.doc_generator.generate_docs(filename, project_files[filename])
        return {
            "response": f"No se encontró el archivo {filename}",
            "actions": [],
            "agent": "DocGenerator"
        }
    
    def _is_start_command(self, message: str) -> bool:
        """Check if the message is a command to start executing a plan."""
        msg_lower = message.strip().lower()
        start_commands = [
            "comienza", "comenzar", "empieza", "empezar",
            "inicia", "iniciar", "start", "go", "hazlo",
            "adelante", "procede", "crea los archivos",
            "crear archivos", "genera", "generar",
            "continua", "continúa", "siguiente", "next",
            "sigue", "seguir"
        ]
        return any(cmd in msg_lower for cmd in start_commands)
    
    def _extract_plan_from_history(self, chat_history: list) -> dict:
        """Extract the most recent plan from chat history."""
        for msg in reversed(chat_history):
            if msg.get("role") == "assistant":
                content = msg.get("content", "")
                # Look for plan indicators in the message
                if "Archivos a Crear" in content or "files_needed" in content:
                    # Try to extract structured plan from formatted response
                    return self._parse_plan_from_response(content)
        return None
    
    def _parse_plan_from_response(self, content: str) -> dict:
        """Parse a plan from a formatted assistant response."""
        plan = {"files_needed": [], "implementation_plan": []}
        
        # Extract file names from "**N. FileName.java**" pattern
        import re
        file_matches = re.findall(r'\*\*\d+\.\s+(\w+\.java)\*\*', content)
        
        # Also try to extract purpose from the lines after file name
        lines = content.split('\n')
        current_file = None
        
        for i, line in enumerate(lines):
            match = re.match(r'\*\*\d+\.\s+(\w+\.java)\*\*', line)
            if match:
                current_file = {"name": match.group(1), "purpose": "", "dependencies": []}
                # Look for purpose in next lines
                for j in range(i+1, min(i+4, len(lines))):
                    if "Propósito:" in lines[j]:
                        current_file["purpose"] = lines[j].split("Propósito:")[-1].strip()
                        break
                    elif lines[j].strip().startswith("-") and ":" not in lines[j]:
                        current_file["purpose"] = lines[j].strip("- ").strip()
                        break
                plan["files_needed"].append(current_file)
        
        # If pattern didn't work, use the simple file names
        if not plan["files_needed"] and file_matches:
            plan["files_needed"] = [{"name": f, "purpose": ""} for f in file_matches]
        
        return plan if plan["files_needed"] else None
    
    def _execute_plan(self, plan: dict, project_files: dict) -> dict:
        """Execute a plan by creating the files one by one."""
        files_needed = plan.get("files_needed", [])
        
        if not files_needed:
            return {
                "response": "No hay archivos pendientes por crear.",
                "actions": [],
                "agent": "Coordinator"
            }
        
        # Create the first file
        first_file = files_needed[0]
        file_name = first_file.get("name", "Archivo.java")
        purpose = first_file.get("purpose", "")
        
        # Build a detailed prompt for code generation
        prompt = f"Crea el archivo {file_name}"
        if purpose:
            prompt += f": {purpose}"
        
        # Add context about the overall plan
        if len(files_needed) > 1:
            other_files = ", ".join([f.get("name", "") for f in files_needed[1:5]])
            prompt += f"\n\nEste archivo es parte de un proyecto que también incluirá: {other_files}"
        
        result = self.code_writer.generate_code(prompt, project_files)
        
        remaining = files_needed[1:] if len(files_needed) > 1 else []
        
        # Build response message
        if remaining:
            result["response"] = f"✅ **Creando {file_name}**\n\n{result.get('response', '')}"
            result["response"] += f"\n\n---\n📋 *Archivos restantes: {len(remaining)}*\n"
            result["response"] += "*Escribe 'continúa' para crear el siguiente archivo.*"
            result["needs_continuation"] = True
            result["remaining_files"] = remaining
        else:
            result["response"] = f"✅ **Archivo creado: {file_name}**\n\n{result.get('response', '')}"
            result["needs_continuation"] = False
        
        result["agent"] = "Coordinator→CodeWriter"
        result["intent"] = "EXECUTE_PLAN"
        
        return result
    
    def plan_project(self, description: str, project_files: dict = None) -> dict:
        """
        Direct method to plan a project architecture.
        
        Args:
            description: What to build
            project_files: Existing project files
        
        Returns:
            dict with architecture plan
        """
        return self.architect.analyze_and_plan(description, project_files or {})

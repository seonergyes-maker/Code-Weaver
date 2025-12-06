"""
Specialized subagents for the Java IDE.
"""
from agents.subagents.code_writer import CodeWriterAgent
from agents.subagents.error_fixer import ErrorFixerAgent
from agents.subagents.test_generator import TestGeneratorAgent
from agents.subagents.doc_generator import DocGeneratorAgent
from agents.subagents.architect import ArchitectAgent

__all__ = [
    'CodeWriterAgent',
    'ErrorFixerAgent', 
    'TestGeneratorAgent',
    'DocGeneratorAgent',
    'ArchitectAgent'
]

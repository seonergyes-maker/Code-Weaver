"""
Agent Architecture for Java IDE with Claude AI

This package implements a coordinator/subagent architecture where:
- CoordinatorAgent analyzes user intent and routes to specialized subagents
- Each subagent handles a specific type of task (code writing, error fixing, etc.)
"""

from agents.coordinator import CoordinatorAgent

__all__ = ['CoordinatorAgent']

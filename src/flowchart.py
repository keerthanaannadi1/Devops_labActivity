import os
import json
import networkx as nx
from typing import List, Dict, Optional
from langchain_groq import ChatGroq
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_core.messages import HumanMessage, SystemMessage
from dotenv import load_dotenv
from json_repair import repair_json


load_dotenv()


class FlowchartGenerator:
    def __init__(
        self,
        model_name: str = "llama-3.1-8b-instant",
        temperature: float = 0.2
    ):
        api_key = os.getenv("GROQ_API_KEY")
        if not api_key:
            raise ValueError("GROQ_API_KEY not found in environment variables")
        
        self.llm = ChatGroq(
            api_key=api_key,
            model=model_name,
            temperature=temperature,
            request_timeout=30
        )
        
        self.detection_prompt = ChatPromptTemplate.from_template(
            """Analyze the following document text and identify any content that can be represented as a flowchart. This could include:
- Step-by-step instructions or procedures
- Workflows or processes
- Methodologies or algorithms
- Decision trees or branching logic
- Any numbered or bulleted lists describing a sequence

Return a JSON object with:
- "has_process": true/false (set to true if there's ANY sequential content, even if imperfect)
- "process_summary": brief description of what you found
- "relevant_section": the most process-like portion of the text (even a partial section is fine)
- "confidence": 0.0 to 1.0 (be lenient - 0.5+ is enough)

Document Text:
{text}

Return ONLY valid JSON."""
        )
        
        self.extraction_prompt = ChatPromptTemplate.from_template(
            """Extract the steps from the following process description and create a structured flowchart JSON.

Process Description:
{text}

Return a JSON object with this exact structure:
{{
  "title": "Process Title",
  "steps": [
    {{
      "id": "step_1",
      "label": "Step description",
      "type": "process|decision|start|end|input"
    }},
    ...
  ],
  "connections": [
    {{"from": "step_1", "to": "step_2", "label": "yes/no/condition (optional)"}},
    ...
  ]
}}

Rules:
- "start" type for beginning steps
- "end" type for final steps
- "decision" type for conditional branches (must have multiple outgoing connections)
- "process" type for regular steps
- "input" type for data inputs
- Ensure all connections create a valid directed graph
- Maximum 15 steps for clarity
- Use clear, action-oriented labels

Return ONLY valid JSON."""
        )
        
        self.editing_prompt = ChatPromptTemplate.from_template(
            """You are editing a flowchart. The current flowchart is:

Current Flowchart JSON:
{current_json}

User's edit instruction: {instruction}

Apply the edit and return the updated JSON in the same format. Return ONLY valid JSON."""
        )
    
    def detect_process(self, text: str, num_chars: int = 3000) -> Dict:
        text_sample = text[:num_chars]
        
        chain = self.detection_prompt | self.llm | StrOutputParser()
        
        try:
            response = chain.invoke({"text": text_sample})
            result = repair_json(response)
            
            if isinstance(result, list):
                if len(result) > 0:
                    result = result[0]
                else:
                    return {"has_process": False, "error": "Empty list returned"}
            
            if not isinstance(result, dict):
                result = json.loads(result)
                
            return result
        except Exception as e:
            return {
                "has_process": False,
                "error": str(e)
            }
    
    def extract_steps(self, process_text: str) -> Dict:
        chain = self.extraction_prompt | self.llm | StrOutputParser()
        
        try:
            response = chain.invoke({"text": process_text})
            result = repair_json(response)
            
            if isinstance(result, list):
                if len(result) > 0:
                    result = result[0]
                else:
                    raise ValueError("Empty list returned from LLM")
            
            if not isinstance(result, dict):
                result = json.loads(result)
                
            return result
        except Exception as e:
            raise ValueError(f"Failed to extract flowchart steps: {str(e)}")
    
    def create_networkx_graph(self, flowchart_data: Dict) -> nx.DiGraph:
        G = nx.DiGraph()
        
        title = flowchart_data.get("title", "Flowchart")
        G.graph["title"] = title
        
        for step in flowchart_data.get("steps", []):
            G.add_node(
                step["id"],
                label=step.get("label", step["id"]),
                step_type=step.get("type", "process")
            )
        
        for conn in flowchart_data.get("connections", []):
            G.add_edge(
                conn["from"],
                conn["to"],
                label=conn.get("label", "")
            )
        
        return G
    
    def to_mermaid(self, flowchart_data: Dict) -> str:
        G = self.create_networkx_graph(flowchart_data)
        
        lines = ["flowchart TD"]
        
        for node, data in G.nodes(data=True):
            node_type = data.get("step_type", "process")
            label = data.get("label", node)
            
            shape = self._get_mermaid_shape(node_type)
            if node_type in ["start", "end"]:
                lines.append(f'    {node}("{label}")')
            elif node_type == "decision":
                lines.append(f'    {node}{{{label}}}')
            elif node_type == "input":
                lines.append(f'    {node}[/{label}/]')
            else:
                lines.append(f'    {node}["{label}"]')
        
        for u, v, data in G.edges(data=True):
            label = data.get("label", "")
            if label:
                lines.append(f'    {u} -- "{label}" --> {v}')
            else:
                lines.append(f'    {u} --> {v}')
        
        return "\n".join(lines)
    
    def _get_mermaid_shape(self, step_type: str) -> str:
        shapes = {
            "start": "()",
            "end": "()",
            "decision": "{{}}",
            "input": "(())",
            "process": "[]"
        }
        return shapes.get(step_type, "[]")
    
    def edit_flowchart(
        self,
        current_json: Dict,
        instruction: str
    ) -> Dict:
        chain = self.editing_prompt | self.llm | StrOutputParser()
        
        try:
            response = chain.invoke({
                "current_json": json.dumps(current_json, indent=2),
                "instruction": instruction
            })
            result = json.loads(repair_json(response))
            return result
        except Exception as e:
            raise ValueError(f"Failed to edit flowchart: {str(e)}")
    
    def validate_graph(self, flowchart_data: Dict) -> bool:
        try:
            G = self.create_networkx_graph(flowchart_data)
            
            if G.number_of_nodes() == 0:
                return False
            
            if not nx.is_weakly_connected(G):
                return False
            
            has_start = any(
                G.nodes[n].get("step_type") == "start" 
                for n in G.nodes()
            )
            has_end = any(
                G.nodes[n].get("step_type") == "end" 
                for n in G.nodes()
            )
            
            return has_start and has_end
        except:
            return False
    
    def get_graph_info(self, flowchart_data: Dict) -> Dict:
        G = self.create_networkx_graph(flowchart_data)
        
        return {
            "num_nodes": G.number_of_nodes(),
            "num_edges": G.number_of_edges(),
            "is_connected": nx.is_weakly_connected(G),
            "title": flowchart_data.get("title", "Untitled"),
            "step_types": {
                node: G.nodes[node].get("step_type", "process")
                for node in G.nodes()
            }
        }

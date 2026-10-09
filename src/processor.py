import os
import hashlib
from typing import Dict, Optional, List
from src.pdf_parser import PDFParser
from src.chunker import ChunkingEngine
from src.retriever import HybridRetriever
from src.rag_chain import RAGChain
from src.flowchart import FlowchartGenerator


class DocumentProcessor:
    def __init__(self):
        self.pdf_parser = PDFParser()
        self.chunker = ChunkingEngine(chunk_size=500, overlap=50)
        self.retriever = HybridRetriever(embedding_model="all-MiniLM-L6-v2")
        self.rag_chain = RAGChain()
        self.flowchart_gen = FlowchartGenerator()
        
        self.current_document = None
        self.current_chunks = []
        self.chat_history = []
    
    def load_document(self, pdf_path: str) -> Dict:
        if not os.path.exists(pdf_path):
            raise FileNotFoundError(f"PDF not found: {pdf_path}")
        
        parsed = self.pdf_parser.parse_pdf(pdf_path)
        
        file_hash = hashlib.md5(open(pdf_path, 'rb').read()).hexdigest()[:8]
        metadata = {
            "filename": os.path.basename(pdf_path),
            "file_hash": file_hash,
            "num_pages": parsed["num_pages"]
        }
        
        self.current_chunks = self.chunker.chunk_with_metadata(
            parsed["full_text"],
            metadata=metadata
        )
        
        self.retriever.fit(self.current_chunks)
        
        self.current_document = {
            "path": pdf_path,
            "metadata": metadata,
            "parsed": parsed,
            "num_chunks": len(self.current_chunks)
        }
        
        self.chat_history = []
        
        return self.current_document
    
    def ask_question(self, question: str) -> Dict:
        if not self.current_document:
            raise RuntimeError("No document loaded. Please upload a PDF first.")
        
        retrieved = self.retriever.search(question, k=5)
        
        answer_data = self.rag_chain.answer(
            question=question,
            retrieved_chunks=retrieved,
            chat_history=self.chat_history
        )
        
        self.chat_history.append({"role": "user", "content": question})
        self.chat_history.append({"role": "assistant", "content": answer_data["answer"]})
        
        return answer_data
    
    def detect_and_generate_flowchart(self, custom_text: Optional[str] = None) -> Dict:
        if not self.current_document and not custom_text:
            raise RuntimeError("No document loaded.")
        
        text_to_analyze = custom_text or self.current_document["parsed"]["full_text"]
        
        detection = self.flowchart_gen.detect_process(text_to_analyze)
        
        has_process = detection.get("has_process", False)
        confidence = detection.get("confidence", 0.0)
        
        if not has_process and confidence < 0.3:
            return {
                "success": False,
                "detection": detection,
                "message": "No clear process/workflow found in the document. Try providing custom text."
            }
        
        if not has_process and confidence >= 0.3:
            has_process = True
        
        if has_process:
            flowchart_data = self.flowchart_gen.extract_steps(detection["relevant_section"])
        else:
            flowchart_data = self.flowchart_gen.extract_steps(text_to_analyze)
        
        is_valid = self.flowchart_gen.validate_graph(flowchart_data)
        
        mermaid_code = self.flowchart_gen.to_mermaid(flowchart_data)
        
        graph_info = self.flowchart_gen.get_graph_info(flowchart_data)
        
        return {
            "success": True,
            "flowchart_data": flowchart_data,
            "mermaid_code": mermaid_code,
            "graph_info": graph_info,
            "detection": detection,
            "is_valid": is_valid
        }
    
    def edit_flowchart(self, current_flowchart: Dict, instruction: str) -> Dict:
        try:
            updated = self.flowchart_gen.edit_flowchart(current_flowchart, instruction)
            
            is_valid = self.flowchart_gen.validate_graph(updated)
            mermaid_code = self.flowchart_gen.to_mermaid(updated)
            graph_info = self.flowchart_gen.get_graph_info(updated)
            
            return {
                "success": is_valid,
                "flowchart_data": updated,
                "mermaid_code": mermaid_code,
                "graph_info": graph_info,
                "error": None if is_valid else "Invalid flowchart structure"
            }
        except Exception as e:
            return {
                "success": False,
                "error": str(e)
            }
    
    def get_document_summary(self) -> Dict:
        if not self.current_document:
            return {}
        
        return {
            "filename": self.current_document["metadata"]["filename"],
            "num_pages": self.current_document["metadata"]["num_pages"],
            "num_chunks": self.current_document["num_chunks"],
            "chat_history_length": len(self.chat_history) // 2
        }
    
    def clear_document(self) -> None:
        self.current_document = None
        self.current_chunks = []
        self.chat_history = []

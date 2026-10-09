import re
from typing import List, Dict, Optional
import uuid


class ChunkingEngine:
    HEADING_PATTERNS = [
        r'^#{1,6}\s+.+',
        r'^\d+\.\s+[A-Z].+',
        r'^[A-Z][A-Z\s]{5,}:?\s*$',
        r'^(Abstract|Introduction|Methods|Results|Discussion|Conclusion|References|Bibliography|Acknowledgments)',
        r'^\s*(Step|Phase|Stage|Chapter|Section)\s+\d+',
    ]
    
    def __init__(
        self,
        chunk_size: int = 500,
        overlap: int = 50,
        min_chunk_size: int = 100
    ):
        self.chunk_size = chunk_size
        self.overlap = overlap
        self.min_chunk_size = min_chunk_size
    
    def detect_headings(self, text: str) -> List[Dict]:
        headings = []
        lines = text.split('\n')
        
        for i, line in enumerate(lines):
            line_stripped = line.strip()
            if not line_stripped:
                continue
            
            for pattern in self.HEADING_PATTERNS:
                if re.match(pattern, line_stripped, re.IGNORECASE):
                    headings.append({
                        "line_index": i,
                        "text": line_stripped,
                        "level": self._get_heading_level(pattern, line_stripped)
                    })
                    break
        
        return headings
    
    def _get_heading_level(self, pattern: str, text: str) -> int:
        if pattern.startswith(r'^#{'):
            match = re.match(r'^#+(?=\s)', text)
            if match:
                return len(match.group())
        
        if re.match(r'^\d+\.\s+', text):
            parts = text.split('.')
            if parts[0].isdigit():
                return int(parts[0]) + 1
        
        return 2
    
    def semantic_chunk(self, text: str) -> List[Dict]:
        chunks = []
        paragraphs = self._split_into_paragraphs(text)
        
        current_chunk = []
        current_size = 0
        current_heading = None
        
        headings = self.detect_headings(text)
        
        for para in paragraphs:
            para_stripped = para.strip()
            if not para_stripped:
                continue
            
            is_heading = any(
                h["text"].lower() in para_stripped.lower() or 
                para_stripped.lower() in h["text"].lower()
                for h in headings
            )
            
            para_size = len(para_stripped.split())
            
            if is_heading and current_chunk and para_size < 20:
                current_heading = para_stripped
                continue
            
            if current_size + para_size > self.chunk_size and current_chunk:
                chunk_text = '\n\n'.join(current_chunk)
                if len(chunk_text.split()) >= self.min_chunk_size:
                    chunks.append({
                        "id": str(uuid.uuid4()),
                        "text": chunk_text,
                        "heading": current_heading,
                        "size": len(chunk_text.split())
                    })
                
                overlap_text = ' '.join(current_chunk[-1].split()[-self.overlap:])
                current_chunk = [overlap_text, para_stripped] if overlap_text else [para_stripped]
                current_size = len(overlap_text.split()) + para_size if overlap_text else para_size
            else:
                current_chunk.append(para_stripped)
                current_size += para_size
        
        if current_chunk:
            chunk_text = '\n\n'.join(current_chunk)
            if len(chunk_text.split()) >= self.min_chunk_size:
                chunks.append({
                    "id": str(uuid.uuid4()),
                    "text": chunk_text,
                    "heading": current_heading,
                    "size": len(chunk_text.split())
                })
        
        for i, chunk in enumerate(chunks):
            chunk["index"] = i
        
        return chunks
    
    def _split_into_paragraphs(self, text: str) -> List[str]:
        text = re.sub(r'\n{3,}', '\n\n', text)
        
        paragraphs = []
        current = []
        
        for line in text.split('\n'):
            stripped = line.strip()
            
            if not stripped:
                if current:
                    paragraphs.append('\n'.join(current))
                    current = []
                continue
            
            if stripped.startswith('--- Page'):
                continue
            
            current.append(stripped)
        
        if current:
            paragraphs.append('\n'.join(current))
        
        return paragraphs
    
    def chunk_with_metadata(self, text: str, metadata: Optional[Dict] = None) -> List[Dict]:
        chunks = self.semantic_chunk(text)
        
        for chunk in chunks:
            if metadata:
                chunk["metadata"] = metadata
        
        return chunks

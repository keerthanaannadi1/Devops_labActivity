import numpy as np
from rank_bm25 import BM25Okapi
from typing import List, Dict, Tuple, Optional
import pickle
import os


class HybridRetriever:
    def __init__(
        self,
        embedding_model: str = "none",
        device: str = "cpu"
    ):
        # Semantic search disabled in Lightweight Mode
        self.embedding_model_name = "Keyword-Only"
        self.dimension = 0
        
        self.index = None
        self.chunks = []
        self.chunk_texts = []
        
        self.bm25 = None
        self.tokenized_corpus = []
        
        self._is_fitted = False
    
    def fit(self, chunks: List[Dict]) -> None:
        if not chunks:
            raise ValueError("No chunks provided for indexing")
        
        self.chunks = chunks
        self.chunk_texts = [chunk["text"] for chunk in chunks]
        
        # We skip FAISS index building in Lightweight Mode
        self._build_bm25_index()
        
        self._is_fitted = True
    
    def _build_bm25_index(self) -> None:
        self.tokenized_corpus = [
            text.lower().split() for text in self.chunk_texts
        ]
        
        if self.tokenized_corpus:
            self.bm25 = BM25Okapi(self.tokenized_corpus)
    
    def search(
        self,
        query: str,
        k: int = 5,
        rrf_k: int = 60
    ) -> List[Dict]:
        if not self._is_fitted:
            raise RuntimeError("Retriever must be fitted before searching")
        
        # In Lightweight Mode, we only use BM25
        bm25_scores, bm25_indices = self._search_bm25(query, k)
        
        results = []
        for i, idx in enumerate(bm25_indices):
            result = self.chunks[idx].copy()
            result["score"] = float(bm25_scores[i])
            result["faiss_score"] = 0.0
            result["bm25_score"] = float(bm25_scores[i])
            results.append(result)
        
        return results
    
    def _search_bm25(
        self,
        query: str,
        k: int
    ) -> Tuple[np.ndarray, np.ndarray]:
        if self.bm25 is None:
            return np.array([]), np.array([])
        
        query_tokens = query.lower().split()
        scores = self.bm25.get_scores(query_tokens)
        
        if len(scores) == 0:
            return np.array([]), np.array([])
        
        top_k = min(k, len(scores))
        # Find indices with non-zero scores first to improve relevance
        top_indices = np.argsort(scores)[::-1][:top_k]
        top_scores = scores[top_indices]
        
        return top_scores, top_indices
    
    def save(self, path: str) -> None:
        os.makedirs(path, exist_ok=True)
        
        with open(os.path.join(path, "bm25.pkl"), "wb") as f:
            pickle.dump({
                "bm25": self.bm25,
                "tokenized_corpus": self.tokenized_corpus
            }, f)
        
        with open(os.path.join(path, "chunks.pkl"), "wb") as f:
            pickle.dump(self.chunks, f)
    
    def load(self, path: str) -> None:
        with open(os.path.join(path, "bm25.pkl"), "rb") as f:
            data = pickle.load(f)
            self.bm25 = data["bm25"]
            self.tokenized_corpus = data["tokenized_corpus"]
        
        with open(os.path.join(path, "chunks.pkl"), "rb") as f:
            self.chunks = pickle.load(f)
            self.chunk_texts = [chunk["text"] for chunk in self.chunks]
        
        self._is_fitted = True

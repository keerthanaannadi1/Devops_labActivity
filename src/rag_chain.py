import os
from typing import List, Dict, Optional
from langchain_groq import ChatGroq
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_core.documents import Document
from dotenv import load_dotenv


load_dotenv()


class RAGChain:
    def __init__(
        self,
        model_name: str = "llama-3.1-8b-instant",
        temperature: float = 0.3,
        max_tokens: int = 1024
    ):
        api_key = os.getenv("GROQ_API_KEY")
        if not api_key:
            raise ValueError("GROQ_API_KEY not found in environment variables")
        
        self.llm = ChatGroq(
            api_key=api_key,
            model=model_name,
            temperature=temperature,
            max_tokens=max_tokens,
            request_timeout=30
        )
        
        self.prompt = ChatPromptTemplate.from_template(
            """You are a helpful assistant that answers questions based on the provided context from documents.
            
Context from the document:
{context}

Chat History:
{chat_history}

Current Question: {question}

Instructions:
1. Answer the question based ONLY on the context provided above.
2. If the context doesn't contain enough information to fully answer the question, say so honestly.
3. Include citations by referencing specific parts of the context (e.g., "According to the document...", "The text states...").
4. Be concise but thorough. If you need to provide multiple points, use bullet points.
5. If the question is unrelated to the document, politely explain that you can only answer questions about the uploaded document.

Answer:"""
        )
        
        self.chain = self.prompt | self.llm | StrOutputParser()
    
    def format_context(self, retrieved_chunks: List[Dict]) -> str:
        context_parts = []
        
        for i, chunk in enumerate(retrieved_chunks, 1):
            heading = chunk.get("heading", "Document Section")
            text = chunk["text"]
            context_parts.append(f"[Source {i}] ({heading}):\n{text}")
        
        return "\n\n---\n\n".join(context_parts)
    
    def format_chat_history(self, chat_history: List[Dict]) -> str:
        if not chat_history:
            return "No previous conversation."
        
        history_parts = []
        for msg in chat_history[-5:]:
            role = "User" if msg["role"] == "user" else "Assistant"
            history_parts.append(f"{role}: {msg['content']}")
        
        return "\n".join(history_parts)
    
    def answer(
        self,
        question: str,
        retrieved_chunks: List[Dict],
        chat_history: Optional[List[Dict]] = None
    ) -> Dict:
        context = self.format_context(retrieved_chunks)
        history = self.format_chat_history(chat_history or [])
        
        response = self.chain.invoke({
            "context": context,
            "question": question,
            "chat_history": history
        })
        
        sources = []
        for chunk in retrieved_chunks:
            sources.append({
                "text": chunk["text"][:200] + "..." if len(chunk["text"]) > 200 else chunk["text"],
                "heading": chunk.get("heading", "Unknown"),
                "score": chunk.get("score", 0),
                "index": chunk.get("index", 0)
            })
        
        return {
            "answer": response,
            "sources": sources,
            "num_sources_used": len(retrieved_chunks)
        }
    
    def generate_followup_suggestions(self, question: str, answer: str) -> List[str]:
        suggestions_prompt = ChatPromptTemplate.from_template(
            """Based on the user's question and your answer, suggest 3 natural follow-up questions they might ask.
            
Question: {question}
Answer: {answer}

Return ONLY a JSON list of 3 questions, nothing else. Example: ["What is X?", "How does Y work?", "Can you explain Z?"]"""
        )
        
        suggestions_chain = suggestions_prompt | self.llm | StrOutputParser()
        
        try:
            suggestions = suggestions_chain.invoke({
                "question": question,
                "answer": answer
            })
            
            import json
            suggestions = json.loads(suggestions)
            return suggestions[:3] if isinstance(suggestions, list) else []
        except:
            return []

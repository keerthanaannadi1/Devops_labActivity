# Document Visualizer
### RAG-Based Document Q&A with Automated Flowchart Generation

---

## 👥 Team

 Name | Roll No |
|------|---------|
| A. Keerthana | 24WH1A05U5 |
| G. Yasasvi | 24WH1A05V9 |
| T. Tejaswini | 24WH1A05R9 |
| P. Diya Reddy | 24WH1A05U4|
| A. Vamshika | 24WH1A05W4|


---

## 📌 Overview

Document Visualizer is an AI-powered web application that helps users quickly understand **any type of document** — research papers, technical reports, legal documents, manuals, and more — through two core features:

### 1. Intelligent Q&A System (RAG-based)
- Upload any document (PDF)
- Ask natural language questions about the document
- Retrieves the most relevant sections using **hybrid search** (semantic + keyword)
- Answers generated using **LLaMA-3** via Groq API with proper citations pointing to the exact source section
- Evaluated using **RAGAS** on 30 QA pairs across 5 diverse documents

### 2. Automated Flowchart Generation
- Automatically identifies the most **process/workflow-like section** of the document
- Converts complex textual content into an **interactive, editable flowchart**
- Users can further edit the flowchart using plain English instructions — no diagramming tools needed
- LLM is forced to output structured **JSON** before rendering, ensuring consistency

---

## 🔍 Technical Approach

### Hybrid Retrieval System
The core innovation — combines two retrieval strategies for higher accuracy:
- **Semantic Search** — sentence-transformer embeddings + FAISS (dense retrieval)
- **Keyword Search** — BM25 (sparse retrieval)
- **Reciprocal Rank Fusion (RRF)** — merges ranked results from both for optimal retrieval

### Indexing & Query Pipeline
**Phase 1 — Indexing:**
1. PDF parsed and cleaned
2. Text split using semantic-aware chunking (with heading detection + fixed-size fallback)
3. Chunks embedded and stored in FAISS
4. BM25 index built in parallel

**Phase 2 — Query:**
1. User query embedded and searched via FAISS
2. Same query searched via BM25
3. RRF merges both ranked lists
4. Top-k chunks passed to LLaMA-3 → cited answer returned

### PDF Handling
Advanced parsing pipeline that handles:
- Two-column layouts
- Tables and equations
- Scanned pages (via OCR)

Tools: PyMuPDF, pdfplumber, Tesseract OCR

### Flowchart Pipeline
1. LLM auto-detects the most process-like section (user can override)
2. LLaMA-3 extracts steps → outputs structured **JSON**
3. JSON converted into a graph using **NetworkX**
4. Rendered as an interactive diagram using **Mermaid.js**
5. Users edit via plain English prompts (e.g., *"add a validation step after step 3"*)

---

## 🎯 Objectives

- [x] Robust PDF parsing pipeline (multi-column, tables, OCR fallback)
- [x] Semantic-aware chunking with heading detection
- [x] Benchmark 3 chunking strategies and 3 embedding models
- [x] Hybrid retrieval (FAISS + BM25 + RRF)
- [x] Automated process/workflow section detection → flowchart conversion
- [x] Conversational (English-based) flowchart editing
- [x] Evaluation using RAGAS on 30 QA pairs from 5 documents
- [x] Live deployment on Hugging Face Spaces

---

## 🛠 Technology Stack

| Layer | Tools / Packages |
|-------|-----------------|
| PDF Parsing | `pymupdf`, `pdfplumber`, `pytesseract` |
| Orchestration | `langchain`, `langchain-groq` |
| Dense Search | `faiss-cpu`, `sentence-transformers` |
| Sparse Search | `rank-bm25` |
| Embeddings (Benchmarked) | `all-MiniLM-L6-v2`, `all-mpnet-base-v2`, `allenai-specter` |
| LLM | Groq API + LLaMA-3 8B |
| Flowchart | `networkx`, Mermaid.js |
| Frontend | `streamlit` |
| Deployment | Hugging Face Spaces |
| Evaluation | `ragas` |

---

## 📚 Literature Review

Built upon:
- **RAG** — Lewis et al., NeurIPS 2020
- **RAGAS** — evaluation framework for RAG pipelines
- **SPECTER** — scientific domain-specific embeddings
- **BM25 + Hybrid Retrieval** — Reciprocal Rank Fusion

**Research Gap Filled:**
No prior work combined hybrid retrieval + automated flowchart generation + RAGAS evaluation for general-purpose document understanding across diverse document types.

---

## 🚀 Getting Started

### Prerequisites
```bash
pip install -r requirements.txt
```

### Run Locally
```bash
streamlit run app.py
```

### Environment Variables
```
GROQ_API_KEY=your_groq_api_key
```

---

## 🌐 Deployment

Deployed as a free web app on **Hugging Face Spaces** using Streamlit.

---

## ✨ Key Benefits

- Works on **any document type** — not limited to research papers
- Saves hours of reading dense documents
- Makes workflows and processes easy to visualize and understand
- Reduces hallucinations through retrieval + citations
- Fully interactive and editable flowchart output
- Free and publicly accessible

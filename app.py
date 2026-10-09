import streamlit as st
import streamlit.components.v1 as components
import os
from dotenv import load_dotenv
from src.processor import DocumentProcessor


load_dotenv()

st.set_page_config(
    page_title="Document Visualizer",
    page_icon="📄",
    layout="wide",
    initial_sidebar_state="collapsed"
)

st.markdown("""
<style>
    @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');
    
    :root {
        --primary: #6366F1;
        --primary-dark: #4F46E5;
        --secondary: #10B981;
        --bg-dark: #0F172A;
        --bg-card: #1E293B;
        --text-primary: #F1F5F9;
        --text-secondary: #94A3B8;
        --border: #334155;
    }
    
    * {
        font-family: 'Inter', sans-serif;
    }
    
    div[data-testid="stChatMessage"] {
        background-color: var(--bg-card) !important;
        border: 1px solid var(--border) !important;
        border-radius: 12px !important;
        padding: 1rem !important;
        margin-bottom: 1rem !important;
    }

    div[data-testid="stChatMessage"] p {
        color: white !important;
    }

    .stApp {
        background: #0F172A;
    }
    
    .main-header {
        font-size: 2.5rem;
        font-weight: 700;
        background: linear-gradient(90deg, #6366F1, #8B5CF6, #EC4899);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        margin-bottom: 0.5rem;
    }
    
    .sub-header {
        color: var(--text-secondary);
        font-size: 1.1rem;
        margin-bottom: 2rem;
    }
    
    .stMarkdown p, .stMarkdown li {
        color: var(--text-primary) !important;
        font-size: 1.05rem;
        line-height: 1.6;
    }
    
    .stMarkdown h1, .stMarkdown h2, .stMarkdown h3, .stMarkdown h4 {
        color: white !important;
        margin-top: 1.5rem !important;
    }

    .upload-section {
        background: rgba(30, 41, 59, 0.7);
        border: 2px dashed var(--primary);
        border-radius: 16px;
        padding: 3rem 2rem;
        text-align: center;
        transition: all 0.3s ease;
        backdrop-filter: blur(10px);
    }
    
    .upload-section:hover {
        border-color: var(--primary);
        transform: translateY(-2px);
    }
    
    .chat-bubble-user {
        background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
        color: white;
        padding: 1rem 1.5rem;
        border-radius: 18px 18px 4px 18px;
        margin-left: 2rem;
        max-width: 80%;
    }
    
    .chat-bubble-assistant {
        background: var(--bg-card);
        border: 1px solid var(--border);
        color: var(--text-primary);
        padding: 1rem 1.5rem;
        border-radius: 18px 18px 18px 4px;
        margin-right: 2rem;
        max-width: 80%;
    }
    
    .source-card {
        background: var(--bg-card);
        border: 1px solid var(--border);
        border-radius: 12px;
        padding: 1rem;
        margin: 0.5rem 0;
    }
    
    .metric-card {
        background: var(--bg-card);
        border-radius: 12px;
        padding: 1.5rem;
        text-align: center;
        border: 1px solid var(--border);
    }
    
    .metric-value {
        font-size: 2rem;
        font-weight: 700;
        color: var(--primary);
    }
    
    .metric-label {
        color: var(--text-secondary);
        font-size: 0.9rem;
        margin-top: 0.5rem;
    }
    
    .stButton > button {
        background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
        color: white;
        border: none;
        border-radius: 10px;
        padding: 0.75rem 1.5rem;
        font-weight: 600;
        transition: all 0.3s ease;
    }
    
    .stButton > button:hover {
        transform: translateY(-2px);
        box-shadow: 0 8px 25px rgba(99, 102, 241, 0.4);
    }
    
    .flowchart-container {
        background: white;
        border-radius: 16px;
        padding: 2rem;
        margin: 1rem 0;
    }
    
    div[data-testid="stExpander"] {
        background: var(--bg-card);
        border-radius: 12px;
        border: 1px solid var(--border);
    }
    
    .tab-content {
        padding: 1rem 0;
    }
    
    .sidebar .stButton > button {
        width: 100%;
    }
    
    .highlight-box {
        background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%);
        border-left: 4px solid var(--primary);
        padding: 1rem;
        border-radius: 0 12px 12px 0;
        margin: 1rem 0;
    }
    
    .status-indicator {
        display: inline-block;
        width: 10px;
        height: 10px;
        border-radius: 50%;
        margin-right: 8px;
    }
    
    .status-ready {
        background: #10B981;
        box-shadow: 0 0 10px #10B981;
    }
    
    .status-processing {
        background: #F59E0B;
        animation: pulse 1.5s infinite;
    }
    
    @keyframes pulse {
        0%, 100% { opacity: 1; }
        50% { opacity: 0.5; }
    }
    
    .feature-card {
        background: var(--bg-card);
        border-radius: 16px;
        padding: 1.5rem;
        border: 1px solid var(--border);
        transition: all 0.3s ease;
    }
    
    .feature-card:hover {
        transform: translateY(-4px);
        border-color: var(--primary);
    }
    
    .feature-icon {
        font-size: 2.5rem;
        margin-bottom: 1rem;
    }
</style>
""", unsafe_allow_html=True)


@st.cache_resource
def get_processor():
    return DocumentProcessor()

def get_processor_instance():
    return get_processor()

if 'current_tab' not in st.session_state:
    st.session_state.current_tab = "qa"

if 'flowchart_data' not in st.session_state:
    st.session_state.flowchart_data = None

if 'messages' not in st.session_state:
    st.session_state.messages = []


def check_api_key():
    if not os.getenv("GROQ_API_KEY"):
        st.error("⚠️ GROQ_API_KEY not found! Please add it to your `.env` file.")
        st.code("GROQ_API_KEY=your_api_key_here")
        return False
    return True


def render_header():
    col1, col2 = st.columns([3, 1])
    with col1:
        st.markdown('<h1 class="main-header">📄 Document Visualizer</h1>', unsafe_allow_html=True)
        st.markdown('<p class="sub-header">AI-powered document Q&A with automated flowchart generation</p>', unsafe_allow_html=True)
    with col2:
        if st.button("🗑️ Clear Document", use_container_width=True):
            get_processor_instance().clear_document()
            st.session_state.messages = []
            st.session_state.flowchart_data = None
            st.rerun()


def render_sidebar():
    with st.sidebar:
        st.markdown("### 📊 Document Status")
        
        summary = get_processor_instance().get_document_summary()
        
        if summary:
            col1, col2 = st.columns(2)
            with col1:
                st.metric("Pages", summary.get("num_pages", 0))
            with col2:
                st.metric("Chunks", summary.get("num_chunks", 0))
            
            st.markdown(f"""
            <div class="highlight-box">
                <strong>📁 {summary.get('filename', 'Unknown')}</strong><br>
                <span style="color: var(--text-secondary)">
                    {len(st.session_state.messages)} Q&A pairs
                </span>
            </div>
            """, unsafe_allow_html=True)
        else:
            st.info("👆 Upload a PDF to get started")
        
        st.markdown("---")
        
        st.markdown("### 🎯 Features")
        st.markdown("""
        - **RAG-based Q&A** - Ask any question about your document
        - **Hybrid Search** - Semantic + keyword retrieval
        - **Flowchart Generation** - Auto-detect processes
        - **Interactive Editing** - Modify flowcharts with plain English
        """)
        
        st.markdown("---")
        
        with st.expander("⚙️ Settings"):
            st.selectbox("Embedding Model", ["all-MiniLM-L6-v2", "all-mpnet-base-v2"])
            st.slider("Retrieval Top-K", 3, 10, 5)
            st.slider("Temperature", 0.0, 1.0, 0.3, 0.1)


def render_upload_section():
    st.markdown('<div class="upload-section">', unsafe_allow_html=True)
    
    col1, col2, col3 = st.columns([1, 2, 1])
    with col2:
        st.markdown("### 📤 Upload Your Document")
        st.markdown("Supports PDF files - research papers, reports, manuals, etc.")
        
        uploaded_file = st.file_uploader(
            "Drag and drop your PDF here",
            type=["pdf"],
            help="Maximum file size: 50MB",
            label_visibility="collapsed"
        )
        
        if uploaded_file:
            with st.spinner("🔄 Processing document..."):
                temp_path = f"temp_{uploaded_file.name}"
                
                with open(temp_path, "wb") as f:
                    f.write(uploaded_file.getbuffer())
                
                try:
                    doc_info = get_processor_instance().load_document(temp_path)
                    os.remove(temp_path)
                    
                    st.success(f"✅ Document loaded successfully!")
                    st.balloons()
                    
                    st.session_state.messages = []
                    st.rerun()
                except Exception as e:
                    os.remove(temp_path)
                    st.error(f"Error loading document: {str(e)}")
    
    st.markdown('</div>', unsafe_allow_html=True)


def render_qa_tab():
    st.markdown("### 💬 Ask Questions About Your Document")
    
    for msg in st.session_state.messages:
        if msg["role"] == "user":
            with st.chat_message("user", avatar="👤"):
                st.markdown(msg["content"])
        else:
            with st.chat_message("assistant", avatar="🤖"):
                st.markdown(msg["content"])
                
                if "sources" in msg:
                    with st.expander("📚 View Sources", expanded=False):
                        for i, source in enumerate(msg["sources"], 1):
                            st.markdown(f"""
                            <div class="source-card">
                                <strong>Source {i}</strong> (Score: {source.get('score', 0):.3f})<br>
                                <em>{source.get('heading', 'Unknown Section')}</em><br>
                                <p style="margin-top: 0.5rem; color: var(--text-secondary)">{source.get('text', '')}</p>
                            </div>
                            """, unsafe_allow_html=True)
    
    if prompt := st.chat_input("Ask a question about your document..."):
        st.session_state.messages.append({"role": "user", "content": prompt})
        st.rerun()
    
    if st.session_state.messages and st.session_state.messages[-1]["role"] == "user":
        question = st.session_state.messages[-1]["content"]
        
        with st.chat_message("assistant", avatar="🤖"):
            with st.spinner("🤔 Thinking..."):
                try:
                    response = get_processor_instance().ask_question(question)
                    
                    st.markdown(response["answer"])
                    
                    with st.expander("📚 View Sources", expanded=False):
                        for i, source in enumerate(response["sources"], 1):
                            st.markdown(f"""
                            <div class="source-card">
                                <strong>Source {i}</strong> (Relevance: {source.get('score', 0):.2%})<br>
                                <em>{source.get('heading', 'Unknown Section')}</em><br>
                                <p style="margin-top: 0.5rem; font-size: 0.9rem; color: var(--text-secondary)">{source.get('text', '')}</p>
                            </div>
                            """, unsafe_allow_html=True)
                    
                    st.session_state.messages[-1] = {
                        "role": "assistant",
                        "content": response["answer"],
                        "sources": response["sources"]
                    }
                    
                except Exception as e:
                    st.error(f"Error: {str(e)}")
                    st.session_state.messages.pop()


def render_flowchart_tab():
    st.markdown("### 🔄 Flowchart Generation")
    
    if st.session_state.flowchart_data:
        st.success("✅ Flowchart ready!")
        
        col1, col2 = st.columns([3, 1])
        with col2:
            st.markdown("**Graph Info**")
            info = get_processor_instance().flowchart_gen.get_graph_info(st.session_state.flowchart_data)
            st.json({
                "Nodes": info["num_nodes"],
                "Edges": info["num_edges"],
                "Title": info["title"]
            })
        
        with col1:
            st.markdown("#### 🎨 Flowchart Preview")
            mermaid_code = get_processor_instance().flowchart_gen.to_mermaid(st.session_state.flowchart_data)
            mermaid_html = f"""
            <!DOCTYPE html>
            <html>
            <head>
                <script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"></script>
                <style>
                    body {{
                        background-color: #1E293B;
                        color: white;
                        font-family: sans-serif;
                        display: flex;
                        justify-content: center;
                        padding: 20px;
                    }}
                    .mermaid {{
                        background-color: transparent;
                    }}
                </style>
            </head>
            <body>
                <div class="mermaid">
                {mermaid_code}
                </div>
                <script>
                    mermaid.initialize({{ 
                        startOnLoad: true, 
                        theme: 'dark', 
                        securityLevel: 'loose',
                        themeVariables: {{
                            darkMode: true,
                            background: '#1E293B'
                        }}
                    }});
                </script>
            </body>
            </html>
            """
            components.html(mermaid_html, height=600, scrolling=True)
        
        st.markdown("---")
        st.markdown("#### ✏️ Edit Flowchart")
        
        edit_instruction = st.text_input(
            "Edit instruction (e.g., 'add a validation step after step 2', 'remove step 5')",
            placeholder="Type your edit instruction in plain English..."
        )
        
        if st.button("Apply Edit", use_container_width=True):
            if edit_instruction:
                with st.spinner("🔄 Updating flowchart..."):
                    result = get_processor_instance().edit_flowchart(
                        st.session_state.flowchart_data,
                        edit_instruction
                    )
                    
                    if result["success"]:
                        st.session_state.flowchart_data = result["flowchart_data"]
                        st.success("✅ Flowchart updated!")
                        st.rerun()
                    else:
                        st.error(f"❌ {result.get('error', 'Failed to update flowchart')}")
        
        if st.button("🔄 Regenerate from Document"):
            with st.spinner("🔄 Generating new flowchart..."):
                result = get_processor_instance().detect_and_generate_flowchart()
                
                if result["success"]:
                    st.session_state.flowchart_data = result["flowchart_data"]
                    st.success("✅ New flowchart generated!")
                    st.rerun()
                else:
                    st.error("No process/workflow found in the document")
    else:
        col1, col2 = st.columns(2)
        
        with col1:
            st.markdown("""
            <div class="feature-card">
                <div class="feature-icon">🤖</div>
                <h4>Auto-Detect Process</h4>
                <p style="color: var(--text-secondary)">
                    Automatically find and visualize workflows in your document
                </p>
            </div>
            """, unsafe_allow_html=True)
            
            if st.button("🚀 Generate Flowchart", use_container_width=True):
                with st.spinner("🔍 Detecting processes..."):
                    result = get_processor_instance().detect_and_generate_flowchart()
                    
                    if result["success"]:
                        st.session_state.flowchart_data = result["flowchart_data"]
                        st.success("✅ Flowchart generated!")
                        st.rerun()
                    else:
                        st.error("No clear process/workflow found in the document")
        
        with col2:
            st.markdown("""
            <div class="feature-card">
                <div class="feature-icon">📝</div>
                <h4>Custom Text</h4>
                <p style="color: var(--text-secondary)">
                    Provide custom text for flowchart generation
                </p>
            </div>
            """, unsafe_allow_html=True)
            
            custom_text = st.text_area(
                "Paste process description here",
                height=150,
                placeholder="Enter a description of a process or workflow..."
            )
            
            if st.button("📊 Generate from Text", use_container_width=True):
                if custom_text:
                    with st.spinner("🔄 Generating flowchart..."):
                        result = get_processor_instance().detect_and_generate_flowchart(custom_text)
                        
                        if result["success"]:
                            st.session_state.flowchart_data = result["flowchart_data"]
                            st.success("✅ Flowchart generated!")
                            st.rerun()
                        else:
                            st.error("Could not generate flowchart from text")
                else:
                    st.warning("Please enter some text first")


def render_features_section():
    st.markdown("---")
    st.markdown("### ✨ Key Features")
    
    col1, col2, col3 = st.columns(3)
    
    with col1:
        st.markdown("""
        <div class="feature-card">
            <div class="feature-icon">🔍</div>
            <h4>Hybrid Retrieval</h4>
            <p style="color: var(--text-secondary)">
                Combines semantic search (FAISS) with keyword search (BM25) for accurate answers
            </p>
        </div>
        """, unsafe_allow_html=True)
    
    with col2:
        st.markdown("""
        <div class="feature-card">
            <div class="feature-icon">📑</div>
            <h4>Smart Chunking</h4>
            <p style="color: var(--text-secondary)">
                Semantic-aware text splitting with heading detection for better context
            </p>
        </div>
        """, unsafe_allow_html=True)
    
    with col3:
        st.markdown("""
        <div class="feature-card">
            <div class="feature-icon">💬</div>
            <h4>Conversational AI</h4>
            <p style="color: var(--text-secondary)">
                Maintains chat history for natural follow-up questions
            </p>
        </div>
        """, unsafe_allow_html=True)


def main():
    if not check_api_key():
        st.stop()
    
    render_header()
    render_sidebar()
    
    summary = get_processor_instance().get_document_summary()
    
    if not summary:
        render_upload_section()
        render_features_section()
    else:
        tab1, tab2 = st.tabs(["💬 Q&A", "🔄 Flowchart"])
        
        with tab1:
            render_qa_tab()
        
        with tab2:
            render_flowchart_tab()
        
        st.markdown("---")
        render_features_section()


if __name__ == "__main__":
    main()

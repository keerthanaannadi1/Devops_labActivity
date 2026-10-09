import fitz
import pdfplumber
import pytesseract
from PIL import Image
import io
import re
from typing import List, Dict, Tuple, Optional


class PDFParser:
    def __init__(self, ocr_enabled: bool = True):
        self.ocr_enabled = ocr_enabled
    
    def extract_text_from_pdf(self, pdf_path: str) -> Dict[int, str]:
        pages_text = {}
        
        doc = fitz.open(pdf_path)
        
        for page_num in range(len(doc)):
            page = doc[page_num]
            text = page.get_text()
            
            if text.strip() and len(text) > 100:
                pages_text[page_num + 1] = text
            elif self.ocr_enabled:
                ocr_text = self._ocr_page(page)
                if ocr_text:
                    pages_text[page_num + 1] = ocr_text
        
        doc.close()
        return pages_text
    
    def _ocr_page(self, page) -> str:
        try:
            pix = page.get_pixmap(dpi=200)
            img_bytes = pix.tobytes("png")
            img = Image.open(io.BytesIO(img_bytes))
            text = pytesseract.image_to_string(img)
            return text
        except Exception:
            return ""
    
    def extract_tables(self, pdf_path: str) -> Dict[int, List[str]]:
        tables_by_page = {}
        
        with pdfplumber.open(pdf_path) as pdf:
            for i, page in enumerate(pdf.pages):
                tables = page.extract_tables()
                if tables:
                    page_num = i + 1
                    tables_by_page[page_num] = []
                    for table in tables:
                        if table:
                            table_str = self._format_table(table)
                            tables_by_page[page_num].append(table_str)
        
        return tables_by_page
    
    def _format_table(self, table: List) -> str:
        if not table:
            return ""
        
        formatted_rows = []
        for row in table:
            if row:
                cleaned = [str(cell).strip() if cell else "" for cell in row]
                formatted_rows.append(" | ".join(cleaned))
        
        return "\n".join(formatted_rows)
    
    def parse_pdf(self, pdf_path: str) -> Dict:
        pages_text = self.extract_text_from_pdf(pdf_path)
        tables = self.extract_tables(pdf_path)
        
        full_text = "\n\n".join([f"--- Page {p} ---\n{text}" for p, text in sorted(pages_text.items())])
        
        return {
            "full_text": full_text,
            "pages": pages_text,
            "tables": tables,
            "num_pages": len(pages_text)
        }

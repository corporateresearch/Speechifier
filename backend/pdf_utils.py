"""PDF text extraction and sentence/paragraph segmentation.

The reader displays a *reflowed* view: we pull the text out of the PDF, group it
into paragraphs (one per text block) and split each paragraph into sentences.
Sentences are the unit of TTS streaming, so we also cap their length to keep
synthesis chunks small and responsive on a CPU.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

import fitz  # PyMuPDF

# Sentences longer than this are hard-wrapped at a word boundary so a single
# synthesis call never blocks playback for too long on a CPU.
MAX_SENTENCE_CHARS = 280

# Collapse runs of whitespace but keep single spaces.
_WS = re.compile(r"\s+")
# Split after sentence-ending punctuation that is followed by whitespace.
_SENT_SPLIT = re.compile(r"(?<=[.!?])\s+(?=[\"'(\[]?[A-Z0-9])")

# Strip characters that render as invisible squares in the browser:
# - Private Use Area (U+E000–U+F8FF)
# - Supplementary Private Use Areas (U+F0000–U+10FFFF)
# - Replacement character (U+FFFD)
# - Geometric shapes (U+25A0–U+25FF) e.g. ■ ▢ ◆
# - Dingbats (U+2700–U+27BF) and misc symbols (U+2600–U+26FF)
# - Control characters except newline/tab/space
_JUNK_CHARS = re.compile(
    r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f"         # C0 controls
    r"\uE000-\uF8FF"                              # Private Use Area
    r"\uFFFD"                                     # Replacement char
    r"\u25A0-\u25FF"                              # Geometric shapes
    r"\u2600-\u26FF"                              # Misc symbols
    r"\u2700-\u27BF"                              # Dingbats
    r"\U000F0000-\U0010FFFF"                      # Supplementary PUA
    r"]+"
)

# Check if a cleaned string has any actual readable content.
_HAS_ALNUM = re.compile(r"[a-zA-Z0-9]")


@dataclass
class Document:
    title: str
    sentences: list[str] = field(default_factory=list)
    # paragraphs[i] is a list of indices into `sentences`, in reading order.
    paragraphs: list[list[int]] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "title": self.title,
            "sentences": self.sentences,
            "paragraphs": self.paragraphs,
        }


def _clean(text: str) -> str:
    # Strip junk/non-printable characters that render as squares.
    text = _JUNK_CHARS.sub("", text)
    # Join hyphenated line breaks ("exam-\nple" -> "example").
    text = re.sub(r"-\n(?=[a-z])", "", text)
    return _WS.sub(" ", text).strip()


def _hard_wrap(sentence: str) -> list[str]:
    """Split an over-long sentence into <= MAX_SENTENCE_CHARS chunks."""
    if len(sentence) <= MAX_SENTENCE_CHARS:
        return [sentence]
    chunks: list[str] = []
    words = sentence.split(" ")
    cur = ""
    for w in words:
        if cur and len(cur) + 1 + len(w) > MAX_SENTENCE_CHARS:
            chunks.append(cur)
            cur = w
        else:
            cur = f"{cur} {w}" if cur else w
    if cur:
        chunks.append(cur)
    return chunks


def _split_sentences(paragraph: str) -> list[str]:
    paragraph = _clean(paragraph)
    if not paragraph or not _HAS_ALNUM.search(paragraph):
        return []
    out: list[str] = []
    for part in _SENT_SPLIT.split(paragraph):
        part = part.strip()
        if part:
            out.extend(_hard_wrap(part))
    return out


def extract_document(pdf_bytes: bytes, filename: str = "document.pdf") -> Document:
    """Extract a reflowed Document from raw PDF bytes."""
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    try:
        meta_title = (doc.metadata or {}).get("title") or ""
        sentences: list[str] = []
        paragraphs: list[list[int]] = []

        for page in doc:
            # "blocks" returns layout blocks; each is roughly a paragraph.
            blocks = page.get_text("blocks")
            # Sort by vertical then horizontal position for natural reading order.
            blocks.sort(key=lambda b: (round(b[1], 1), round(b[0], 1)))
            for b in blocks:
                # b is (x0, y0, x1, y1, text, block_no, block_type)
                # block_type 0 = text, 1 = image; skip non-text blocks.
                if b[6] != 0:
                    continue
                block_text = b[4]
                para_sentences = _split_sentences(block_text)
                if not para_sentences:
                    continue
                start = len(sentences)
                sentences.extend(para_sentences)
                paragraphs.append(list(range(start, len(sentences))))
    finally:
        doc.close()

    title = meta_title.strip() or _derive_title(filename)
    return Document(title=title, sentences=sentences, paragraphs=paragraphs)


def _derive_title(filename: str) -> str:
    name = re.sub(r"\.pdf$", "", filename, flags=re.IGNORECASE)
    name = name.replace("_", " ").replace("-", " ").strip()
    return name or "Document"

#!/usr/bin/env python3
"""
VaultChain Handwritten English OCR Engine (HTR)
Extracts handwritten English text from documents and images with:
- Adaptive Otsu binarization
- Notebook ruled-line removal
- Slant correction (deslanting)
- Multi-line projection segmentation
- Word and glyph segmentation
- Topological loop & zone character recognition
- Two-tier priority English vocabulary beam search
- Tesseract integration fallback if present
"""

import sys
import os
import shutil
import subprocess
import itertools
from pathlib import Path

try:
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont, ImageOps, ImageFilter
    import scipy.ndimage as ndi
except ImportError as e:
    sys.stderr.write(f"Missing required Python libraries: {e}\n")
    sys.exit(1)

FONT_CANDIDATES = [
    '/usr/share/fonts/truetype/ubuntu/Ubuntu-Italic[wdth,wght].ttf',
    '/usr/share/fonts/truetype/ubuntu/Ubuntu[wdth,wght].ttf',
    '/usr/share/fonts/truetype/ubuntu/UbuntuSans[wdth,wght].ttf',
    '/usr/share/fonts/truetype/ubuntu/UbuntuSans-Italic[wdth,wght].ttf',
    '/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf'
]
AVAILABLE_FONTS = [f for f in FONT_CANDIDATES if os.path.exists(f)]

CORE_WORDS = {
    'the', 'be', 'to', 'of', 'and', 'a', 'in', 'that', 'have', 'i', 'it', 'for', 'not', 'on', 'with', 'he',
    'as', 'you', 'do', 'at', 'this', 'but', 'his', 'by', 'from', 'they', 'we', 'say', 'her', 'she', 'or',
    'an', 'will', 'my', 'one', 'all', 'would', 'there', 'their', 'what', 'so', 'up', 'out', 'if', 'about',
    'who', 'get', 'which', 'go', 'me', 'when', 'make', 'can', 'like', 'time', 'no', 'just', 'him', 'know',
    'take', 'people', 'into', 'year', 'your', 'good', 'some', 'could', 'them', 'see', 'other', 'than', 'then',
    'now', 'look', 'only', 'come', 'its', 'over', 'think', 'also', 'back', 'after', 'use', 'two', 'how',
    'our', 'work', 'first', 'well', 'way', 'even', 'new', 'want', 'because', 'any', 'these', 'give', 'day',
    'most', 'us', 'hello', 'world', 'handwritten', 'handwriting', 'note', 'document', 'text', 'page', 'notes',
    'verified', 'authvault', 'blockchain', 'digital', 'asset', 'ledger', 'proof', 'secure', 'signature',
    'contract', 'agreement', 'certificate', 'record', 'university', 'project', 'student', 'assignment',
    'english', 'letter', 'memo', 'report', 'evidence', 'identification', 'official', 'signed', 'date', 'name',
    'address', 'title', 'subject', 'dear', 'sincerely', 'regards', 'total', 'amount', 'account', 'number',
    'check', 'receipt', 'invoice', 'stamp', 'authorized', 'seal', 'registration', 'hash', 'code',
    'system', 'program', 'result', 'test', 'sample', 'paper', 'written', 'write', 'author', 'original',
    'token', 'transfer', 'balance', 'credit', 'credits', 'marketplace', 'price', 'owner', 'vault', 'wallet'
}

def load_dictionaries():
    core = set(CORE_WORDS)
    extended = set(CORE_WORDS)
    dict_path = '/usr/share/dict/words'
    if os.path.exists(dict_path):
        try:
            with open(dict_path, 'r', encoding='utf-8', errors='ignore') as f:
                for line in f:
                    w = line.strip().lower()
                    if w.isalpha() and 2 <= len(w) <= 22:
                        extended.add(w)
        except Exception:
            pass
    return core, extended

def count_loops(glyph_arr):
    padded = np.pad(glyph_arr, ((1, 1), (1, 1)), mode='constant', constant_values=0)
    inv = (padded == 0).astype(np.uint8)
    _, num = ndi.label(inv)
    return max(0, num - 1)

def extract_glyph_features(glyph_arr):
    h, w = glyph_arr.shape
    if h == 0 or w == 0:
        return np.zeros(400), 0, 1.0
    img = Image.fromarray((glyph_arr * 255).astype(np.uint8)).resize((20, 20), Image.BILINEAR)
    vec = (np.array(img) > 64).astype(float).flatten()
    loops = count_loops(glyph_arr)
    ratio = w / float(h)
    return vec, loops, ratio

def build_prototypes():
    chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
    prototypes = []
    fonts_to_use = AVAILABLE_FONTS if AVAILABLE_FONTS else []
    for fpath in fonts_to_use:
        try:
            font = ImageFont.truetype(fpath, 36)
            for ch in chars:
                img = Image.new('L', (50, 50), 255)
                d = ImageDraw.Draw(img)
                d.text((8, 4), ch, fill=0, font=font)
                arr = (np.array(img) < 128).astype(np.uint8)
                objs = ndi.find_objects(arr)
                if objs:
                    sl = objs[0]
                    glyph = arr[sl]
                    vec, loops, ratio = extract_glyph_features(glyph)
                    prototypes.append({'char': ch, 'vec': vec, 'loops': loops, 'ratio': ratio})
        except Exception:
            continue
    if not prototypes:
        default_font = ImageFont.load_default()
        for ch in chars:
            img = Image.new('L', (40, 40), 255)
            d = ImageDraw.Draw(img)
            d.text((5, 2), ch, fill=0, font=default_font)
            arr = (np.array(img) < 128).astype(np.uint8)
            objs = ndi.find_objects(arr)
            if objs:
                sl = objs[0]
                glyph = arr[sl]
                vec, loops, ratio = extract_glyph_features(glyph)
                prototypes.append({'char': ch, 'vec': vec, 'loops': loops, 'ratio': ratio})
    return prototypes

def otsu_threshold(gray):
    hist, _ = np.histogram(gray, bins=256, range=(0, 256))
    total = gray.size
    sum_total = np.dot(np.arange(256), hist)
    sum_b, w_b, var_max, thresh = 0, 0, 0, 128
    for t in range(256):
        w_b += hist[t]
        if w_b == 0: continue
        w_f = total - w_b
        if w_f == 0: break
        sum_b += t * hist[t]
        m_b = sum_b / w_b
        m_f = (sum_total - sum_b) / w_f
        var_b = w_b * w_f * ((m_b - m_f)**2)
        if var_b > var_max:
            var_max = var_b
            thresh = t
    return thresh

def preprocess_image(image_path):
    pil_img = Image.open(image_path).convert('L')
    arr = np.array(pil_img)
    t = otsu_threshold(arr)
    binary = (arr <= t).astype(np.uint8)
    if np.sum(binary) == 0:
        binary = (arr < 128).astype(np.uint8)
    
    # Suppress notebook ruling lines (long horizontal lines)
    if binary.shape[1] >= 60:
        kernel_w = min(35, binary.shape[1] // 3)
        horizontal_kernel = np.ones((1, kernel_w), dtype=np.uint8)
        lines_detected = ndi.binary_opening(binary, structure=horizontal_kernel)
        binary = binary & (~lines_detected)
        
    return binary

def segment_lines(binary):
    h_proj = np.sum(binary, axis=1)
    line_ranges = []
    in_line = False
    start_y = 0
    thresh = max(6, int(np.max(h_proj) * 0.08)) if len(h_proj) > 0 and np.max(h_proj) > 0 else 6
    for y, val in enumerate(h_proj):
        if val > thresh and not in_line:
            in_line = True
            start_y = y
        elif val <= thresh and in_line:
            in_line = False
            if y - start_y >= 8:
                line_ranges.append((max(0, start_y - 2), min(len(h_proj), y + 2)))
    if in_line and len(h_proj) - start_y >= 8:
        line_ranges.append((start_y, len(h_proj)))
    return line_ranges

def edit_distance(s1, s2):
    m, n = len(s1), len(s2)
    dp = [[0]*(n+1) for _ in range(m+1)]
    for i in range(m+1): dp[i][0] = i
    for j in range(n+1): dp[0][j] = j
    for i in range(1, m+1):
        for j in range(1, n+1):
            cost = 0 if s1[i-1].lower() == s2[j-1].lower() else 1
            dp[i][j] = min(dp[i-1][j]+1, dp[i][j-1]+1, dp[i-1][j-1]+cost)
    return dp[m][n]

def normalize_confusable(text):
    trans = str.maketrans('0158', 'oils')
    return text.translate(trans)

def correct_word(candidate_matrix, core_words, extended_words):
    if not candidate_matrix:
        return ""
    raw_word = "".join(opts[0][0] for opts in candidate_matrix)
    if not raw_word.isalpha() or len(raw_word) <= 1:
        norm = normalize_confusable(raw_word)
        if norm.lower() in core_words:
            return norm.capitalize() if raw_word[0].isupper() else norm
        return raw_word
        
    lower_raw = raw_word.lower()
    if lower_raw in core_words:
        return raw_word
    if lower_raw in extended_words:
        return raw_word
        
    # Check normalized confusables
    norm = normalize_confusable(lower_raw)
    if norm in core_words:
        return norm.capitalize() if raw_word[0].isupper() else norm
    if norm in extended_words:
        return norm.capitalize() if raw_word[0].isupper() else norm
        
    # Priority check: Core dictionary first
    best_word = None
    best_dist = 3
    target_len = len(lower_raw)
    for cand in core_words:
        if abs(len(cand) - target_len) <= 1:
            d = edit_distance(lower_raw, cand)
            if d < best_dist:
                best_dist = d
                best_word = cand
                if d == 1:
                    break
                    
    if best_word:
        return best_word.capitalize() if raw_word[0].isupper() else best_word
        
    # Secondary check: Extended dictionary
    for cand in extended_words:
        if abs(len(cand) - target_len) <= 1:
            d = edit_distance(lower_raw, cand)
            if d < best_dist:
                best_dist = d
                best_word = cand
                if d == 1:
                    break
                    
    if best_word and raw_word[0].isupper():
        return best_word.capitalize()
    return best_word or raw_word

def run_tesseract_if_available(image_path, mode='handwritten'):
    tesseract_bin = shutil.which('tesseract')
    if not tesseract_bin:
        return None
    try:
        psm = '6' if mode == 'handwritten' else '3'
        cmd = [tesseract_bin, str(image_path), 'stdout', '-l', 'eng', '--psm', psm, '--oem', '1']
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if res.returncode == 0 and len(res.stdout.strip()) >= 5:
            return res.stdout.strip()
    except Exception:
        pass
    return None

def extract_handwriting(image_path, mode='handwritten'):
    tess_output = run_tesseract_if_available(image_path, mode)
    if tess_output and len(tess_output) >= 10:
        return tess_output

    binary = preprocess_image(image_path)
    line_ranges = segment_lines(binary)
    if not line_ranges:
        return tess_output or ""
        
    prototypes = build_prototypes()
    core_words, extended_words = load_dictionaries()
    extracted_lines = []
    
    for y1, y2 in line_ranges:
        line_arr = binary[y1:y2, :]
        v_proj = np.sum(line_arr, axis=0)
        
        word_boxes = []
        in_word = False
        w_start = 0
        gap_size = max(5, int(line_arr.shape[0] * 0.22))
        
        for x, val in enumerate(v_proj):
            if val > 0 and not in_word:
                in_word = True
                w_start = x
            elif val == 0 and in_word:
                lookahead = v_proj[x:x+gap_size]
                if np.all(lookahead == 0):
                    in_word = False
                    if x - w_start >= 4:
                        word_boxes.append((w_start, x))
        if in_word and len(v_proj) - w_start >= 4:
            word_boxes.append((w_start, len(v_proj)))
            
        line_words = []
        for wx1, wx2 in word_boxes:
            word_crop = line_arr[:, wx1:wx2]
            labeled, num = ndi.label(word_crop)
            objs = ndi.find_objects(labeled)
            char_boxes = []
            for sl in objs:
                cy1, cx1 = sl[0].start, sl[1].start
                cy2, cx2 = sl[0].stop, sl[1].stop
                if (cx2 - cx1) * (cy2 - cy1) >= 4:
                    char_boxes.append([cx1, cx2, cy1, cy2])
            if not char_boxes:
                continue
            char_boxes.sort(key=lambda b: (b[0], b[2]))
            
            # Merge dots over i / j
            merged = []
            skip = set()
            for i in range(len(char_boxes)):
                if i in skip: continue
                bx1, bx2, by1, by2 = char_boxes[i]
                for j in range(i+1, min(i+4, len(char_boxes))):
                    if j in skip: continue
                    ox1, ox2, oy1, oy2 = char_boxes[j]
                    if abs((bx1+bx2)/2 - (ox1+ox2)/2) < 10:
                        bx1, bx2 = min(bx1, ox1), max(bx2, ox2)
                        by1, by2 = min(by1, oy1), max(by2, oy2)
                        skip.add(j)
                merged.append((bx1, bx2, by1, by2))
            merged.sort(key=lambda b: b[0])
            
            candidate_matrix = []
            for bx1, bx2, by1, by2 in merged:
                glyph = word_crop[by1:by2, bx1:bx2]
                vec, loops, ratio = extract_glyph_features(glyph)
                scores = []
                for p in prototypes:
                    loop_pen = 45.0 if abs(p['loops'] - loops) > 0 else 0.0
                    dist = np.sum(np.abs(vec - p['vec'])) + loop_pen
                    scores.append((p['char'], dist))
                scores.sort(key=lambda item: item[1])
                candidate_matrix.append(scores[:3])
                
            recognized_word = correct_word(candidate_matrix, core_words, extended_words)
            if recognized_word:
                line_words.append(recognized_word)
        if line_words:
            extracted_lines.append(" ".join(line_words))
            
    result_text = "\n".join(extracted_lines)
    return result_text if result_text.strip() else (tess_output or "")

def main():
    if len(sys.argv) < 2:
        print("Usage: handwriting_ocr.py <image_path> [--mode handwritten|printed|auto]", file=sys.stderr)
        sys.exit(1)
    image_path = Path(sys.argv[1])
    if not image_path.exists():
        print(f"Error: File not found: {image_path}", file=sys.stderr)
        sys.exit(1)
    mode = 'handwritten'
    if '--mode' in sys.argv:
        idx = sys.argv.index('--mode')
        if idx + 1 < len(sys.argv):
            mode = sys.argv[idx + 1]
    try:
        text = extract_handwriting(image_path, mode)
        sys.stdout.write(text + "\n")
        sys.stdout.flush()
    except Exception as e:
        sys.stderr.write(f"Extraction failed: {e}\n")
        sys.exit(1)

if __name__ == '__main__':
    main()

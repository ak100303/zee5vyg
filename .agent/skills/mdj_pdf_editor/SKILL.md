---
name: mdj_pdf_editor
description: Extract diagram images from StarUML (.mdj) project files and insert them into PDF documents.
---

# MDJ & PDF Editor Skill

This skill provides the capability to extract diagram images from `.mdj` (StarUML) model files and embed those images precisely into `.pdf` documents.

## Prerequisites

1. **StarUML CLI**: StarUML must be installed, and its executable (`staruml`) must be accessible via the system `PATH`.
2. **PyMuPDF (`fitz`)**: Required for editing the PDF file.
   - Install via pip: `pip install pymupdf`

## Workflow

### 1. Extract Images from `.mdj` Files
StarUML models (`.mdj`) don't store image files directly; they contain structured JSON diagram data. Use the StarUML CLI to generate images from this data.

Run the following command to export all diagrams in an `.mdj` file to a specified directory:
```powershell
staruml image "path/to/project_model.mdj" -o "path/to/output_directory" -f png
```

### 2. Insert Extracted Image into a `.pdf`
Once the diagrams have been exported to `.png` (or `.jpeg`) format, you can insert them into an existing PDF file using the provided Python script.

Run the Python script located in this skill's `scripts` directory:
```powershell
python scripts/insert_image_to_pdf.py "path/to/input.pdf" "path/to/exported_diagram.png" "path/to/output.pdf" --page 1 --x 50 --y 50 --width 400 --height 300
```

## Available Scripts

### `insert_image_to_pdf.py`
A Python script that leverages PyMuPDF (`fitz`) to embed images into a PDF document at exact page numbers, coordinates, and scales.

**Usage:**
```powershell
python insert_image_to_pdf.py input_pdf image_path output_pdf [OPTIONS]
```

**Arguments:**
- `input_pdf`: Path to the existing input `.pdf`.
- `image_path`: Path to the image file (e.g., extracted `.png` from an `.mdj`).
- `output_pdf`: Path where the newly edited `.pdf` should be saved.

**Options:**
- `--page`: The page number (1-based index) to insert the image into. (Default: 1)
- `--x`: The X coordinate for the top-left corner of the image. (Default: 50.0)
- `--y`: The Y coordinate for the top-left corner of the image. (Default: 50.0)
- `--width`: The width of the image. (Default: 200.0)
- `--height`: The height of the image. (Default: 200.0)

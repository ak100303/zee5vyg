import fitz  # PyMuPDF
import sys
import argparse
import os

def insert_image(input_pdf, output_pdf, image_path, page_num, x, y, width, height):
    try:
        if not os.path.exists(input_pdf):
            print(f"Error: PDF file '{input_pdf}' not found.")
            sys.exit(1)
        if not os.path.exists(image_path):
            print(f"Error: Image file '{image_path}' not found.")
            sys.exit(1)
            
        doc = fitz.open(input_pdf)
        if page_num < 1 or page_num > len(doc):
            print(f"Error: Invalid page number {page_num}. PDF has {len(doc)} pages.")
            sys.exit(1)
            
        page = doc[page_num - 1]
        
        # PyMuPDF uses top-left coordinates: Rect(x0, y0, x1, y1)
        rect = fitz.Rect(x, y, x + width, y + height)
        
        page.insert_image(rect, filename=image_path)
        
        doc.save(output_pdf)
        doc.close()
        print(f"Successfully inserted '{image_path}' into '{output_pdf}' on page {page_num}.")
        
    except Exception as e:
        print(f"An error occurred: {e}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Insert an image into a PDF file.")
    parser.add_argument("input_pdf", help="Path to the input PDF file")
    parser.add_argument("image_path", help="Path to the image to insert")
    parser.add_argument("output_pdf", help="Path to save the modified PDF")
    
    parser.add_argument("--page", type=int, default=1, help="Page number (1-based index) to insert the image (default: 1)")
    parser.add_argument("--x", type=float, default=50, help="X coordinate for the top-left corner of the image (default: 50)")
    parser.add_argument("--y", type=float, default=50, help="Y coordinate for the top-left corner of the image (default: 50)")
    parser.add_argument("--width", type=float, default=200, help="Width of the inserted image (default: 200)")
    parser.add_argument("--height", type=float, default=200, help="Height of the inserted image (default: 200)")

    args = parser.parse_args()
    
    insert_image(
        args.input_pdf, 
        args.output_pdf, 
        args.image_path, 
        args.page, 
        args.x, 
        args.y, 
        args.width, 
        args.height
    )

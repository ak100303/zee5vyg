import fitz
import os
import sys

# Paths setup
pdf_path = r"D:\generate uml for praticals\4_Stock_Maintenance_System.pdf"
png_dir = r"D:\generate uml for praticals\png"
output_pdf = r"D:\generate uml for praticals\4_Stock_Maintenance_System_Completed.pdf"

# Page numbers (1-indexed) to PNG mapping
mappings = {
    2: "Model1!UseCaseDiagram1_1.png",
    3: "Model1!ClassDiagram1_2.png",
    4: "Model2!ComponentDiagram1_3.png",
    5: "Collaboration1!Interaction1!CommunicationDiagram1_4.png",
    6: "Collaboration2!Interaction1!SequenceDiagram1_5.png",
    7: "Activity1!ActivityDiagram1_8.png",
    8: "StateMachine1!StatechartDiagram1_6.png",
    9: "Model3!DeploymentDiagram1_7.png"
}

try:
    doc = fitz.open(pdf_path)

    for page_num, img_name in mappings.items():
        page_idx = page_num - 1 # PyMuPDF is 0-indexed
        page = doc[page_idx]
        
        img_path = os.path.join(png_dir, img_name)
        if os.path.exists(img_path):
            print(f"Embedding {img_name} onto Page {page_num}...")
            # Insert image, fit proportionally in the middle area of the A4 page
            rect = fitz.Rect(50, 180, 545, 700) 
            page.insert_image(rect, filename=img_path, keep_proportion=True)
        else:
            print(f"[ERROR] PNG missing: {img_path}")

    doc.save(output_pdf)
    print(f"\nSUCCESS! Created final document: {output_pdf}")
except Exception as e:
    print(f"An error occurred: {e}")
    sys.exit(1)

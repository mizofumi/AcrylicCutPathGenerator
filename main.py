from fastapi import FastAPI, File, UploadFile, Form
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware
from tempfile import NamedTemporaryFile
import shutil
import os
from generate_cutpath import generate_cutpath

app = FastAPI()
app.add_middleware(
    CORSMiddleware,
#    allow_origins=["http://localhost:3000"],
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.post("/generate")
async def create_svg(file: UploadFile = File(...), offset: int = Form(10)):
    if not file.filename.lower().endswith(".png"):
        return {"error": "Only PNG files are supported."}
    
    with NamedTemporaryFile(delete=False, suffix=".png") as tmp_in:
        shutil.copyfileobj(file.file, tmp_in)
        tmp_in_path = tmp_in.name

    tmp_out_path = tmp_in_path.replace(".png", ".svg")
    
    try:
        generate_cutpath(tmp_in_path, tmp_out_path, offset)
        return FileResponse(tmp_out_path, media_type="image/svg+xml", filename="cutpath.svg")
    finally:
        os.unlink(tmp_in_path)


from fastapi import FastAPI, File, UploadFile, HTTPException, Request
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
import shutil
import os
import time
import uuid
import logging
from deepface import DeepFace

from state import (
    REFERENCE_DIR, STATIC_DIR, HISTORY_DIR, CURRENT_DIR,
    analysis_history, current_status
)
from dashboard import router as dashboard_router

app = FastAPI(title="Haru-i AI Analysis Server")

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# CORS settings
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount static files
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")
app.mount("/reference", StaticFiles(directory=REFERENCE_DIR), name="reference")

# Include dashboard router
app.include_router(dashboard_router)

@app.post("/stop")
async def stop_analysis():
    current_status["stop_requested"] = True
    current_status["task"] = "Stopped"
    return {"message": "Stop requested"}

@app.post("/resume")
async def resume_server():
    current_status["stop_requested"] = False
    current_status["task"] = "Idle"
    return {"message": "Server resumed"}

@app.post("/register")
async def register_baby(file: UploadFile = File(...)):
    current_status["task"] = "Registering..."
    try:
        file_path = os.path.join(REFERENCE_DIR, "baby_ref.jpg")
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        return {"status": "success"}
    finally:
        current_status["task"] = "Idle"

@app.post("/analyze")
async def analyze_photo(file: UploadFile = File(...)):
    if current_status["stop_requested"]:
        return JSONResponse(status_code=503, content={"status": "stopped"})

    current_status["is_analyzing"] = True
    current_status["task"] = "Analyzing"

    unique_id = uuid.uuid4().hex[:8]
    temp_analyze_path = os.path.join(CURRENT_DIR, f"analyze_{unique_id}.jpg")
    current_display_path = os.path.join(CURRENT_DIR, "current_target.jpg")

    try:
        contents = await file.read()
        with open(temp_analyze_path, "wb") as f:
            f.write(contents)
            f.flush()
            os.fsync(f.fileno())

        shutil.copy(temp_analyze_path, current_display_path)
        current_status["current_img"] = "current_target.jpg"

        ref_path = os.path.join(REFERENCE_DIR, "baby_ref.jpg")
        if not os.path.exists(ref_path): raise ValueError("기준 사진이 없습니다.")

        # Failsafe: Try multiple backends in case one fails due to environment
        backends = ["ssd", "opencv", "skip"]
        result = None
        last_err = ""

        for backend in backends:
            try:
                result = DeepFace.verify(
                    img1_path = temp_analyze_path,
                    img2_path = ref_path,
                    model_name = "Facenet512",
                    detector_backend = backend,
                    distance_metric = "cosine",
                    enforce_detection = False,
                    align = True
                )
                if result: break
            except Exception as e:
                last_err = str(e)
                continue

        if not result:
            raise ValueError(f"모든 분석 백엔드 실패: {last_err}")

        distance = float(result["distance"])
        is_verified = distance < 0.65
        res_text = "MATCH" if is_verified else "NO MATCH"
        current_status["last_result"] = res_text

        hist_name = f"hist_{unique_id}.jpg"
        shutil.copy(temp_analyze_path, os.path.join(HISTORY_DIR, hist_name))

        analysis_history.append({
            "time": time.strftime("%H:%M:%S"),
            "name": file.filename,
            "res": res_text,
            "dist": f"{distance:.4f}",
            "thumb": hist_name
        })

        if len(analysis_history) > 50:
            analysis_history.pop(0)

        return {"is_target_baby": is_verified, "distance": distance}

    except Exception as e:
        logger.error(f"Analysis Failed: {str(e)}")
        current_status["task"] = "Error"
        current_status["last_result"] = f"Error: {str(e)}"
        return {"status": "error", "message": str(e)}
    finally:
        current_status["is_analyzing"] = False
        if current_status["task"] != "Error":
            current_status["task"] = "Idle"
        if os.path.exists(temp_analyze_path):
            os.remove(temp_analyze_path)
        current_status["last_update"] = time.time()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

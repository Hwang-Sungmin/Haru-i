from fastapi import FastAPI, File, UploadFile, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
import shutil
import os
import time
import uuid
import cv2
import numpy as np
from deepface import DeepFace
import logging
import traceback

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

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
REFERENCE_DIR = os.path.join(BASE_DIR, "models", "reference_baby")
STATIC_DIR = os.path.join(BASE_DIR, "static")
HISTORY_DIR = os.path.join(STATIC_DIR, "history")
CURRENT_DIR = os.path.join(STATIC_DIR, "current")

os.makedirs(REFERENCE_DIR, exist_ok=True)
os.makedirs(STATIC_DIR, exist_ok=True)
os.makedirs(HISTORY_DIR, exist_ok=True)
os.makedirs(CURRENT_DIR, exist_ok=True)

# Mount static files
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")
app.mount("/reference", StaticFiles(directory=REFERENCE_DIR), name="reference")

# Shared state
analysis_history = []
current_status = {
    "task": "Idle",
    "last_update": time.time(),
    "current_img": None,
    "last_result": "None",
    "is_analyzing": False,
    "stop_requested": False
}

@app.get("/", response_class=HTMLResponse)
async def dashboard(request: Request):
    ref_exists = os.path.exists(os.path.join(REFERENCE_DIR, "baby_ref.jpg"))
    ts = int(time.time())

    history_html = ""
    for h in reversed(analysis_history[-15:]):
        color = "#4CAF50" if h['res'] == "MATCH" else ("#F44336" if h['res'] == "NO MATCH" else "#999")
        thumb_url = f"/static/history/{h['thumb']}?t={ts}" if h.get('thumb') else ""
        history_html += f"""
        <tr style="border-bottom: 1px solid #ddd;">
            <td style="padding: 12px;">{h['time']}</td>
            <td style="padding: 12px; display: flex; align-items: center; gap: 10px;">
                {"<img src='" + thumb_url + "' style='width: 45px; height: 45px; object-fit: cover; border-radius: 6px; border: 1px solid #eee;'>" if thumb_url else ""}
                <span style="font-size: 0.85em; color: #555;">{h['name']}</span>
            </td>
            <td style="padding: 12px; color: {color}; font-weight: bold;">{h['res']}</td>
            <td style="padding: 12px; font-family: 'Courier New', monospace; font-size: 0.9em;">{h['dist']}</td>
        </tr>
        """

    if not history_html:
        history_html = "<tr><td colspan='4' style='padding: 30px; text-align: center; color: #999;'>분석 이력이 없습니다. 앱에서 '아기 사진 찾기'를 눌러보세요.</td></tr>"

    is_busy = current_status["is_analyzing"]
    status_color = "#FF9800" if is_busy else ("#F44336" if current_status["task"] == "Error" else "#4CAF50")
    current_img_url = f"/static/current/{current_status['current_img']}?t={ts}" if current_status['current_img'] else None

    return f"""
    <html>
        <head>
            <title>Haru-i AI Monitor</title>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body {{ font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif; padding: 20px; background: #FFFDF5; color: #3E2723; line-height: 1.5; }}
                .card {{ background: white; padding: 20px; border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); margin-bottom: 20px; }}
                .btn {{ border: none; padding: 10px 20px; border-radius: 8px; cursor: pointer; font-weight: bold; transition: 0.2s; }}
                .btn-stop {{ background: #F44336; color: white; }}
                .btn-resume {{ background: #4CAF50; color: white; }}
                @keyframes pulse {{ 0% {{ opacity: 1; }} 50% {{ opacity: 0.5; }} 100% {{ opacity: 1; }} }}
                .analyzing {{ animation: pulse 1s infinite; color: #FF9800; }}
            </style>
        </head>
        <body>
            <div style="max-width: 1000px; margin: 0 auto;">
                <h1 style="color: #D87D4A; text-align: center; margin-bottom: 30px;">👶 Haru-i AI Analysis Dashboard</h1>

                <div class="card" style="display: flex; align-items: center; justify-content: space-between;">
                    <div>
                        <h2 style="margin: 0; font-size: 1.1em; color: #888; font-weight: 400;">System Status</h2>
                        <div style="font-size: 1.6em; margin-top: 5px; font-weight: bold; color: {status_color};" class="{"analyzing" if is_busy else ""}">
                            {current_status["task"]}
                        </div>
                    </div>
                    <div>
                        {f'<button class="btn btn-stop" onclick="stopAnalysis()">분석 중단</button>' if not current_status["stop_requested"] else '<button class="btn btn-resume" onclick="resumeServer()">분석 재개</button>'}
                    </div>
                </div>

                <div style="display: grid; grid-template-columns: 350px 1fr; gap: 25px;">
                    <div>
                        <div class="card" style="text-align: center;">
                            <h3 style="margin-top: 0; font-size: 1em; color: #D87D4A;">현재 분석 사진</h3>
                            <div style="width: 100%; height: 250px; background: #fafafa; border-radius: 12px; display: flex; align-items: center; justify-content: center; overflow: hidden; border: 2px dashed #EBCFB2;">
                                {f"<img src='{current_img_url}' style='width: 100%; height: 100%; object-fit: contain;'>" if current_img_url else "<span style='color: #ccc;'>No Image</span>"}
                            </div>
                            <div style="margin-top: 15px; font-weight: bold; font-size: 1.1em;">
                                결과: <span style="color: {('#4CAF50' if current_status['last_result']=='MATCH' else '#F44336') if current_status['last_result'] != 'None' else '#999'}">{current_status["last_result"]}</span>
                            </div>
                        </div>
                        <div class="card" style="text-align: center;">
                            <h3 style="margin-top: 0; font-size: 1em; color: #888;">우리 아기 기준 사진</h3>
                            <img src='/reference/baby_ref.jpg?t={ts}' style='width: 150px; height: 150px; object-fit: cover; border-radius: 50%; border: 4px solid #EBCFB2; background: #eee;'>
                        </div>
                    </div>

                    <div class="card">
                        <h3 style="margin-top: 0; margin-bottom: 20px;">분석 히스토리</h3>
                        <div style="max-height: 550px; overflow-y: auto;">
                            <table style="width: 100%; border-collapse: collapse; text-align: left;">
                                <thead>
                                    <tr style="border-bottom: 2px solid #FDF2F0;">
                                        <th style="padding: 12px;">시간</th>
                                        <th style="padding: 12px;">대상 사진</th>
                                        <th style="padding: 12px;">결과</th>
                                        <th style="padding: 12px;">유사도</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {history_html}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
            <script>
                function stopAnalysis() {{ fetch('/stop', {{method: 'POST'}}).then(() => location.reload()); }}
                function resumeServer() {{ fetch('/resume', {{method: 'POST'}}).then(() => location.reload()); }}
                setTimeout(() => location.reload(), {1000 if is_busy else 3000});
            </script>
        </body>
    </html>
    """

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

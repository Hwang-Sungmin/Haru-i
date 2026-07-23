from fastapi import FastAPI, File, UploadFile, HTTPException, Request, Header
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
    current_status, supabase
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
    current_status["is_analyzing"] = False
    current_status["is_batch_mode"] = False
    return {"message": "Stop requested"}


@app.post("/resume")
async def resume_server():
    current_status["stop_requested"] = False
    current_status["task"] = "Idle"
    current_status["is_analyzing"] = False
    current_status["is_batch_mode"] = False
    return {"message": "Server resumed"}


@app.post("/reset")
async def reset_server():
    """로컬 상태를 초기화하고 필요한 경우 DB 연동을 준비합니다."""
    current_status.update({
        "task": "Idle",
        "last_result": "None",
        "current_img": None,
        "is_analyzing": False,
        "stop_requested": False,
        "is_batch_mode": False
    })
    return {"message": "Server reset successfully"}


@app.post("/start")
async def start_batch():
    """대량 분석 시작을 알립니다."""
    current_status["task"] = "Analyzing"
    current_status["is_analyzing"] = True
    current_status["stop_requested"] = False
    current_status["is_batch_mode"] = True
    return {"message": "Batch analysis started"}


@app.post("/finish")
async def finish_batch():
    """대량 분석 종료를 알립니다."""
    current_status["task"] = "Idle"
    current_status["is_analyzing"] = False
    current_status["is_batch_mode"] = False
    return {"message": "Batch analysis finished"}


@app.post("/register")
async def register_baby(
    file: UploadFile = File(...),
    x_user_id: str = Header(None)
):
    if not x_user_id:
        raise HTTPException(status_code=400, detail="X-User-ID header is required")
        
    current_status["task"] = "Registering..."
    try:
        # 1. 로컬 백업 저장 (선택 사항)
        local_path = os.path.join(REFERENCE_DIR, f"{x_user_id}_ref.jpg")
        contents = await file.read()
        with open(local_path, "wb") as buffer:
            buffer.write(contents)
            
        # 2. Supabase Storage 업로드
        if supabase:
            try:
                # 'profiles' 버킷이 미리 생성되어 있어야 합니다.
                bucket_name = "profiles"
                file_path = f"{x_user_id}.jpg"
                
                # 기존 파일 삭제 시도 (덮어쓰기 보장)
                try:
                    supabase.storage.from_(bucket_name).remove([file_path])
                except:
                    pass
                    
                # 신규 파일 업로드
                supabase.storage.from_(bucket_name).upload(
                    path=file_path,
                    file=contents,
                    file_options={"content-type": "image/jpeg"}
                )
                logger.info(f"Successfully uploaded profile for user: {x_user_id}")
            except Exception as se:
                logger.error(f"Supabase Storage Upload Error: {str(se)}")
                
        return {"status": "success"}
    finally:
        current_status["task"] = "Idle"


async def get_reference_photo(user_id: str) -> str:
    """사용자의 기준 사진 경로를 반환합니다. 로컬에 없으면 Storage에서 다운로드합니다."""
    local_path = os.path.join(REFERENCE_DIR, f"{user_id}_ref.jpg")
    
    # 로컬에 있으면 즉시 반환
    if os.path.exists(local_path):
        return local_path
        
    # 로컬에 없으면 Supabase Storage에서 다운로드
    if supabase:
        try:
            bucket_name = "profiles"
            file_path = f"{user_id}.jpg"
            res = supabase.storage.from_(bucket_name).download(file_path)
            with open(local_path, "wb") as f:
                f.write(res)
            return local_path
        except Exception as e:
            logger.error(f"Failed to download reference photo for {user_id}: {str(e)}")
            
    # 최후의 수단: 기본 파일 체크
    default_path = os.path.join(REFERENCE_DIR, "baby_ref.jpg")
    if os.path.exists(default_path):
        return default_path
        
    return None


@app.post("/analyze")
async def analyze_photo(
    file: UploadFile = File(...),
    x_user_id: str = Header(None)
):
    if current_status["stop_requested"]:
        return JSONResponse(status_code=503, content={"status": "stopped"})

    if not x_user_id:
        x_user_id = "default_user"

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

        # 사용자별 기준 사진 가져오기
        ref_path = await get_reference_photo(x_user_id)
        if not ref_path:
            raise ValueError(f"사용자({x_user_id})의 기준 사진이 없습니다.")

        backends = ["ssd", "opencv", "skip"]
        result = None
        last_err = ""

        for backend in backends:
            try:
                result = DeepFace.verify(
                    img1_path=temp_analyze_path,
                    img2_path=ref_path,
                    model_name="Facenet512",
                    detector_backend=backend,
                    distance_metric="cosine",
                    enforce_detection=False,
                    align=True
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

        # Supabase Logging
        if supabase:
            try:
                data = {
                    "user_id": x_user_id,
                    "filename": file.filename,
                    "result": res_text,
                    "distance": distance
                }
                supabase.table("analysis_history").insert(data).execute()
            except Exception as se:
                logger.error(f"Supabase Log Error: {str(se)}")

        hist_name = f"hist_{unique_id}.jpg"
        shutil.copy(temp_analyze_path, os.path.join(HISTORY_DIR, hist_name))

        return {"is_target_baby": is_verified, "distance": distance}

    except Exception as e:
        logger.error(f"Analysis Failed: {str(e)}")
        current_status["task"] = "Error"
        current_status["last_result"] = f"Error: {str(e)}"
        return {"status": "error", "message": str(e)}
    finally:
        current_status["is_analyzing"] = False
        if not current_status["is_batch_mode"]:
            current_status["task"] = "Idle"
            
        if os.path.exists(temp_analyze_path):
            os.remove(temp_analyze_path)
        current_status["last_update"] = time.time()


@app.post("/describe")
async def describe_photo(file: UploadFile = File(...)):
    current_status["task"] = "Describing"
    unique_id = uuid.uuid4().hex[:8]
    temp_path = os.path.join(CURRENT_DIR, f"describe_{unique_id}.jpg")

    try:
        contents = await file.read()
        with open(temp_path, "wb") as f:
            f.write(contents)

        # DeepFace를 사용하여 감정 및 정보 분석
        backends = ["ssd", "opencv", "skip"]
        objs = None
        for backend in backends:
            try:
                objs = DeepFace.analyze(
                    img_path=temp_path,
                    actions=['emotion', 'age', 'gender'],
                    enforce_detection=False,
                    detector_backend=backend
                )
                if objs: break
            except:
                continue

        if not objs:
            return {"caption": "사진에서 아기를 찾을 수 없어요.", "emotion": "unknown"}

        analysis = objs[0]
        emotion = analysis.get('dominant_emotion', 'neutral')
        age = int(analysis.get('age', 0))
        gender = analysis.get('dominant_gender', 'Woman')

        captions = {
            "happy": "방긋 웃고 있는 예쁜 아기 모습이에요.",
            "sad": "조금 슬픈 표정을 짓고 있네요. 무슨 일이 있었나요?",
            "angry": "뿌루퉁한 표정이 너무 귀여워요!",
            "surprise": "눈을 동그랗게 뜨고 깜짝 놀란 표정이에요.",
            "fear": "조금 겁을 먹은 것 같아요. 꼭 안아주세요.",
            "disgust": "인상을 찌푸린 모습도 사랑스러워요.",
            "neutral": "평온하게 휴식을 취하고 있는 아기의 모습입니다.",
            "sleepy": "쿨쿨 잠든 천사 같은 모습이에요."
        }

        caption = captions.get(emotion, "소중한 우리 아기의 순간입니다.")
        if age < 5:
            caption = "[아기] " + caption

        return {
            "caption": caption,
            "emotion": emotion,
            "status": "success"
        }

    except Exception as e:
        logger.error(f"Description Failed: {str(e)}")
        return {"status": "error", "message": str(e)}
    finally:
        current_status["task"] = "Idle"
        if os.path.exists(temp_path):
            os.remove(temp_path)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)

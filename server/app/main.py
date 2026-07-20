from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import shutil
import os
from deepface import DeepFace
import cv2
import numpy as np

app = FastAPI(title="Haru-i AI Analysis Server")

# CORS settings for Android app communication
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
REFERENCE_DIR = os.path.join(BASE_DIR, "models", "reference_baby")
os.makedirs(REFERENCE_DIR, exist_ok=True)

@app.get("/")
async def root():
    return {"status": "online", "message": "Haru-i AI Server is running"}

@app.post("/register")
async def register_baby(file: UploadFile = File(...)):
    """
    Register the target baby's face.
    """
    file_path = os.path.join(REFERENCE_DIR, "baby_ref.jpg")
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
    print(f"[+] Successfully registered new reference baby face: {file.filename}")
    return {"status": "success", "message": "Baby face registered successfully"}

@app.post("/analyze")
async def analyze_photo(file: UploadFile = File(...)):
    """
    Check if the uploaded photo contains the registered baby's face.
    """
    ref_path = os.path.join(REFERENCE_DIR, "baby_ref.jpg")
    if not os.path.exists(ref_path):
        print("[-] Error: Reference baby face not registered")
        raise HTTPException(status_code=400, detail="Reference baby face not registered")

    # Temporary save uploaded photo
    temp_path = os.path.join(BASE_DIR, "static", f"temp_{file.filename}")
    with open(temp_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    print(f"[*] Analyzing photo: {file.filename}...")

    try:
        # Perform Face Verification
        # model_name options: VGG-Face, Facenet, OpenFace, DeepFace, DeepID, ArcFace, Dlib, SFace
        result = DeepFace.verify(
            img1_path = temp_path,
            img2_path = ref_path,
            model_name = "VGG-Face",
            distance_metric = "cosine",
            enforce_detection = False
        )

        is_verified = bool(result["verified"])
        distance = float(result["distance"])

        # Enhanced Logging for Verification
        status_msg = "[+]" if is_verified else "[-]"
        print(f"{status_msg} Result: {'MATCH' if is_verified else 'NO MATCH'} (Distance: {distance:.4f}, Threshold: {result['threshold']})")

        # Cleanup
        os.remove(temp_path)

        return {
            "is_target_baby": is_verified,
            "distance": distance,
            "threshold": float(result["threshold"])
        }

    except Exception as e:
        print(f"[-] AI Analysis Error: {str(e)}")
        if os.path.exists(temp_path):
            os.remove(temp_path)
        return {"status": "error", "message": str(e)}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

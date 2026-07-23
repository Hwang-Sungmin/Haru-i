import os
import time

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
REFERENCE_DIR = os.path.join(BASE_DIR, "models", "reference_baby")
STATIC_DIR = os.path.join(BASE_DIR, "static")
HISTORY_DIR = os.path.join(STATIC_DIR, "history")
CURRENT_DIR = os.path.join(STATIC_DIR, "current")

# Ensure directories exist
os.makedirs(REFERENCE_DIR, exist_ok=True)
os.makedirs(STATIC_DIR, exist_ok=True)
os.makedirs(HISTORY_DIR, exist_ok=True)
os.makedirs(CURRENT_DIR, exist_ok=True)

# Shared state
analysis_history = []
current_status = {
    "task": "Idle",
    "last_update": time.time(),
    "current_img": None,
    "last_result": "None",
    "is_analyzing": False,
    "stop_requested": False,
    "is_batch_mode": False  # 배치 모드 여부 추가
}

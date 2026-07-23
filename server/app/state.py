import os
import time
from supabase import create_client, Client
from dotenv import load_dotenv

# .env 파일의 절대 경로를 계산하여 로드 (어느 위치에서 실행해도 파일 유실 방지)
# state.py는 app 폴더 안에 있으므로 부모의 부모 디렉토리가 server 루트임
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
load_dotenv(os.path.join(BASE_DIR, ".env"))

# 디렉토리 설정
REFERENCE_DIR = os.path.join(BASE_DIR, "app", "models", "reference_baby")
STATIC_DIR = os.path.join(BASE_DIR, "app", "static")
HISTORY_DIR = os.path.join(STATIC_DIR, "history")
CURRENT_DIR = os.path.join(STATIC_DIR, "current")

# 디렉토리 생성
os.makedirs(REFERENCE_DIR, exist_ok=True)
os.makedirs(STATIC_DIR, exist_ok=True)
os.makedirs(HISTORY_DIR, exist_ok=True)
os.makedirs(CURRENT_DIR, exist_ok=True)

# Supabase 초기화 및 연결 확인
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")

if not SUPABASE_URL or not SUPABASE_KEY:
    print("❌ Error: SUPABASE_URL or SUPABASE_KEY is missing in .env")
    supabase = None
else:
    try:
        supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)
        print(f"✅ Connected to Supabase: {SUPABASE_URL}")
    except Exception as e:
        print(f"❌ Supabase Connection Failed: {e}")
        supabase = None

# Shared state
current_status = {
    "task": "Idle",
    "last_update": time.time(),
    "current_img": None,
    "last_result": "None",
    "is_analyzing": False,
    "stop_requested": False,
    "is_batch_mode": False
}

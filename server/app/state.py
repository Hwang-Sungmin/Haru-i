import os
import time
from supabase import create_client, Client
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

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

# Supabase initialization
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")

if not SUPABASE_URL or not SUPABASE_KEY:
    print("Warning: SUPABASE_URL or SUPABASE_KEY not set in .env file")
    supabase = None
else:
    supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)

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

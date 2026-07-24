from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse
import time
import os
import glob
from state import current_status, REFERENCE_DIR, supabase

router = APIRouter()


@router.get("/", response_class=HTMLResponse)
async def dashboard(request: Request):
    ts = int(time.time())

    # 1. Supabase에서 최근 분석 이력 가져오기
    history_data = []
    last_user_id = None
    if supabase:
        try:
            response = supabase.table("analysis_history") \
                .select("*") \
                .order("created_at", desc=True) \
                .limit(15) \
                .execute()
            history_data = response.data
            if history_data:
                last_user_id = history_data[0].get('user_id')
        except Exception as e:
            print(f"Failed to fetch history from Supabase: {e}")

    # 2. 기준 사진 결정 로직
    ref_photo_url = "/static/current/no_image.jpg" # 기본 이미지
    
    # 2-1. 특정 사용자 ID가 있는 경우 해당 사진 우선
    if last_user_id:
        local_ref_path = os.path.join(REFERENCE_DIR, f"{last_user_id}_ref.jpg")
        if os.path.exists(local_ref_path):
            ref_photo_url = f"/reference/{last_user_id}_ref.jpg?t={ts}"

    # 2-2. 위에서 사진을 못 찾았다면 폴더 내 가장 최근 파일 찾기
    if ref_photo_url.startswith("/static"):
        all_refs = glob.glob(os.path.join(REFERENCE_DIR, "*_ref.jpg"))
        if all_refs:
            # 수정 시간 순으로 정렬하여 가장 최신 것 선택
            all_refs.sort(key=os.path.getmtime)
            latest_file = all_refs[-1]
            ref_photo_url = f"/reference/{os.path.basename(latest_file)}?t={ts}"

    history_html = ""
    for h in history_data:
        res = h.get('result', 'UNKNOWN')
        color = "#4CAF50" if res == "MATCH" else ("#F44336" if res == "NO MATCH" else "#999")
        
        created_at = h.get('created_at', '')
        display_time = created_at.split('T')[-1].split('.')[0] if 'T' in created_at else created_at
        
        history_html += f"""
        <tr style="border-bottom: 1px solid #ddd;">
            <td style="padding: 12px;">{display_time}</td>
            <td style="padding: 12px; display: flex; align-items: center; gap: 10px;">
                <span style="font-size: 0.85em; color: #555;">{h.get('filename', 'Unknown')}</span>
                <br/><small style="color: #aaa; font-size: 0.7em;">{h.get('user_id', '')[:8]}...</small>
            </td>
            <td style="padding: 12px; color: {color}; font-weight: bold;">{res}</td>
            <td style="padding: 12px; font-family: 'Courier New', monospace; font-size: 0.9em;">{h.get('distance', '0.0')}</td>
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
                                결과: <span style="color: {('#4CAF50' if current_status['last_result'] == 'MATCH' else '#F44336') if current_status['last_result'] != 'None' else '#999'}">{current_status["last_result"]}</span>
                            </div>
                        </div>
                        <div class="card" style="text-align: center;">
                            <h3 style="margin-top: 0; font-size: 1em; color: #888;">우리 아기 기준 사진</h3>
                            <p style="font-size: 0.8em; color: #999;">(최근 분석 사용자 기준)</p>
                            <div style="width: 150px; height: 150px; margin: 0 auto; border-radius: 50%; border: 4px solid #EBCFB2; overflow: hidden; background: #eee;">
                                <img src='{ref_photo_url}' style='width: 100%; height: 100%; object-fit: cover;'>
                            </div>
                        </div>
                    </div>

                    <div class="card">
                        <h3 style="margin-top: 0; margin-bottom: 20px;">전체 분석 히스토리 (Supabase DB)</h3>
                        <div style="max-height: 550px; overflow-y: auto;">
                            <table style="width: 100%; border-collapse: collapse; text-align: left;">
                                <thead>
                                    <tr style="border-bottom: 2px solid #FDF2F0;">
                                        <th style="padding: 12px;">시간</th>
                                        <th style="padding: 12px;">대상 사진 / 사용자</th>
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

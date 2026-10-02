"""DocVoice - 문서 ↔ 음성 변환 앱."""

__version__ = "1.0.0"
APP_NAME = "DocVoice"


class Cancelled(Exception):
    """사용자가 작업을 취소했을 때 발생합니다."""

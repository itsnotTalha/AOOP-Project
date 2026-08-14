from fastapi import FastAPI

from app.api.routes import router

app = FastAPI(
    title="AuthVault AI Forensics",
    description="Internal image-forensics service boundary.",
    version="0.1.0",
)
app.include_router(router)

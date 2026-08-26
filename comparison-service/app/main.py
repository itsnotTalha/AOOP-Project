from fastapi import FastAPI

from app.api.comparison_routes import router as comparison_router

app = FastAPI(
    title="VaultChain Image Comparison Service",
    description="Internal deterministic image-comparison service boundary.",
    version="0.1.0",
)
app.include_router(comparison_router)

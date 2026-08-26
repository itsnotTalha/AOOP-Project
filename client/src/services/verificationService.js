import api from './api'

const ASSET_PATH = '/v1/assets'
const REVIEW_PATH = '/v1/authenticator/reviews'

async function data(request) {
  const response = await request()
  return response.data?.data
}

export function generateEvidence(assetId) {
  return data(() => api.post(`${ASSET_PATH}/${encodeURIComponent(assetId)}/verification/evidence`))
}

export function getEvidence(assetId) {
  return data(() => api.get(`${ASSET_PATH}/${encodeURIComponent(assetId)}/verification/evidence`))
}

export async function getPendingReviews() {
  const result = await data(() => api.get(`${REVIEW_PATH}/pending`))
  return Array.isArray(result) ? result : []
}

export function getReview(assetId) {
  return data(() => api.get(`${REVIEW_PATH}/${encodeURIComponent(assetId)}`))
}

export function approve(assetId, reason) {
  return data(() => api.post(`${REVIEW_PATH}/${encodeURIComponent(assetId)}/approve`, { reason }))
}

export function reject(assetId, reason) {
  return data(() => api.post(`${REVIEW_PATH}/${encodeURIComponent(assetId)}/reject`, { reason }))
}

import api from './api'

export async function getDashboardSummary() {
  try {
    const response = await api.get('/v1/dashboard/summary')
    return response.data?.data ?? emptySummary()
  } catch (error) {
    const message = error.response?.data?.message
      || (error.response ? 'Dashboard data could not be loaded.' : 'Unable to reach the server. Check your connection and try again.')
    throw new Error(message, { cause: error })
  }
}

function emptySummary() {
  return {
    totalAssets: 0,
    verifiedAssets: 0,
    storageUsed: 0,
  }
}

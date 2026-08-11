import api from './api'

const ASSET_PATH = '/v1/assets'

export class AssetServiceError extends Error {
  constructor(message, { status = null, code = null, errors = [], cause = null } = {}) {
    super(message)
    this.name = 'AssetServiceError'
    this.status = status
    this.code = code
    this.errors = errors
    this.cause = cause
  }
}

export function uploadImage(asset, onUploadProgress) {
  return uploadAsset(`${ASSET_PATH}/images`, asset, onUploadProgress)
}

export function uploadDocument(asset, onUploadProgress) {
  return uploadAsset(`${ASSET_PATH}/documents`, asset, onUploadProgress)
}

export async function getAssets() {
  const data = await requestData(() => api.get(ASSET_PATH))
  return Array.isArray(data) ? data : []
}

export function getAsset(assetId) {
  return requestData(() => api.get(`${ASSET_PATH}/${requireAssetId(assetId)}`))
}

export function verifyIntegrity(assetId) {
  return requestData(() => api.post(
    `${ASSET_PATH}/${requireAssetId(assetId)}/verify-integrity`,
  ))
}

export async function downloadAsset(assetId) {
  const normalizedAssetId = requireAssetId(assetId)

  try {
    const response = await api.get(`${ASSET_PATH}/${normalizedAssetId}/download`, {
      responseType: 'blob',
    })
    const contentType = response.headers['content-type'] || 'application/octet-stream'

    return {
      blob: response.data,
      filename: getDownloadFilename(
        response.headers['content-disposition'],
        `asset-${normalizedAssetId}`,
      ),
      contentType,
    }
  } catch (error) {
    throw normalizeAssetError(error)
  }
}

async function uploadAsset(endpoint, asset, onUploadProgress) {
  const { file, title, description } = asset ?? {}

  if (file == null) {
    throw new AssetServiceError('Select a file to upload.')
  }

  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', title ?? '')

  if (description != null && description !== '') {
    formData.append('description', description)
  }

  return requestData(() => api.post(endpoint, formData, {
    onUploadProgress: typeof onUploadProgress === 'function'
      ? onUploadProgress
      : undefined,
  }))
}

async function requestData(request) {
  try {
    const response = await request()
    return response.data?.data
  } catch (error) {
    throw normalizeAssetError(error)
  }
}

function requireAssetId(assetId) {
  if (typeof assetId !== 'string' || assetId.trim() === '') {
    throw new AssetServiceError('Asset ID is required.')
  }

  return encodeURIComponent(assetId.trim())
}

function normalizeAssetError(error) {
  if (error instanceof AssetServiceError) {
    return error
  }

  const status = error.response?.status ?? null
  const response = error.response?.data
  const code = response?.code ?? null
  const errors = Array.isArray(response?.errors)
    ? response.errors.filter((message) => typeof message === 'string' && message.trim() !== '')
    : []

  let message
  if (code === 'DUPLICATE_FILE') {
    message = 'This file has already been uploaded.'
  } else if (errors.length > 0) {
    message = errors.join(' ')
  } else if (typeof response?.message === 'string' && response.message.trim() !== '') {
    message = response.message
  } else {
    message = fallbackErrorMessage(status)
  }

  return new AssetServiceError(message, {
    status,
    code,
    errors,
    cause: error,
  })
}

function fallbackErrorMessage(status) {
  switch (status) {
    case 400:
      return 'Check the asset details and try again.'
    case 401:
      return 'Your session has expired. Sign in and try again.'
    case 403:
      return 'You do not have access to this asset.'
    case 404:
      return 'The requested asset could not be found.'
    case 409:
      return 'This asset conflicts with an existing file.'
    case 413:
      return 'The selected file is too large.'
    case 415:
      return 'The selected file type is not supported.'
    case 422:
      return 'The selected file is malformed or corrupt.'
    default:
      return status == null
        ? 'Unable to reach the server. Check your connection and try again.'
        : 'The asset request could not be completed. Please try again.'
  }
}

function getDownloadFilename(contentDisposition, fallback) {
  if (typeof contentDisposition !== 'string') {
    return fallback
  }

  const encodedMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  const basicMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  let filename = encodedMatch?.[1] ?? basicMatch?.[1]

  if (!filename) {
    return fallback
  }

  try {
    filename = decodeURIComponent(filename)
  } catch {
    // Use the unencoded value when a server returns an invalid escape sequence.
  }

  filename = filename
    .split(/[\\/]/)
    .pop()
    .replace(/[\r\n\u0000-\u001f\u007f]/g, '_')
    .trim()

  return filename || fallback
}

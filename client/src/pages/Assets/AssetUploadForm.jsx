import { useRef, useState } from 'react'
import {
  FileText,
  Image as ImageIcon,
  LoaderCircle,
  UploadCloud,
  X,
} from 'lucide-react'

import * as assetService from '../../services/assetService'

const FILE_RULES = {
  IMAGE: {
    accept: '.jpg,.jpeg,.png,image/jpeg,image/png',
    extensions: ['jpg', 'jpeg', 'png'],
    maxBytes: 25 * 1024 * 1024,
    help: 'JPEG or PNG, up to 25 MB',
  },
  DOCUMENT: {
    accept: '.pdf,application/pdf',
    extensions: ['pdf'],
    maxBytes: 50 * 1024 * 1024,
    help: 'PDF, up to 50 MB',
  },
}

export default function AssetUploadForm({ onUploaded }) {
  const fileInputRef = useRef(null)
  const [assetType, setAssetType] = useState('IMAGE')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [file, setFile] = useState(null)
  const [isDragging, setIsDragging] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState('')

  const rules = FILE_RULES[assetType]

  const changeAssetType = (nextType) => {
    if (isUploading || nextType === assetType) return
    setAssetType(nextType)
    setFile(null)
    setError('')
    setProgress(0)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const selectFile = (nextFile) => {
    setError('')

    if (!nextFile) {
      setFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
      return
    }

    const validationMessage = validateFile(nextFile, assetType)
    if (validationMessage) {
      setFile(null)
      setError(validationMessage)
      if (fileInputRef.current) fileInputRef.current.value = ''
      return
    }

    setFile(nextFile)
  }

  const handleDrop = (event) => {
    event.preventDefault()
    setIsDragging(false)
    if (!isUploading) selectFile(event.dataTransfer.files?.[0])
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')

    const validationMessage = validateForm({ title, description, file, assetType })
    if (validationMessage) {
      setError(validationMessage)
      return
    }

    setIsUploading(true)
    setProgress(0)

    try {
      const upload = assetType === 'IMAGE'
        ? assetService.uploadImage
        : assetService.uploadDocument
      const uploadedAsset = await upload(
        { file, title: title.trim(), description: description.trim() },
        (progressEvent) => {
          const percentage = progressEvent.total
            ? Math.round((progressEvent.loaded / progressEvent.total) * 100)
            : Math.round((progressEvent.progress ?? 0) * 100)
          setProgress(Math.min(100, Math.max(0, percentage)))
        },
      )

      setProgress(100)
      setTitle('')
      setDescription('')
      setFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
      onUploaded(uploadedAsset)
    } catch (uploadError) {
      setError(uploadError.message || 'The file could not be uploaded. Please try again.')
    } finally {
      setIsUploading(false)
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200/80 bg-white shadow-sm shadow-slate-200/30">
      <div className="border-b border-slate-100 px-5 py-5 sm:px-6">
        <p className="text-xs font-bold uppercase tracking-[0.16em] text-indigo-600">New asset</p>
        <h2 className="mt-1.5 text-xl font-bold tracking-tight">Upload and verify</h2>
        <p className="mt-1 text-sm text-slate-500">The file is validated and assigned a SHA-256 fingerprint.</p>
      </div>

      <form className="space-y-5 p-5 sm:p-6" onSubmit={handleSubmit}>
        <fieldset disabled={isUploading}>
          <legend className="mb-2 text-sm font-semibold text-slate-700">Asset type</legend>
          <div className="grid grid-cols-2 gap-2 rounded-xl bg-slate-100 p-1">
            {[
              { value: 'IMAGE', label: 'Image', icon: ImageIcon },
              { value: 'DOCUMENT', label: 'Document', icon: FileText },
            ].map(({ value, label, icon: Icon }) => (
              <button
                className={`flex items-center justify-center gap-2 rounded-lg px-3 py-2.5 text-sm font-semibold transition ${
                  assetType === value
                    ? 'bg-white text-indigo-700 shadow-sm'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
                key={value}
                onClick={() => changeAssetType(value)}
                type="button"
              >
                <Icon size={17} /> {label}
              </button>
            ))}
          </div>
        </fieldset>

        <div>
          <input
            accept={rules.accept}
            className="sr-only"
            disabled={isUploading}
            id="asset-file"
            onChange={(event) => selectFile(event.target.files?.[0])}
            ref={fileInputRef}
            type="file"
          />
          <label
            className={`flex min-h-44 cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-5 py-7 text-center transition ${
              isDragging
                ? 'border-indigo-500 bg-indigo-50'
                : 'border-slate-200 bg-slate-50/60 hover:border-indigo-300 hover:bg-indigo-50/40'
            } ${isUploading ? 'cursor-not-allowed opacity-60' : ''}`}
            htmlFor="asset-file"
            onDragEnter={(event) => {
              event.preventDefault()
              if (!isUploading) setIsDragging(true)
            }}
            onDragLeave={(event) => {
              event.preventDefault()
              setIsDragging(false)
            }}
            onDragOver={(event) => event.preventDefault()}
            onDrop={handleDrop}
          >
            <span className="grid h-12 w-12 place-items-center rounded-2xl bg-indigo-100 text-indigo-600">
              <UploadCloud size={23} />
            </span>
            <span className="mt-4 text-sm font-semibold text-slate-800">Drop your file here or browse</span>
            <span className="mt-1.5 text-xs text-slate-400">{rules.help}</span>
          </label>
        </div>

        {file && (
          <div className="flex items-center gap-3 rounded-xl border border-indigo-100 bg-indigo-50/60 p-3">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-white text-indigo-600 shadow-sm">
              {assetType === 'IMAGE' ? <ImageIcon size={18} /> : <FileText size={18} />}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-slate-800">{file.name}</p>
              <p className="mt-0.5 text-xs text-slate-500">{formatFileSize(file.size)}</p>
            </div>
            <button
              aria-label="Remove selected file"
              className="rounded-lg p-2 text-slate-400 hover:bg-white hover:text-slate-700"
              disabled={isUploading}
              onClick={() => selectFile(null)}
              type="button"
            >
              <X size={17} />
            </button>
          </div>
        )}

        <div>
          <label className="mb-1.5 block text-sm font-medium text-slate-700" htmlFor="asset-title">Title</label>
          <input
            className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100"
            disabled={isUploading}
            id="asset-title"
            maxLength={150}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="Give this asset a clear title"
            value={title}
          />
          <p className="mt-1.5 text-right text-[11px] text-slate-400">{title.length}/150</p>
        </div>

        <div>
          <label className="mb-1.5 block text-sm font-medium text-slate-700" htmlFor="asset-description">
            Description <span className="font-normal text-slate-400">(optional)</span>
          </label>
          <textarea
            className="min-h-24 w-full resize-y rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100"
            disabled={isUploading}
            id="asset-description"
            maxLength={2000}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="Add context about this file"
            value={description}
          />
          <p className="mt-1.5 text-right text-[11px] text-slate-400">{description.length}/2000</p>
        </div>

        {error && (
          <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
            {error}
          </div>
        )}

        {isUploading && (
          <div aria-live="polite">
            <div className="mb-2 flex items-center justify-between text-xs font-semibold text-slate-600">
              <span>Uploading securely</span>
              <span>{progress}%</span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full rounded-full bg-indigo-600 transition-[width] duration-200" style={{ width: `${progress}%` }} />
            </div>
          </div>
        )}

        <button
          className="flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-indigo-600/20 transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
          disabled={isUploading}
          type="submit"
        >
          {isUploading ? <LoaderCircle className="animate-spin" size={18} /> : <UploadCloud size={18} />}
          {isUploading ? 'Uploading and verifying…' : `Upload ${assetType === 'IMAGE' ? 'image' : 'document'}`}
        </button>
      </form>
    </section>
  )
}

function validateForm({ title, description, file, assetType }) {
  const normalizedTitle = title.trim()
  if (!normalizedTitle) return 'Enter a title for this asset.'
  if (normalizedTitle.length > 150) return 'Title must be 150 characters or fewer.'
  if (description.trim().length > 2000) return 'Description must be 2,000 characters or fewer.'
  if (!file) return 'Select a file to upload.'
  return validateFile(file, assetType)
}

function validateFile(file, assetType) {
  const rules = FILE_RULES[assetType]
  const extension = file.name.split('.').pop()?.toLowerCase()

  if (!rules.extensions.includes(extension)) {
    return assetType === 'IMAGE'
      ? 'Choose a JPEG or PNG image.'
      : 'Choose a PDF document.'
  }
  if (file.size === 0) return 'The selected file is empty.'
  if (file.size > rules.maxBytes) {
    return `${assetType === 'IMAGE' ? 'Images' : 'Documents'} must be ${formatFileSize(rules.maxBytes)} or smaller.`
  }
  return ''
}

function formatFileSize(bytes) {
  if (!Number.isFinite(bytes) || bytes < 1) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const unitIndex = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  const value = bytes / (1024 ** unitIndex)
  return `${value.toFixed(unitIndex === 0 || value >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

import { useEffect, useState } from 'react'
import { AlertCircle, FileText, Image as ImageIcon, LoaderCircle } from 'lucide-react'

import * as assetService from '../../services/assetService'

export default function AssetPreview({ asset }) {
  const [previewUrl, setPreviewUrl] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    let objectUrl = ''

    setPreviewUrl('')
    setError('')
    setIsLoading(true)

    assetService.downloadAsset(asset.assetId)
      .then(({ blob, contentType }) => {
        if (!active) return

        const resolvedContentType = asset.mimeType || contentType || blob.type
        const previewBlob = resolvedContentType && blob.type !== resolvedContentType
          ? blob.slice(0, blob.size, resolvedContentType)
          : blob
        objectUrl = URL.createObjectURL(previewBlob)
        setPreviewUrl(objectUrl)
      })
      .catch((requestError) => {
        if (active) {
          setError(requestError.message || 'The file preview could not be loaded.')
        }
      })
      .finally(() => {
        if (active) setIsLoading(false)
      })

    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [asset.assetId, asset.mimeType])

  const isImage = asset.assetType === 'IMAGE'

  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm shadow-slate-200/30">
      <div className="flex items-center gap-2 border-b border-slate-100 px-5 py-4">
        {isImage ? <ImageIcon className="text-amber-600" size={18} /> : <FileText className="text-violet-600" size={18} />}
        <h2 className="font-bold text-slate-900">Preview</h2>
      </div>

      <div className="grid min-h-72 place-items-center bg-slate-50 p-4 sm:min-h-96 sm:p-6 lg:min-h-[34rem]">
        {isLoading && (
          <div className="flex flex-col items-center text-sm text-slate-500" role="status">
            <LoaderCircle className="mb-3 animate-spin text-indigo-600" size={28} />
            Loading secure preview…
          </div>
        )}

        {!isLoading && (error || !previewUrl) && (
          <div className="max-w-sm text-center" role="alert">
            <span className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-white text-slate-500 shadow-sm"><AlertCircle size={22} /></span>
            <h3 className="mt-4 font-bold text-slate-800">Preview unavailable</h3>
            <p className="mt-1 text-sm leading-6 text-slate-500">{error || 'This file cannot be previewed in the browser. You can still download it.'}</p>
          </div>
        )}

        {!isLoading && !error && previewUrl && isImage && (
          <img
            alt={`Preview of ${asset.title}`}
            className="max-h-[70vh] max-w-full object-contain"
            onError={() => setError('The browser could not display this image preview.')}
            src={previewUrl}
          />
        )}

        {!isLoading && !error && previewUrl && !isImage && (
          <object
            aria-label={`PDF preview of ${asset.title}`}
            className="h-[65vh] min-h-96 w-full rounded-xl bg-white"
            data={previewUrl}
            type="application/pdf"
          >
            <div className="grid h-full place-items-center p-8 text-center text-sm text-slate-500">
              This browser cannot display the PDF preview. Use Download to open the file.
            </div>
          </object>
        )}
      </div>
    </section>
  )
}

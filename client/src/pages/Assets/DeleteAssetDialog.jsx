import { useEffect } from 'react'
import { AlertCircle, LoaderCircle, Trash2, X } from 'lucide-react'

export default function DeleteAssetDialog({ asset, error, isDeleting, onCancel, onConfirm }) {
  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === 'Escape' && !isDeleting) onCancel()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isDeleting, onCancel])

  const assetName = asset.title || asset.originalFilename || 'this asset'

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/55 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isDeleting) onCancel()
      }}
    >
      <div aria-describedby="delete-asset-description" aria-labelledby="delete-asset-title" aria-modal="true" className="w-full max-w-md rounded-2xl bg-white p-5 shadow-2xl sm:p-6" role="dialog">
        <div className="flex items-start justify-between gap-4">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-rose-50 text-rose-600"><Trash2 size={21} /></span>
          <button aria-label="Close delete confirmation" className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50" disabled={isDeleting} onClick={onCancel} type="button"><X size={19} /></button>
        </div>

        <h2 className="mt-5 text-lg font-bold text-slate-900" id="delete-asset-title">Delete “{assetName}”?</h2>
        <p className="mt-2 text-sm leading-6 text-slate-600" id="delete-asset-description">
          This permanently removes the asset and its verification history. This action cannot be undone.
        </p>

        {error && (
          <div className="mt-4 flex gap-2 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700" role="alert">
            <AlertCircle className="mt-0.5 shrink-0" size={17} /> {error}
          </div>
        )}

        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button autoFocus className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50" disabled={isDeleting} onClick={onCancel} type="button">Cancel</button>
          <button className="inline-flex items-center justify-center gap-2 rounded-xl bg-rose-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-60" disabled={isDeleting} onClick={onConfirm} type="button">
            {isDeleting ? <LoaderCircle className="animate-spin" size={17} /> : <Trash2 size={17} />}
            {isDeleting ? 'Deleting…' : 'Delete Asset'}
          </button>
        </div>
      </div>
    </div>
  )
}

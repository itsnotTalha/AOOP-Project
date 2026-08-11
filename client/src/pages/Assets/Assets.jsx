import { useCallback, useEffect, useState } from 'react'
import { Check, CheckCircle2, Clipboard, ShieldCheck } from 'lucide-react'

import * as assetService from '../../services/assetService'
import AssetList from './AssetList'
import AssetUploadForm from './AssetUploadForm'

export default function Assets() {
  const [assets, setAssets] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [uploadedAsset, setUploadedAsset] = useState(null)
  const [copiedHash, setCopiedHash] = useState(false)
  const [verifyingAssetId, setVerifyingAssetId] = useState(null)
  const [verificationResults, setVerificationResults] = useState({})
  const [verificationErrors, setVerificationErrors] = useState({})

  const loadAssets = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setIsLoading(true)
    setListError('')

    try {
      setAssets(await assetService.getAssets())
    } catch (error) {
      setListError(error.message || 'Assets could not be loaded. Please try again.')
    } finally {
      if (!silent) setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    let active = true

    assetService.getAssets()
      .then((result) => active && setAssets(result))
      .catch((error) => active && setListError(error.message || 'Assets could not be loaded. Please try again.'))
      .finally(() => active && setIsLoading(false))

    return () => { active = false }
  }, [])

  const handleUploaded = (asset) => {
    setUploadedAsset(asset)
    setCopiedHash(false)
    setAssets((current) => [asset, ...current.filter((item) => item.assetId !== asset.assetId)])
    loadAssets({ silent: true })
  }

  const handleVerify = async (assetId) => {
    setVerifyingAssetId(assetId)
    setVerificationErrors((current) => ({ ...current, [assetId]: '' }))
    setVerificationResults((current) => ({ ...current, [assetId]: null }))

    try {
      const result = await assetService.verifyIntegrity(assetId)
      setVerificationResults((current) => ({ ...current, [assetId]: result }))
      await loadAssets({ silent: true })
    } catch (error) {
      setVerificationErrors((current) => ({
        ...current,
        [assetId]: error.message || 'Integrity verification could not be completed.',
      }))
    } finally {
      setVerifyingAssetId(null)
    }
  }

  const copyHash = async () => {
    if (!uploadedAsset?.sha256Hash) return

    try {
      await navigator.clipboard.writeText(uploadedAsset.sha256Hash)
      setCopiedHash(true)
      window.setTimeout(() => setCopiedHash(false), 1800)
    } catch {
      setCopiedHash(false)
    }
  }

  return (
    <div className="space-y-6 lg:space-y-8">
      <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.14em] text-indigo-600">
            <span className="h-px w-5 bg-indigo-500" /> Integrity workspace
          </div>
          <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Assets</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
            Upload supported files, preserve their SHA-256 fingerprint, and verify stored bytes whenever needed.
          </p>
        </div>
        <div className="flex w-fit items-center gap-2 rounded-xl border border-emerald-100 bg-emerald-50 px-3.5 py-2 text-xs font-semibold text-emerald-700">
          <ShieldCheck size={16} /> Owner-only access
        </div>
      </section>

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(360px,0.72fr)_minmax(0,1.28fr)]">
        <div className="space-y-6">
          <AssetUploadForm onUploaded={handleUploaded} />
          {uploadedAsset && (
            <UploadSuccessCard
              asset={uploadedAsset}
              copied={copiedHash}
              onCopy={copyHash}
            />
          )}
        </div>

        <AssetList
          assets={assets}
          error={listError}
          isLoading={isLoading}
          onRetry={() => loadAssets()}
          onVerify={handleVerify}
          verificationErrors={verificationErrors}
          verificationResults={verificationResults}
          verifyingAssetId={verifyingAssetId}
        />
      </div>
    </div>
  )
}

function UploadSuccessCard({ asset, copied, onCopy }) {
  return (
    <section className="overflow-hidden rounded-2xl border border-emerald-200 bg-white shadow-sm shadow-emerald-100/50" aria-live="polite">
      <div className="bg-emerald-50 px-5 py-5 sm:px-6">
        <div className="flex items-center gap-3">
          <span className="grid h-11 w-11 place-items-center rounded-xl bg-emerald-600 text-white shadow-lg shadow-emerald-600/20"><CheckCircle2 size={22} /></span>
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.14em] text-emerald-700">VERIFIED / Approved</p>
            <h2 className="mt-1 text-lg font-bold text-emerald-950">Upload completed</h2>
          </div>
        </div>
      </div>

      <div className="space-y-4 p-5 sm:p-6">
        <div>
          <h3 className="text-base font-bold text-slate-900">{asset.title}</h3>
          <p className="mt-1 text-sm text-slate-500">{asset.assetType === 'IMAGE' ? 'Image' : 'Document'} · {asset.originalFilename}</p>
        </div>

        <div className="grid grid-cols-2 gap-3 text-sm">
          <ResultDetail label="File size" value={formatFileSize(asset.fileSize)} />
          <ResultDetail label="Uploaded" value={formatDate(asset.uploadDate)} />
        </div>

        <div className="rounded-xl border border-slate-200 bg-slate-50 p-3.5">
          <div className="flex items-center justify-between gap-3">
            <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500">SHA-256</p>
            <button className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-xs font-semibold text-indigo-600 hover:bg-indigo-50" onClick={onCopy} type="button">
              {copied ? <Check size={14} /> : <Clipboard size={14} />}
              {copied ? 'Copied' : 'Copy hash'}
            </button>
          </div>
          <p className="mt-2 break-all font-mono text-[11px] leading-5 text-slate-700">{asset.sha256Hash}</p>
        </div>
      </div>
    </section>
  )
}

function ResultDetail({ label, value }) {
  return <div><p className="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">{label}</p><p className="mt-1 font-semibold text-slate-700">{value}</p></div>
}

function formatFileSize(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value < 1) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const unitIndex = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  const size = value / (1024 ** unitIndex)
  return `${size.toFixed(unitIndex === 0 || size >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

function formatDate(value) {
  if (!value) return 'Unknown'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Unknown'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

import { useCallback, useEffect, useState } from 'react'
import {
  AlertCircle,
  ArrowLeft,
  Check,
  Clipboard,
  Download,
  FileText,
  Image as ImageIcon,
  LoaderCircle,
  RefreshCw,
  ShieldCheck,
  Trash2,
} from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'

import * as assetService from '../../services/assetService'
import * as verificationService from '../../services/verificationService'
import AssetPreview from './AssetPreview'
import DeleteAssetDialog from './DeleteAssetDialog'
import VerificationHistoryList from './VerificationHistoryList'

export default function AssetDetails() {
  const { assetId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const assetsPath = `/assets${location.search}`
  const [asset, setAsset] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [history, setHistory] = useState([])
  const [isHistoryLoading, setIsHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState('')
  const [historyVersion, setHistoryVersion] = useState(0)
  const [copied, setCopied] = useState(false)
  const [copyError, setCopyError] = useState('')
  const [isVerifying, setIsVerifying] = useState(false)
  const [verifyError, setVerifyError] = useState('')
  const [verificationResult, setVerificationResult] = useState(null)
  const [isDownloading, setIsDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState('')
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const [evidence, setEvidence] = useState(null)
  const [evidenceError, setEvidenceError] = useState('')
  const [isGeneratingEvidence, setIsGeneratingEvidence] = useState(false)

  const loadAsset = useCallback(async () => {
    setIsLoading(true)
    setLoadError(null)
    try {
      setAsset(await assetService.getAsset(assetId))
    } catch (error) {
      setLoadError(error)
    } finally {
      setIsLoading(false)
    }
  }, [assetId])

  useEffect(() => {
    let active = true
    setAsset(null)
    setHistory([])
    setHistoryError('')
    setVerificationResult(null)
    setIsLoading(true)
    setLoadError(null)

    assetService.getAsset(assetId)
      .then((result) => active && setAsset(result))
      .catch((error) => active && setLoadError(error))
      .finally(() => active && setIsLoading(false))

    return () => { active = false }
  }, [assetId])

  useEffect(() => {
    if (!asset) return undefined

    let active = true
    setIsHistoryLoading(true)
    setHistoryError('')

    assetService.getVerificationHistory(asset.assetId)
      .then((result) => active && setHistory(result))
      .catch((error) => active && setHistoryError(error.message || 'Verification history could not be loaded.'))
      .finally(() => active && setIsHistoryLoading(false))

    return () => { active = false }
  }, [asset?.assetId, historyVersion])

  useEffect(() => {
    if (!asset?.assetId) return undefined
    let active = true
    setEvidenceError('')
    verificationService.getEvidence(asset.assetId)
      .then((result) => active && setEvidence(result))
      .catch((error) => {
        if (active && error.response?.status !== 404) {
          setEvidenceError(error.response?.data?.message || 'Verification evidence could not be loaded.')
        }
      })
    return () => { active = false }
  }, [asset?.assetId])

  const handleGenerateEvidence = async () => {
    setIsGeneratingEvidence(true)
    setEvidenceError('')
    try {
      const result = await verificationService.generateEvidence(asset.assetId)
      setEvidence(result)
      setAsset((current) => ({ ...current, verificationStatus: 'PENDING_REVIEW' }))
    } catch (error) {
      setEvidenceError(error.response?.data?.message || 'Verification evidence could not be generated.')
    } finally {
      setIsGeneratingEvidence(false)
    }
  }

  const handleCopyHash = async () => {
    if (!asset?.sha256Hash) return
    setCopyError('')
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable')
      await navigator.clipboard.writeText(asset.sha256Hash)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      setCopied(false)
      setCopyError('Copy is unavailable in this browser. Select the hash to copy it manually.')
    }
  }

  const handleVerify = async () => {
    setIsVerifying(true)
    setVerifyError('')
    setVerificationResult(null)
    try {
      const result = await assetService.verifyIntegrity(asset.assetId)
      setVerificationResult(result)
      setAsset((current) => ({
        ...current,
        verificationStatus: result.verificationStatus,
        lastVerifiedAt: result.verifiedAt,
      }))
      setHistoryVersion((current) => current + 1)
    } catch (error) {
      setVerifyError(error.message || 'Integrity verification could not be completed.')
    } finally {
      setIsVerifying(false)
    }
  }

  const handleDownload = async () => {
    setIsDownloading(true)
    setDownloadError('')
    try {
      const { blob, filename } = await assetService.downloadAsset(asset.assetId)
      const objectUrl = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = objectUrl
      anchor.download = filename || asset.originalFilename || 'asset-download'
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
    } catch (error) {
      setDownloadError(error.message || 'The asset could not be downloaded.')
    } finally {
      setIsDownloading(false)
    }
  }

  const handleDelete = async () => {
    setIsDeleting(true)
    setDeleteError('')
    try {
      await assetService.deleteAsset(asset.assetId)
      navigate(assetsPath)
    } catch (error) {
      setDeleteError(error.message || 'The asset could not be deleted.')
      setIsDeleting(false)
    }
  }

  if (isLoading) return <AssetDetailsLoading />
  if (loadError || !asset) return <AssetDetailsError assetsPath={assetsPath} error={loadError} onRetry={loadAsset} />

  const verificationApproved = verificationResult?.hashMatches === true

  return (
    <div className="space-y-6 lg:space-y-8">
      <header>
        <Link className="inline-flex items-center gap-2 text-sm font-semibold text-slate-500 hover:text-indigo-700" to={assetsPath}><ArrowLeft size={17} /> Back to Assets</Link>
        <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <p className="text-xs font-bold uppercase tracking-[0.16em] text-indigo-600">Asset details</p>
            <h1 className="mt-2 break-words text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">{asset.title}</h1>
            <p className="mt-2 break-all text-sm text-slate-500">{asset.originalFilename}</p>
          </div>
          <StatusBadge status={asset.verificationStatus} prominent />
        </div>
      </header>

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.25fr)_minmax(340px,0.75fr)]">
        <AssetPreview asset={asset} />

        <div className="space-y-6">
          <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm shadow-slate-200/30 sm:p-6">
            <div className="flex items-center gap-2"><ShieldCheck className="text-teal-600" size={19} /><h2 className="font-bold text-slate-900">Human verification</h2></div>
            <p className="mt-3 text-sm leading-6 text-slate-500">Generate a stable evidence snapshot for an authorized authenticator. Machine evidence does not make the final decision.</p>
            {evidence ? (
              <div className="mt-4 grid gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm sm:grid-cols-2">
                <Metadata label="Evidence status" value={asset.verificationStatus === 'PENDING_REVIEW' ? 'Pending human review' : asset.verificationStatus} />
                <Metadata label="Similar candidates" value={evidence.similarCandidateCount ?? 'Not applicable'} />
                <Metadata label="Comparison" value={evidence.comparisonPerformed ? evidence.comparisonStatus : evidence.comparisonReason} />
                <Metadata label="Fabric lookup" value={evidence.fabricStatus} />
              </div>
            ) : (
              <button className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-teal-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-700 disabled:opacity-60" disabled={isGeneratingEvidence} onClick={handleGenerateEvidence} type="button">
                {isGeneratingEvidence ? <LoaderCircle className="animate-spin" size={17} /> : <ShieldCheck size={17} />}
                {isGeneratingEvidence ? 'Generating evidence…' : 'Generate verification evidence'}
              </button>
            )}
            {evidenceError && <ErrorMessage message={evidenceError} />}
          </section>

          <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm shadow-slate-200/30 sm:p-6">
            <div className="flex items-center gap-2">
              {asset.assetType === 'IMAGE' ? <ImageIcon className="text-amber-600" size={19} /> : <FileText className="text-violet-600" size={19} />}
              <h2 className="font-bold text-slate-900">Asset information</h2>
            </div>
            <dl className="mt-5 grid gap-x-5 gap-y-4 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
              <Metadata label="Title" value={asset.title} />
              <Metadata label="Asset type" value={formatLabel(asset.assetType)} />
              <Metadata label="Original filename" value={asset.originalFilename} breakAll />
              <Metadata label="MIME type" value={asset.mimeType} />
              <Metadata label="File size" value={formatFileSize(asset.fileSize)} />
              <Metadata label="Uploaded" value={formatDate(asset.uploadDate)} />
              <Metadata label="Verification status" value={asset.verificationStatus || 'PENDING'} />
              <Metadata label="Last integrity event" value={formatDate(asset.lastVerifiedAt, 'No integrity event')} />
            </dl>
            <div className="mt-5 border-t border-slate-100 pt-5">
              <Metadata label="Description" value={asset.description || 'No description provided.'} />
            </div>
          </section>

          <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm shadow-slate-200/30 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2"><ShieldCheck className="text-indigo-600" size={19} /><h2 className="font-bold text-slate-900">Integrity</h2></div>
              <button className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-indigo-600 hover:bg-indigo-50" onClick={handleCopyHash} type="button">
                {copied ? <Check size={15} /> : <Clipboard size={15} />} {copied ? 'Copied' : 'Copy hash'}
              </button>
            </div>
            <p className="mt-4 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500">SHA-256</p>
            <p className="mt-2 select-all break-all rounded-xl border border-slate-200 bg-slate-50 p-3.5 font-mono text-xs leading-6 text-slate-700">{asset.sha256Hash}</p>
            {copyError && <p className="mt-2 text-xs text-rose-600" role="alert">{copyError}</p>}
            <p className="mt-4 text-xs leading-5 text-slate-500">Verification confirms that the stored file matches the SHA-256 fingerprint recorded during upload.</p>

            <button className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-lg shadow-indigo-600/15 hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60" disabled={isVerifying} onClick={handleVerify} type="button">
              {isVerifying ? <LoaderCircle className="animate-spin" size={17} /> : <ShieldCheck size={17} />}
              {isVerifying ? 'Verifying…' : 'Verify Integrity'}
            </button>
            {verifyError && <ErrorMessage message={verifyError} />}
            {verificationResult && (
              <div className={`mt-4 rounded-xl border p-4 ${verificationApproved ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50'}`} aria-live="polite">
                <p className={`text-sm font-bold ${verificationApproved ? 'text-emerald-800' : 'text-rose-800'}`}>{verificationApproved ? 'Integrity match' : 'Integrity check failed'}</p>
                <p className="mt-1 text-xs text-slate-600">{verificationApproved ? 'The current stored bytes match the upload hash.' : 'The current stored bytes do not match the upload hash, or the stored file could not be read.'}</p>
              </div>
            )}
          </section>

          <VerificationHistoryList error={historyError} history={history} isLoading={isHistoryLoading} onRetry={() => setHistoryVersion((current) => current + 1)} />

          <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm shadow-slate-200/30 sm:p-6">
            <h2 className="font-bold text-slate-900">Actions</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
              <button className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-700 disabled:opacity-60" disabled={isDownloading} onClick={handleDownload} type="button">
                {isDownloading ? <LoaderCircle className="animate-spin" size={17} /> : <Download size={17} />} {isDownloading ? 'Downloading…' : 'Download'}
              </button>
              <button className="inline-flex items-center justify-center gap-2 rounded-xl border border-rose-200 px-4 py-2.5 text-sm font-semibold text-rose-700 hover:bg-rose-50" onClick={() => { setDeleteError(''); setShowDeleteDialog(true) }} type="button"><Trash2 size={17} /> Delete Asset</button>
            </div>
            {downloadError && <ErrorMessage message={downloadError} />}
          </section>
        </div>
      </div>

      {showDeleteDialog && (
        <DeleteAssetDialog
          asset={asset}
          error={deleteError}
          isDeleting={isDeleting}
          onCancel={() => { setShowDeleteDialog(false); setDeleteError('') }}
          onConfirm={handleDelete}
        />
      )}
    </div>
  )
}

function AssetDetailsLoading() {
  return <div className="space-y-6" aria-label="Loading asset details"><div className="h-5 w-32 animate-pulse rounded bg-slate-100" /><div className="h-10 w-72 max-w-full animate-pulse rounded bg-slate-100" /><div className="grid gap-6 xl:grid-cols-2"><div className="h-96 animate-pulse rounded-2xl bg-slate-100" /><div className="h-96 animate-pulse rounded-2xl bg-slate-100" /></div></div>
}

function AssetDetailsError({ assetsPath, error, onRetry }) {
  const notFound = error?.status === 404
  const inaccessible = error?.status === 401 || error?.status === 403
  const title = notFound ? 'Asset not found' : inaccessible ? 'Asset unavailable' : 'Unable to load asset'
  const message = notFound
    ? 'This asset does not exist or is not available to your account.'
    : inaccessible
      ? 'Your session cannot access this private asset.'
      : error?.message || 'The asset details could not be loaded. Please try again.'

  return (
    <div className="rounded-2xl border border-slate-200 bg-white px-6 py-16 text-center shadow-sm">
      <AlertCircle className="mx-auto text-rose-500" size={32} />
      <h1 className="mt-4 text-xl font-bold text-slate-900">{title}</h1>
      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">{message}</p>
      <div className="mt-6 flex flex-col justify-center gap-2 sm:flex-row">
        <Link className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50" to={assetsPath}><ArrowLeft size={16} /> Back to Assets</Link>
        {!notFound && !inaccessible && <button className="inline-flex items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700" onClick={onRetry} type="button"><RefreshCw size={16} /> Try again</button>}
      </div>
    </div>
  )
}

function Metadata({ breakAll = false, label, value }) {
  return <div className="min-w-0"><dt className="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">{label}</dt><dd className={`mt-1 text-sm font-semibold leading-6 text-slate-700 ${breakAll ? 'break-all' : 'break-words'}`}>{value}</dd></div>
}

function StatusBadge({ prominent = false, status }) {
  const classes = status === 'VERIFIED'
    ? 'bg-emerald-50 text-emerald-700 ring-emerald-600/10'
    : status === 'REJECTED'
      ? 'bg-rose-50 text-rose-700 ring-rose-600/10'
      : 'bg-amber-50 text-amber-700 ring-amber-600/10'
  const label = status === 'PENDING_REVIEW'
    ? 'PENDING REVIEW'
    : status === 'VERIFIED' && prominent
      ? 'VERIFIED / Approved'
      : status || 'PENDING'
  return <span className={`w-fit shrink-0 rounded-full font-bold ring-1 ring-inset ${classes} ${prominent ? 'px-3.5 py-2 text-xs' : 'px-2.5 py-1 text-[10px]'}`}>{label}</span>
}

function ErrorMessage({ message }) {
  return <div className="mt-4 flex gap-2 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700" role="alert"><AlertCircle className="mt-0.5 shrink-0" size={17} /> {message}</div>
}

function formatLabel(value) {
  if (!value) return 'Unknown'
  return value.charAt(0) + value.slice(1).toLowerCase()
}

function formatFileSize(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value < 1) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const unitIndex = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  const size = value / (1024 ** unitIndex)
  return `${size.toFixed(unitIndex === 0 || size >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

function formatDate(value, empty = 'Unknown') {
  if (!value) return empty
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return empty
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

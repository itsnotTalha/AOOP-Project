import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  FileText,
  FileUp,
  HardDrive,
  Image as ImageIcon,
  LoaderCircle,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react'
import { Link } from 'react-router-dom'

import { useAuth } from '../../hooks/useAuth'
import * as assetService from '../../services/assetService'
import * as dashboardService from '../../services/dashboardService'

const EMPTY_SUMMARY = { totalAssets: 0, verifiedAssets: 0, storageUsed: 0 }

export default function Dashboard() {
  const { currentUser } = useAuth()
  const [summary, setSummary] = useState(EMPTY_SUMMARY)
  const [assets, setAssets] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [verifyingAssetId, setVerifyingAssetId] = useState('')
  const [actionMessage, setActionMessage] = useState(null)

  const loadDashboard = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setIsLoading(true)
    setError('')

    const [summaryResult, assetsResult] = await Promise.allSettled([
      dashboardService.getDashboardSummary(),
      assetService.getAssets(),
    ])

    if (summaryResult.status === 'fulfilled') setSummary(summaryResult.value)
    if (assetsResult.status === 'fulfilled') setAssets(assetsResult.value)

    if (summaryResult.status === 'rejected' || assetsResult.status === 'rejected') {
      setError(summaryResult.status === 'rejected' && assetsResult.status === 'rejected'
        ? 'Your dashboard could not be loaded. Check the server connection and try again.'
        : 'Some dashboard data is temporarily unavailable. The available records are shown below.')
    }
    setIsLoading(false)
  }, [])

  useEffect(() => {
    loadDashboard()
  }, [loadDashboard])

  const derived = useMemo(() => {
    const rejected = assets.filter((asset) => asset.verificationStatus === 'REJECTED').length
    const pendingAssets = assets.filter((asset) => ['PENDING', 'PENDING_REVIEW'].includes(asset.verificationStatus))
    return {
      needsAttention: rejected + pendingAssets.length,
      pendingAssets,
      recentAssets: assets.slice(0, 5),
    }
  }, [assets])

  const verifyingAsset = assets.find((asset) => asset.assetId === verifyingAssetId)

  const handleVerify = async (asset) => {
    setVerifyingAssetId(asset.assetId)
    setActionMessage(null)
    try {
      const result = await assetService.verifyIntegrity(asset.assetId)
      const approved = result.hashMatches
      setActionMessage({
        tone: approved ? 'success' : 'danger',
        text: approved
          ? `${asset.title} matches its original SHA-256 fingerprint.`
          : `${asset.title} no longer matches its original SHA-256 fingerprint.`,
      })
      await loadDashboard({ silent: true })
    } catch (verificationError) {
      setActionMessage({ tone: 'danger', text: verificationError.message || 'Integrity verification could not be completed.' })
    } finally {
      setVerifyingAssetId('')
    }
  }

  const firstName = (currentUser?.fullName || currentUser?.name || currentUser?.username || 'there').split(' ')[0]
  const verificationRate = summary.totalAssets
    ? Math.round((summary.verifiedAssets / summary.totalAssets) * 100)
    : 0
  const metrics = [
    { label: 'Total assets', value: summary.totalAssets, detail: 'Images and PDF documents', icon: FileText, accent: 'bg-sky-50 text-sky-700' },
    { label: 'Verified', value: summary.verifiedAssets, detail: `${verificationRate}% of uploaded assets`, icon: CheckCircle2, accent: 'bg-emerald-50 text-emerald-700' },
    { label: 'Needs attention', value: derived.needsAttention, detail: 'Pending or rejected records', icon: AlertCircle, accent: 'bg-amber-50 text-amber-700' },
    { label: 'Storage used', value: formatFileSize(summary.storageUsed), detail: 'Across your stored files', icon: HardDrive, accent: 'bg-violet-50 text-violet-700' },
  ]

  return (
    <div className="space-y-6 lg:space-y-8">
      <section className="overflow-hidden rounded-3xl bg-[#07111f] px-6 py-7 text-white shadow-xl shadow-slate-900/10 sm:px-8 sm:py-8">
        <div className="relative z-10 flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="mb-3 flex items-center gap-2 text-xs font-bold uppercase tracking-[0.16em] text-teal-300">
              <span className="h-px w-6 bg-teal-300" /> Integrity overview
            </div>
            <h1 className="max-w-2xl text-2xl font-bold tracking-tight sm:text-3xl">Welcome back, {firstName}.</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-400">Track the files you have registered and recheck their stored bytes against the SHA-256 fingerprint captured at upload.</p>
          </div>
          <Link className="inline-flex h-11 w-fit items-center justify-center gap-2 rounded-xl bg-teal-300 px-5 text-sm font-bold text-slate-950 transition hover:bg-teal-200" to="/assets">
            <FileUp size={18} /> Upload a file
          </Link>
        </div>
      </section>

      {error && (
        <div className="flex flex-col gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 sm:flex-row sm:items-center sm:justify-between" role="alert">
          <span>{error}</span>
          <button className="inline-flex shrink-0 items-center gap-2 font-bold" onClick={() => loadDashboard()} type="button"><RefreshCw size={15} /> Try again</button>
        </div>
      )}

      {actionMessage && (
        <div className={`rounded-2xl border px-4 py-3 text-sm ${actionMessage.tone === 'success' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-rose-200 bg-rose-50 text-rose-800'}`} role="status">
          {actionMessage.text}
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {metrics.map(({ label, value, detail, icon: Icon, accent }) => (
          <article className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm shadow-slate-200/30" key={label}>
            <div className={`grid h-10 w-10 place-items-center rounded-xl ${accent}`}><Icon size={19} /></div>
            <p className="mt-5 text-sm font-medium text-slate-500">{label}</p>
            {isLoading ? <div className="mt-2 h-8 w-24 animate-pulse rounded-lg bg-slate-100" /> : <p className="mt-1 text-2xl font-bold tracking-tight text-slate-950">{value}</p>}
            <p className="mt-2 text-xs text-slate-400">{detail}</p>
          </article>
        ))}
      </section>

      <section className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(300px,0.7fr)]">
        <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm shadow-slate-200/30">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-5 sm:px-6">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.15em] text-teal-700">Latest records</p>
              <h2 className="mt-1.5 text-xl font-bold tracking-tight">Recent assets</h2>
            </div>
            <Link className="flex items-center gap-1.5 text-xs font-bold text-slate-600 hover:text-teal-700" to="/assets">View all <ArrowRight size={14} /></Link>
          </div>

          {isLoading && <RecentLoading />}
          {!isLoading && derived.recentAssets.length === 0 && (
            <div className="flex flex-col items-center px-6 py-14 text-center">
              <span className="grid h-14 w-14 place-items-center rounded-2xl bg-teal-50 text-teal-700"><ShieldCheck size={25} /></span>
              <h3 className="mt-4 font-bold">Create your first integrity record</h3>
              <p className="mt-1 max-w-md text-sm leading-6 text-slate-500">Upload a JPEG, PNG, or PDF. AuthVault will validate it, stream it into controlled storage, and record its SHA-256 fingerprint.</p>
              <Link className="mt-5 inline-flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-bold text-white hover:bg-slate-800" to="/assets"><FileUp size={16} /> Upload a file</Link>
            </div>
          )}
          {!isLoading && derived.recentAssets.length > 0 && (
            <div className="divide-y divide-slate-100">
              {derived.recentAssets.map((asset) => (
                <RecentAsset
                  asset={asset}
                  isVerifying={verifyingAssetId === asset.assetId}
                  key={asset.assetId}
                  onVerify={() => handleVerify(asset)}
                />
              ))}
            </div>
          )}
        </div>

        <div className="space-y-6">
          <ProcessMonitor
            isLoading={isLoading}
            pendingAssets={derived.pendingAssets}
            verifyingAsset={verifyingAsset}
          />

          <article className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm shadow-slate-200/30">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.15em] text-teal-700">Coverage</p>
                <h2 className="mt-2 text-xl font-bold">Human verification status</h2>
              </div>
              <span className="text-3xl font-bold tracking-tight text-slate-950">{isLoading ? '—' : `${verificationRate}%`}</span>
            </div>
            <div className="mt-6 h-2.5 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full rounded-full bg-teal-500 transition-all duration-700" style={{ width: `${verificationRate}%` }} />
            </div>
            <p className="mt-4 text-sm leading-6 text-slate-500">A verified status records an authenticator's approval of the captured evidence. SHA-256 matching remains separate integrity evidence.</p>
          </article>

          <article className="rounded-2xl border border-slate-200/80 bg-[#e8f7f3] p-6">
            <p className="text-xs font-bold uppercase tracking-[0.15em] text-teal-800">Supported now</p>
            <h2 className="mt-2 text-lg font-bold text-slate-950">JPEG, PNG, and PDF</h2>
            <p className="mt-2 text-sm leading-6 text-slate-600">Images can be up to 25 MB and PDF documents up to 50 MB. Duplicate file bytes are rejected.</p>
            <Link className="mt-5 inline-flex items-center gap-2 text-sm font-bold text-teal-900 hover:text-teal-700" to="/assets">Open asset workspace <ArrowRight size={16} /></Link>
          </article>
        </div>
      </section>
    </div>
  )
}

function RecentAsset({ asset, isVerifying, onVerify }) {
  const isImage = asset.assetType === 'IMAGE'
  return (
    <article className="flex flex-col gap-4 px-5 py-4 transition hover:bg-slate-50/70 sm:flex-row sm:items-center sm:px-6">
      <div className="flex min-w-0 flex-1 items-center gap-3.5">
        <span className={`grid h-11 w-11 shrink-0 place-items-center rounded-xl ${isImage ? 'bg-sky-50 text-sky-700' : 'bg-violet-50 text-violet-700'}`}>
          {isImage ? <ImageIcon size={20} /> : <FileText size={20} />}
        </span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <Link className="truncate text-sm font-bold text-slate-900 hover:text-teal-700" to={`/assets/${encodeURIComponent(asset.assetId)}`}>{asset.title}</Link>
            <StatusBadge status={asset.verificationStatus} />
          </div>
          <p className="mt-1 truncate text-xs text-slate-500">{asset.originalFilename}</p>
          <p className="mt-1 text-[11px] text-slate-400">{formatFileSize(asset.fileSize)} · {formatDate(asset.uploadDate)}</p>
        </div>
      </div>
      <button
        className="inline-flex shrink-0 items-center justify-center gap-2 rounded-xl border border-slate-200 px-3.5 py-2 text-xs font-bold text-slate-700 transition hover:border-teal-300 hover:bg-teal-50 hover:text-teal-800 disabled:opacity-60"
        disabled={isVerifying}
        onClick={onVerify}
        type="button"
      >
        {isVerifying ? <LoaderCircle className="animate-spin" size={15} /> : <ShieldCheck size={15} />}
        {isVerifying ? 'Checking…' : 'Verify now'}
      </button>
    </article>
  )
}

function StatusBadge({ status }) {
  const classes = status === 'VERIFIED'
    ? 'bg-emerald-50 text-emerald-700'
    : status === 'REJECTED'
      ? 'bg-rose-50 text-rose-700'
      : 'bg-amber-50 text-amber-700'
  const label = status === 'PENDING_REVIEW' ? 'PENDING REVIEW' : status || 'PENDING'
  return <span className={`rounded-full px-2 py-0.5 text-[9px] font-bold tracking-wide ${classes}`}>{label}</span>
}

function ProcessMonitor({ isLoading, pendingAssets, verifyingAsset }) {
  const visiblePending = pendingAssets
    .filter((asset) => asset.assetId !== verifyingAsset?.assetId)
    .slice(0, 2)
  const processCount = (verifyingAsset ? 1 : 0) + visiblePending.length

  return (
    <article className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm shadow-slate-200/30" aria-live="polite">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.15em] text-teal-700">Live activity</p>
          <h2 className="mt-2 text-xl font-bold">Ongoing processes</h2>
        </div>
        <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${processCount > 0 ? 'bg-sky-50 text-sky-700' : 'bg-slate-100 text-slate-500'}`}>
          {isLoading ? 'Checking…' : `${processCount} active`}
        </span>
      </div>

      {isLoading && <div className="mt-5 h-16 animate-pulse rounded-xl bg-slate-100" />}

      {!isLoading && processCount === 0 && (
        <div className="mt-5 flex items-center gap-3 rounded-xl border border-emerald-100 bg-emerald-50/70 p-4">
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-emerald-100 text-emerald-700"><CheckCircle2 size={18} /></span>
          <div><p className="text-sm font-bold text-emerald-900">All caught up</p><p className="mt-0.5 text-xs leading-5 text-emerald-700">No uploads or integrity checks are currently running.</p></div>
        </div>
      )}

      {!isLoading && processCount > 0 && (
        <div className="mt-5 space-y-3">
          {verifyingAsset && (
            <ProcessItem
              active
              detail="Recalculating and comparing SHA-256"
              title={verifyingAsset.title}
            />
          )}
          {visiblePending.map((asset) => (
            <ProcessItem detail={asset.verificationStatus === 'PENDING_REVIEW' ? 'Waiting for authenticator review' : 'Waiting for evidence generation'} key={asset.assetId} title={asset.title} />
          ))}
        </div>
      )}

      <Link className="mt-5 inline-flex items-center gap-2 text-sm font-bold text-teal-800 hover:text-teal-600" to="/assets">
        Open upload workspace <ArrowRight size={15} />
      </Link>
    </article>
  )
}

function ProcessItem({ active = false, detail, title }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-slate-100 bg-slate-50 p-3.5">
      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-sky-100 text-sky-700">
        {active ? <LoaderCircle className="animate-spin" size={17} /> : <RefreshCw size={17} />}
      </span>
      <div className="min-w-0"><p className="truncate text-sm font-bold text-slate-800">{title}</p><p className="mt-0.5 truncate text-[11px] text-slate-500">{detail}</p></div>
    </div>
  )
}

function RecentLoading() {
  return (
    <div className="divide-y divide-slate-100" aria-label="Loading recent assets">
      {[1, 2, 3].map((item) => (
        <div className="flex animate-pulse items-center gap-4 px-6 py-5" key={item}>
          <div className="h-11 w-11 rounded-xl bg-slate-100" />
          <div className="flex-1"><div className="h-4 w-36 rounded bg-slate-100" /><div className="mt-2 h-3 w-52 max-w-full rounded bg-slate-100" /></div>
          <div className="hidden h-8 w-24 rounded-xl bg-slate-100 sm:block" />
        </div>
      ))}
    </div>
  )
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
  if (!value) return 'Unknown date'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Unknown date'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date)
}

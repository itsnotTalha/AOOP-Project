import {
  AlertCircle,
  CheckCircle2,
  FileText,
  Image as ImageIcon,
  LoaderCircle,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react'

export default function AssetList({
  assets,
  error,
  isLoading,
  onRetry,
  onVerify,
  verificationErrors,
  verificationResults,
  verifyingAssetId,
}) {
  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm shadow-slate-200/30">
      <div className="flex flex-col gap-3 border-b border-slate-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-indigo-600">Asset library</p>
          <h2 className="mt-1.5 text-xl font-bold tracking-tight">Your uploaded assets</h2>
          <p className="mt-1 text-sm text-slate-500">Newest assets appear first.</p>
        </div>
        {!isLoading && !error && (
          <span className="w-fit rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-600">
            {assets.length} {assets.length === 1 ? 'asset' : 'assets'}
          </span>
        )}
      </div>

      {isLoading && <LoadingState />}

      {!isLoading && error && (
        <div className="flex flex-col items-center px-6 py-14 text-center">
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-rose-50 text-rose-600"><AlertCircle size={23} /></span>
          <h3 className="mt-4 font-bold">Unable to load assets</h3>
          <p className="mt-1 max-w-md text-sm text-slate-500">{error}</p>
          <button className="mt-5 inline-flex items-center gap-2 rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" onClick={onRetry} type="button">
            <RefreshCw size={16} /> Try again
          </button>
        </div>
      )}

      {!isLoading && !error && assets.length === 0 && (
        <div className="flex flex-col items-center px-6 py-16 text-center">
          <span className="grid h-14 w-14 place-items-center rounded-2xl bg-indigo-50 text-indigo-600"><ShieldCheck size={25} /></span>
          <h3 className="mt-4 font-bold">No assets yet</h3>
          <p className="mt-1 max-w-md text-sm text-slate-500">Upload your first image or PDF to create its SHA-256 integrity record.</p>
        </div>
      )}

      {!isLoading && !error && assets.length > 0 && (
        <div className="divide-y divide-slate-100">
          {assets.map((asset) => (
            <AssetRow
              asset={asset}
              error={verificationErrors[asset.assetId]}
              isVerifying={verifyingAssetId === asset.assetId}
              key={asset.assetId}
              onVerify={() => onVerify(asset.assetId)}
              verification={verificationResults[asset.assetId]}
            />
          ))}
        </div>
      )}
    </section>
  )
}

function AssetRow({ asset, error, isVerifying, onVerify, verification }) {
  const isImage = asset.assetType === 'IMAGE'

  return (
    <article className="p-4 transition hover:bg-slate-50/70 sm:p-5">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-center">
        <div className="flex min-w-0 flex-1 items-start gap-3.5">
          <span className={`grid h-11 w-11 shrink-0 place-items-center rounded-xl ${isImage ? 'bg-amber-50 text-amber-600' : 'bg-violet-50 text-violet-600'}`}>
            {isImage ? <ImageIcon size={20} /> : <FileText size={20} />}
          </span>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="truncate text-sm font-bold text-slate-900">{asset.title}</h3>
              <StatusBadge status={asset.verificationStatus} />
            </div>
            <p className="mt-1 truncate text-xs text-slate-500">{asset.originalFilename}</p>
            <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[11px] font-medium text-slate-400">
              <span>{isImage ? 'Image' : 'Document'}</span>
              <span>{formatFileSize(asset.fileSize)}</span>
              <span>{formatDate(asset.uploadDate)}</span>
            </div>
          </div>
        </div>

        <button
          className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-indigo-200 bg-white px-4 py-2.5 text-sm font-semibold text-indigo-700 transition hover:border-indigo-300 hover:bg-indigo-50 disabled:cursor-not-allowed disabled:opacity-60 xl:w-auto"
          disabled={isVerifying}
          onClick={onVerify}
          type="button"
        >
          {isVerifying ? <LoaderCircle className="animate-spin" size={17} /> : <ShieldCheck size={17} />}
          {isVerifying ? 'Verifying…' : 'Verify integrity'}
        </button>
      </div>

      {error && (
        <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">{error}</div>
      )}

      {verification && <VerificationResult verification={verification} />}
    </article>
  )
}

function VerificationResult({ verification }) {
  const approved = verification.hashMatches && verification.verificationStatus === 'VERIFIED'

  return (
    <div className={`mt-4 rounded-xl border p-4 ${approved ? 'border-emerald-200 bg-emerald-50/70' : 'border-rose-200 bg-rose-50/70'}`}>
      <div className="flex items-center gap-2">
        {approved ? <CheckCircle2 className="text-emerald-600" size={18} /> : <AlertCircle className="text-rose-600" size={18} />}
        <p className={`text-sm font-bold ${approved ? 'text-emerald-800' : 'text-rose-800'}`}>
          {approved ? 'VERIFIED / Approved' : 'REJECTED'}
        </p>
      </div>
      <div className="mt-3 grid gap-3 lg:grid-cols-2">
        <HashValue label="Original SHA-256" value={verification.originalHash} />
        <HashValue label="Current SHA-256" value={verification.currentHash} empty="Unavailable — the stored file could not be read." />
      </div>
    </div>
  )
}

function HashValue({ empty = 'Unavailable', label, value }) {
  return (
    <div>
      <p className="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">{label}</p>
      <p className="mt-1 break-all font-mono text-[11px] leading-5 text-slate-700">{value || empty}</p>
    </div>
  )
}

function StatusBadge({ status }) {
  const verified = status === 'VERIFIED'
  const rejected = status === 'REJECTED'
  const classes = verified
    ? 'bg-emerald-50 text-emerald-700 ring-emerald-600/10'
    : rejected
      ? 'bg-rose-50 text-rose-700 ring-rose-600/10'
      : 'bg-amber-50 text-amber-700 ring-amber-600/10'

  return <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ring-1 ring-inset ${classes}`}>{status || 'PENDING'}</span>
}

function LoadingState() {
  return (
    <div className="divide-y divide-slate-100" aria-label="Loading assets">
      {[1, 2, 3].map((item) => (
        <div className="flex animate-pulse items-center gap-4 p-5" key={item}>
          <div className="h-11 w-11 rounded-xl bg-slate-100" />
          <div className="flex-1"><div className="h-4 w-40 rounded bg-slate-100" /><div className="mt-2 h-3 w-64 max-w-full rounded bg-slate-100" /></div>
          <div className="hidden h-9 w-32 rounded-xl bg-slate-100 sm:block" />
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
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

import { AlertCircle, Clock3, History, LoaderCircle, RefreshCw } from 'lucide-react'

export default function VerificationHistoryList({ error, history, isLoading, onRetry }) {
  return (
    <section className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm shadow-slate-200/30 sm:p-6">
      <div className="flex items-center gap-2">
        <History className="text-indigo-600" size={19} />
        <h2 className="font-bold text-slate-900">Verification history</h2>
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 py-10 text-sm text-slate-500" role="status">
          <LoaderCircle className="animate-spin text-indigo-600" size={19} /> Loading history…
        </div>
      )}

      {!isLoading && error && (
        <div className="mt-5 rounded-xl border border-rose-200 bg-rose-50 p-4" role="alert">
          <div className="flex gap-2 text-sm text-rose-700"><AlertCircle className="mt-0.5 shrink-0" size={17} /><span>{error}</span></div>
          <button className="mt-3 inline-flex items-center gap-1.5 text-sm font-semibold text-rose-700 hover:text-rose-900" onClick={onRetry} type="button">
            <RefreshCw size={15} /> Try again
          </button>
        </div>
      )}

      {!isLoading && !error && history.length === 0 && (
        <div className="py-10 text-center">
          <Clock3 className="mx-auto text-slate-300" size={27} />
          <p className="mt-3 text-sm font-semibold text-slate-700">No verification history</p>
          <p className="mt-1 text-xs text-slate-500">Verification records will appear here.</p>
        </div>
      )}

      {!isLoading && !error && history.length > 0 && (
        <ol className="mt-5 space-y-3">
          {history.map((item, index) => (
            <li className="rounded-xl border border-slate-200 bg-slate-50/70 p-4" key={`${item.verifiedAt}-${item.verificationMethod}-${index}`}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-mono text-xs font-bold text-slate-700">{item.verificationMethod || 'UNKNOWN'}</p>
                <StatusBadge status={item.result} />
              </div>
              <p className="mt-2 text-xs font-medium text-slate-500">{formatDate(item.verifiedAt)}</p>
              {item.notes && <p className="mt-2 text-sm leading-6 text-slate-600">{item.notes}</p>}
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

function StatusBadge({ status }) {
  const classes = status === 'VERIFIED'
    ? 'bg-emerald-50 text-emerald-700 ring-emerald-600/10'
    : status === 'REJECTED'
      ? 'bg-rose-50 text-rose-700 ring-rose-600/10'
      : 'bg-amber-50 text-amber-700 ring-amber-600/10'

  return <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ring-1 ring-inset ${classes}`}>{status || 'PENDING'}</span>
}

function formatDate(value) {
  if (!value) return 'Unknown date'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Unknown date'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

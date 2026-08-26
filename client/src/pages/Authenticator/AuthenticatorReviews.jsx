import { useEffect, useState } from 'react'
import { CheckCircle2, LoaderCircle, ShieldCheck, XCircle } from 'lucide-react'

import * as verificationService from '../../services/verificationService'

export default function AuthenticatorReviews() {
  const [pending, setPending] = useState([])
  const [selected, setSelected] = useState(null)
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)

  const loadPending = async () => {
    setLoading(true)
    setError('')
    try {
      setPending(await verificationService.getPendingReviews())
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Pending reviews could not be loaded.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadPending() }, [])

  const openReview = async (assetId) => {
    setError('')
    try {
      setSelected(await verificationService.getReview(assetId))
      setReason('')
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Review evidence could not be loaded.')
    }
  }

  const decide = async (decision) => {
    if (decision === 'reject' && !reason.trim()) {
      setError('A rejection reason is required.')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await verificationService[decision](selected.evidence.assetId, reason.trim() || null)
      setSelected(null)
      setReason('')
      await loadPending()
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'The review decision could not be saved.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-6">
      <header>
        <p className="text-xs font-bold uppercase tracking-[0.16em] text-indigo-600">Authenticator workspace</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Verification reviews</h1>
        <p className="mt-2 text-sm text-slate-500">Review deterministic evidence and make the final human decision.</p>
      </header>

      {error && <p className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700" role="alert">{error}</p>}

      <div className="grid gap-6 lg:grid-cols-[minmax(280px,0.7fr)_minmax(0,1.3fr)]">
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="font-bold text-slate-900">Pending assets</h2>
          {loading ? <LoaderCircle className="mt-6 animate-spin text-indigo-600" /> : pending.length === 0 ? (
            <p className="mt-5 text-sm text-slate-500">No assets are waiting for review.</p>
          ) : (
            <div className="mt-4 space-y-2">
              {pending.map((item) => (
                <button key={item.assetId} className="w-full rounded-xl border border-slate-200 p-3 text-left hover:border-indigo-300 hover:bg-indigo-50" onClick={() => openReview(item.assetId)} type="button">
                  <p className="font-semibold text-slate-900">{item.title}</p>
                  <p className="mt-1 text-xs text-slate-500">{item.assetType} · {item.assetId}</p>
                </button>
              ))}
            </div>
          )}
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
          {!selected ? <p className="text-sm text-slate-500">Select an asset to inspect its evidence snapshot.</p> : (
            <div className="space-y-5">
              <div className="flex items-center gap-2"><ShieldCheck className="text-indigo-600" size={20} /><h2 className="font-bold text-slate-900">Evidence summary</h2></div>
              <Evidence label="Asset" value={selected.evidence.assetTitle} />
              <Evidence label="SHA-256" value={selected.evidence.sha256} mono />
              <Evidence label="pHash evidence" value={`${selected.evidence.perceptualHashStatus}${selected.evidence.similarCandidateCount == null ? '' : ` · ${selected.evidence.similarCandidateCount} candidate(s)`}`} />
              <Evidence label="Known-original comparison" value={selected.evidence.comparisonPerformed ? selected.evidence.comparisonStatus : selected.evidence.comparisonReason} />
              <Evidence label="Fabric lookup" value={`${selected.evidence.fabricStatus}${selected.evidence.fabricRegisteredOriginalFound === true ? ' · registered original found' : ''}`} />
              <Evidence label="Evidence fingerprint" value={selected.evidence.evidenceHash} mono />
              <textarea className="min-h-28 w-full rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-indigo-400" maxLength={2000} onChange={(event) => setReason(event.target.value)} placeholder="Review note (required for rejection)" value={reason} />
              <div className="grid gap-3 sm:grid-cols-2">
                <button className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60" disabled={submitting} onClick={() => decide('approve')} type="button"><CheckCircle2 size={17} /> Approve</button>
                <button className="inline-flex items-center justify-center gap-2 rounded-xl bg-rose-600 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60" disabled={submitting} onClick={() => decide('reject')} type="button"><XCircle size={17} /> Reject</button>
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

function Evidence({ label, mono = false, value }) {
  return <div><p className="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">{label}</p><p className={`mt-1 break-all text-sm text-slate-700 ${mono ? 'font-mono text-xs' : 'font-semibold'}`}>{value || 'Not available'}</p></div>
}

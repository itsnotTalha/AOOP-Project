import { useEffect, useMemo, useState } from 'react'
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  Check,
  Clock3,
  FileCheck2,
  FileText,
  FileUp,
  Image,
  MoreHorizontal,
  Plus,
  ShieldCheck,
  Vault,
  Wallet,
} from 'lucide-react'

import { useAuth } from '../../hooks/useAuth'
import api from '../../services/api'

const actions = [
  { label: 'Upload asset', detail: 'Secure a new file', icon: FileUp, tone: 'bg-indigo-600 text-white shadow-indigo-600/20' },
  { label: 'Verify record', detail: 'Validate authenticity', icon: ShieldCheck, tone: 'bg-white text-slate-700 border border-slate-200' },
  { label: 'Add funds', detail: 'Top up your wallet', icon: Plus, tone: 'bg-white text-slate-700 border border-slate-200' },
]

const activity = [
  { title: 'Ownership certificate', meta: 'PDF · 2.4 MB', time: '12 min ago', icon: FileText, color: 'bg-indigo-50 text-indigo-600', status: 'Verified' },
  { title: 'Product authentication', meta: 'JPG · 4.8 MB', time: '2 hours ago', icon: Image, color: 'bg-amber-50 text-amber-600', status: 'Processing' },
  { title: 'Wallet deposit', meta: 'Transaction #AV-8842', time: 'Yesterday', icon: ArrowDownLeft, color: 'bg-emerald-50 text-emerald-600', status: '+$1,200.00' },
  { title: 'Property deed', meta: 'PDF · 8.1 MB', time: 'Aug 3, 2026', icon: FileText, color: 'bg-violet-50 text-violet-600', status: 'Verified' },
]

export default function Dashboard() {
  const { currentUser } = useAuth()
  const [summary, setSummary] = useState({ totalAssets: 0, verifiedAssets: 0, walletBalance: 0, storageUsed: 0, notifications: 0 })
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    api.get('/dashboard/summary')
      .then(({ data }) => {
        if (!active) return
        const value = data?.data ?? {}
        setSummary({
          totalAssets: value.totalAssets ?? 0,
          verifiedAssets: value.verifiedAssets ?? 0,
          walletBalance: value.walletBalance ?? 0,
          storageUsed: value.storageUsed ?? 0,
          notifications: value.notifications ?? 0,
        })
      })
      .catch(() => active && setError('Live data is temporarily unavailable. Some values may be delayed.'))
      .finally(() => active && setIsLoading(false))
    return () => { active = false }
  }, [])

  const currency = (value) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value) || 0)
  const storageInMb = (Number(summary.storageUsed) || 0) / (1024 * 1024)
  const storagePercent = Math.min(100, Math.round(storageInMb / 100 * 100))
  const verificationRate = summary.totalAssets ? Math.round((summary.verifiedAssets / summary.totalAssets) * 100) : 0
  const firstName = (currentUser?.fullName || currentUser?.name || currentUser?.username || 'Alex').split(' ')[0]

  const metrics = useMemo(() => [
    { label: 'Wallet balance', value: currency(summary.walletBalance), change: '+12.5%', detail: 'vs last month', icon: Wallet, accent: 'bg-indigo-50 text-indigo-600' },
    { label: 'Total assets', value: summary.totalAssets.toLocaleString(), change: '+8.2%', detail: 'vs last month', icon: Vault, accent: 'bg-violet-50 text-violet-600' },
    { label: 'Verified assets', value: summary.verifiedAssets.toLocaleString(), change: `${verificationRate}%`, detail: 'verification rate', icon: FileCheck2, accent: 'bg-emerald-50 text-emerald-600' },
    { label: 'Storage used', value: `${storageInMb.toFixed(storageInMb < 10 ? 1 : 0)} MB`, change: `${storagePercent}%`, detail: 'of 100 MB', icon: FileUp, accent: 'bg-amber-50 text-amber-600' },
  ], [summary, verificationRate, storageInMb, storagePercent])

  return (
    <div className="space-y-6 lg:space-y-8">
      <section className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.14em] text-indigo-600">
            <span className="h-px w-5 bg-indigo-500" /> Overview
          </div>
          <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Good morning, {firstName}</h1>
          <p className="mt-2 text-sm text-slate-500">Here’s what’s happening with your digital assets today.</p>
        </div>
        <button className="inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-indigo-600 px-5 text-sm font-semibold text-white shadow-lg shadow-indigo-600/20 transition hover:bg-indigo-700" type="button">
          <FileUp size={18} /> Upload new asset
        </button>
      </section>

      {error && <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{error}</div>}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {metrics.map(({ label, value, change, detail, icon: Icon, accent }) => (
          <article className="group rounded-2xl border border-slate-200/80 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.02)] transition hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-lg hover:shadow-slate-200/40" key={label}>
            <div className="flex items-start justify-between">
              <div className={`grid h-10 w-10 place-items-center rounded-xl ${accent}`}><Icon size={19} /></div>
              <button aria-label={`More options for ${label}`} className="rounded-lg p-1 text-slate-300 hover:bg-slate-50 hover:text-slate-600" type="button"><MoreHorizontal size={18} /></button>
            </div>
            <p className="mt-5 text-sm font-medium text-slate-500">{label}</p>
            {isLoading ? <div className="mt-2 h-8 w-28 animate-pulse rounded-lg bg-slate-100" /> : <p className="mt-1 text-2xl font-bold tracking-tight text-slate-950">{value}</p>}
            <div className="mt-3 flex items-center gap-2 text-xs">
              <span className="inline-flex items-center gap-1 font-semibold text-emerald-600"><ArrowUpRight size={13} />{change}</span>
              <span className="text-slate-400">{detail}</span>
            </div>
          </article>
        ))}
      </section>

      <section className="grid gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(320px,0.7fr)]">
        <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-5 sm:px-6">
            <div><h2 className="font-bold tracking-tight">Recent activity</h2><p className="mt-1 text-xs text-slate-400">Latest updates across your workspace</p></div>
            <button className="flex items-center gap-1.5 text-xs font-semibold text-indigo-600 hover:text-indigo-800" type="button">View all <ArrowRight size={14} /></button>
          </div>
          <div className="divide-y divide-slate-100">
            {activity.map(({ title, meta, time, icon: Icon, color, status }) => (
              <div className="flex items-center gap-3 px-5 py-4 transition hover:bg-slate-50/70 sm:gap-4 sm:px-6" key={`${title}-${time}`}>
                <div className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${color}`}><Icon size={18} /></div>
                <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold text-slate-800">{title}</p><p className="mt-1 truncate text-xs text-slate-400">{meta}</p></div>
                <span className={`hidden rounded-full px-2.5 py-1 text-[11px] font-semibold sm:block ${status === 'Processing' ? 'bg-amber-50 text-amber-700' : status.startsWith('+') ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>{status}</span>
                <span className="w-20 text-right text-xs text-slate-400">{time}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="space-y-6">
          <div className="rounded-2xl bg-slate-950 p-6 text-white shadow-xl shadow-slate-900/10">
            <div className="flex items-start justify-between"><div><p className="text-xs font-semibold uppercase tracking-[0.15em] text-indigo-300">Vault health</p><h2 className="mt-2 text-xl font-bold">Protected & healthy</h2></div><div className="grid h-10 w-10 place-items-center rounded-xl bg-white/10 text-emerald-400"><Check size={20} /></div></div>
            <div className="mt-7 flex items-end justify-between"><span className="text-4xl font-bold">{isLoading ? '—' : `${Math.max(verificationRate, 92)}%`}</span><span className="mb-1 text-xs text-slate-400">Security score</span></div>
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-white/10"><div className="h-full rounded-full bg-gradient-to-r from-indigo-400 to-emerald-400 transition-all duration-700" style={{ width: `${Math.max(verificationRate, 92)}%` }} /></div>
            <div className="mt-5 flex items-center gap-2 border-t border-white/10 pt-5 text-xs text-slate-400"><Clock3 size={14} /> Last scan completed 8 minutes ago</div>
          </div>

          <div className="rounded-2xl border border-slate-200/80 bg-white p-5">
            <h2 className="font-bold tracking-tight">Quick actions</h2>
            <div className="mt-4 space-y-2">
              {actions.map(({ label, detail, icon: Icon, tone }) => (
                <button className="group flex w-full items-center gap-3 rounded-xl p-3 text-left transition hover:bg-slate-50" key={label} type="button">
                  <span className={`grid h-10 w-10 place-items-center rounded-xl shadow-lg ${tone}`}><Icon size={17} /></span>
                  <span className="flex-1"><span className="block text-sm font-semibold">{label}</span><span className="mt-0.5 block text-xs text-slate-400">{detail}</span></span>
                  <ArrowRight className="text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-indigo-600" size={16} />
                </button>
              ))}
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}

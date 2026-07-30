import { useEffect, useState } from 'react'

import { ArrowUpRight, Bell, CreditCard, FileUp, ShieldCheck, Vault, Wallet } from 'lucide-react'

import api from '../../services/api'

const overviewCards = [
  {
    label: 'Wallet',
    value: '$12,480.00',
    detail: 'Available balance',
    icon: Wallet,
  },
  {
    label: 'Assets',
    value: '248',
    detail: 'Total stored items',
    icon: Vault,
  },
  {
    label: 'Verified Assets',
    value: '196',
    detail: 'Passed verification',
    icon: ShieldCheck,
  },
  {
    label: 'Storage',
    value: '72 GB',
    detail: 'Used of 100 GB',
    icon: FileUp,
  },
]

const quickActions = [
  {
    label: 'Upload Asset',
    description: 'Add a new document, image, or record to your vault.',
    icon: FileUp,
  },
  {
    label: 'Verify Asset',
    description: 'Check integrity and validation status for a stored asset.',
    icon: ShieldCheck,
  },
  {
    label: 'Wallet',
    description: 'Review your balance and recent wallet activity.',
    icon: Wallet,
  },
  {
    label: 'Vault',
    description: 'Browse secured files and protected storage areas.',
    icon: Vault,
  },
]

const sidebarItems = ['Overview', 'Assets', 'Wallet', 'Vault', 'Verifications']

export default function Dashboard() {
  const [summary, setSummary] = useState({
    totalAssets: 0,
    verifiedAssets: 0,
    walletBalance: 0,
    storageUsed: 0,
    notifications: 0,
  })
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let isMounted = true

    const loadSummary = async () => {
      try {
        setIsLoading(true)
        setError('')

        const response = await api.get('/dashboard/summary')
        const data = response.data?.data ?? {}

        if (!isMounted) {
          return
        }

        setSummary({
          totalAssets: data.totalAssets ?? 0,
          verifiedAssets: data.verifiedAssets ?? 0,
          walletBalance: data.walletBalance ?? 0,
          storageUsed: data.storageUsed ?? 0,
          notifications: data.notifications ?? 0,
        })
      } catch {
        if (isMounted) {
          setError('Unable to load dashboard summary. Showing placeholder values.')
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    loadSummary()

    return () => {
      isMounted = false
    }
  }, [])

  const formatCurrency = (value) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Number(value) || 0)
  }

  const formatStorage = (value) => {
    const size = Number(value) || 0

    if (size >= 1024 ** 3) {
      return `${(size / 1024 ** 3).toFixed(1)} GB`
    }

    if (size >= 1024 ** 2) {
      return `${(size / 1024 ** 2).toFixed(1)} MB`
    }

    if (size >= 1024) {
      return `${(size / 1024).toFixed(1)} KB`
    }

    return `${size} B`
  }

  const overviewCards = [
    {
      label: 'Wallet',
      value: isLoading ? 'Loading...' : formatCurrency(summary.walletBalance),
      detail: 'Available balance',
      icon: Wallet,
    },
    {
      label: 'Assets',
      value: isLoading ? 'Loading...' : String(summary.totalAssets),
      detail: 'Total stored items',
      icon: Vault,
    },
    {
      label: 'Verified Assets',
      value: isLoading ? 'Loading...' : String(summary.verifiedAssets),
      detail: 'Passed verification',
      icon: ShieldCheck,
    },
    {
      label: 'Storage',
      value: isLoading ? 'Loading...' : formatStorage(summary.storageUsed),
      detail: 'Used storage',
      icon: FileUp,
    },
  ]

  const notificationCount = isLoading ? 'Loading...' : `${summary.notifications} unread updates`

  return (
    <section className="space-y-6">
      <div className="grid gap-6 lg:grid-cols-[240px_minmax(0,1fr)]">
        <aside className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center gap-3 border-b border-slate-200 pb-5">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-950 text-white">
              <Vault size={20} />
            </div>
            <div>
              <p className="text-xs uppercase tracking-[0.2em] text-slate-500">AuthVault</p>
              <h2 className="text-lg font-semibold text-slate-950">Control Panel</h2>
            </div>
          </div>

          <nav className="mt-5 space-y-2">
            {sidebarItems.map((item, index) => (
              <a
                key={item}
                className={`flex items-center justify-between rounded-2xl px-4 py-3 text-sm transition-colors ${
                  index === 0
                    ? 'bg-slate-950 text-white'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'
                }`}
                href="#"
              >
                <span>{item}</span>
                {index === 0 && <ArrowUpRight size={16} />}
              </a>
            ))}
          </nav>

          <div className="mt-6 rounded-2xl bg-slate-950 p-4 text-white">
            <p className="text-sm font-medium">Storage health</p>
            <p className="mt-2 text-2xl font-semibold">72%</p>
            <div className="mt-4 h-2 rounded-full bg-white/15">
              <div className="h-2 w-[72%] rounded-full bg-emerald-400" />
            </div>
            <p className="mt-3 text-xs text-slate-300">Plenty of room remains for new uploads.</p>
          </div>
        </aside>

        <div className="space-y-6">
          <header className="rounded-3xl border border-slate-200 bg-white px-6 py-5 shadow-sm">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <p className="text-sm font-medium text-indigo-600">Dashboard</p>
                <h1 className="mt-1 text-3xl font-semibold tracking-tight text-slate-950">
                  Welcome back, Alex
                </h1>
                <p className="mt-2 text-sm text-slate-500">
                  Here’s a quick snapshot of your vault, wallet, and verification activity.
                </p>
              </div>

              <div className="flex items-center gap-3 self-start rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 md:self-auto">
                <Bell size={18} className="text-slate-500" />
                <div>
                  <p className="text-xs uppercase tracking-[0.18em] text-slate-500">Notifications</p>
                  <p className="text-sm font-medium text-slate-950">{notificationCount}</p>
                </div>
              </div>
            </div>

            {error && (
              <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                {error}
              </div>
            )}
          </header>

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {overviewCards.map((card) => {
              const Icon = card.icon

              return (
                <article
                  key={card.label}
                  className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm transition-transform hover:-translate-y-0.5"
                >
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="text-sm font-medium text-slate-500">{card.label}</p>
                      <p className="mt-3 text-3xl font-semibold tracking-tight text-slate-950">{card.value}</p>
                      <p className="mt-2 text-sm text-slate-500">{card.detail}</p>
                    </div>

                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-950 text-white">
                      <Icon size={20} />
                    </div>
                  </div>
                </article>
              )
            })}
          </div>

          <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-sm font-medium text-indigo-600">Quick actions</p>
                <h2 className="mt-1 text-xl font-semibold text-slate-950">Start with the most common tasks</h2>
              </div>
              <div className="hidden items-center gap-2 rounded-full bg-slate-100 px-4 py-2 text-sm text-slate-600 md:flex">
                <CreditCard size={16} />
                  Demo mode
              </div>
            </div>

            <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {quickActions.map((action) => {
                const Icon = action.icon

                return (
                  <button
                    key={action.label}
                    className="group rounded-3xl border border-slate-200 bg-slate-50 p-5 text-left transition-all hover:-translate-y-0.5 hover:border-slate-300 hover:bg-white"
                    type="button"
                  >
                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white text-slate-950 shadow-sm transition-colors group-hover:bg-slate-950 group-hover:text-white">
                      <Icon size={20} />
                    </div>
                    <h3 className="mt-5 text-lg font-semibold text-slate-950">{action.label}</h3>
                    <p className="mt-2 text-sm leading-6 text-slate-500">{action.description}</p>
                  </button>
                )
              })}
            </div>
          </div>

          <div className="grid gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,0.7fr)]">
            <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
              <p className="text-sm font-medium text-indigo-600">Activity</p>
              <h2 className="mt-1 text-xl font-semibold text-slate-950">Recent overview</h2>
              <div className="mt-6 space-y-4">
                {[
                  'Wallet balance is steady and ready for transfers.',
                  'Verified assets continue to outpace pending items.',
                  'Storage usage remains within healthy limits.',
                ].map((item) => (
                  <div key={item} className="flex items-start gap-3 rounded-2xl bg-slate-50 px-4 py-4">
                    <span className="mt-1 h-2.5 w-2.5 rounded-full bg-emerald-500" />
                    <p className="text-sm leading-6 text-slate-600">{item}</p>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-3xl border border-slate-200 bg-slate-950 p-6 text-white shadow-sm">
              <p className="text-sm font-medium text-slate-300">Workspace status</p>
              <h2 className="mt-1 text-xl font-semibold">Everything is within expected limits</h2>
              <div className="mt-6 space-y-4">
                <div>
                  <div className="mb-2 flex items-center justify-between text-sm text-slate-300">
                    <span>Wallet</span>
                    <span>{isLoading ? '...' : `${Math.min(100, Math.round((Number(summary.walletBalance) || 0) / 150000 * 100))}%`}</span>
                  </div>
                  <div className="h-2 rounded-full bg-white/10">
                    <div
                      className="h-2 rounded-full bg-indigo-400"
                      style={{ width: isLoading ? '82%' : `${Math.min(100, Math.round((Number(summary.walletBalance) || 0) / 150000 * 100))}%` }}
                    />
                  </div>
                </div>
                <div>
                  <div className="mb-2 flex items-center justify-between text-sm text-slate-300">
                    <span>Assets</span>
                    <span>{isLoading ? '...' : `${Math.min(100, summary.totalAssets || 0)}%`}</span>
                  </div>
                  <div className="h-2 rounded-full bg-white/10">
                    <div
                      className="h-2 rounded-full bg-emerald-400"
                      style={{ width: isLoading ? '64%' : `${Math.min(100, summary.totalAssets || 0)}%` }}
                    />
                  </div>
                </div>
                <div>
                  <div className="mb-2 flex items-center justify-between text-sm text-slate-300">
                    <span>Storage</span>
                    <span>{isLoading ? '...' : `${Math.min(100, Math.round((Number(summary.storageUsed) || 0) / (100 * 1024 * 1024) * 100))}%`}</span>
                  </div>
                  <div className="h-2 rounded-full bg-white/10">
                    <div
                      className="h-2 rounded-full bg-amber-400"
                      style={{ width: isLoading ? '72%' : `${Math.min(100, Math.round((Number(summary.storageUsed) || 0) / (100 * 1024 * 1024) * 100))}%` }}
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

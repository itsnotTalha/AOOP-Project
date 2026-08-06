import { useState } from 'react'
import {
  Bell,
  ChevronDown,
  FileCheck2,
  Files,
  LayoutDashboard,
  LogOut,
  Menu,
  Search,
  ShieldCheck,
  Vault,
  Wallet,
  X,
} from 'lucide-react'
import { Link, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from '../hooks/useAuth'

const navigation = [
  { label: 'Overview', icon: LayoutDashboard, href: '/dashboard' },
  { label: 'Assets', icon: Files, href: '#' },
  { label: 'Verifications', icon: FileCheck2, href: '#' },
  { label: 'Vault', icon: Vault, href: '#' },
  { label: 'Wallet', icon: Wallet, href: '#' },
]

export default function DashboardLayout() {
  const { currentUser, logout } = useAuth()
  const location = useLocation()
  const [isMenuOpen, setIsMenuOpen] = useState(false)

  const displayName = currentUser?.fullName || currentUser?.name || currentUser?.username || 'Alex Morgan'
  const email = currentUser?.email || 'alex@authvault.io'
  const initials = displayName
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  return (
    <div className="min-h-screen bg-[#f7f8fa] text-slate-950">
      {isMenuOpen && (
        <button
          aria-label="Close navigation"
          className="fixed inset-0 z-40 bg-slate-950/30 backdrop-blur-sm lg:hidden"
          onClick={() => setIsMenuOpen(false)}
          type="button"
        />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-50 flex w-[272px] flex-col border-r border-slate-200/80 bg-white transition-transform duration-300 lg:translate-x-0 ${
          isMenuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-20 items-center justify-between px-6">
          <Link className="flex items-center gap-3" to="/dashboard">
            <span className="grid h-10 w-10 place-items-center rounded-xl bg-indigo-600 text-white shadow-lg shadow-indigo-600/20">
              <ShieldCheck size={21} strokeWidth={2.2} />
            </span>
            <span>
              <span className="block text-base font-bold tracking-tight">AuthVault</span>
              <span className="block text-[10px] font-semibold uppercase tracking-[0.2em] text-slate-400">Digital custody</span>
            </span>
          </Link>
          <button className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 lg:hidden" onClick={() => setIsMenuOpen(false)} type="button">
            <X size={19} />
          </button>
        </div>

        <nav className="flex-1 px-4 py-5">
          <p className="mb-3 px-3 text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">Workspace</p>
          <div className="space-y-1">
            {navigation.map(({ label, icon: Icon, href }) => {
              const active = href === '/dashboard' && location.pathname === '/dashboard'
              return (
                <Link
                  key={label}
                  className={`group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                    active ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-950'
                  }`}
                  onClick={() => setIsMenuOpen(false)}
                  to={href}
                >
                  <Icon className={active ? 'text-indigo-600' : 'text-slate-400 group-hover:text-slate-600'} size={19} />
                  {label}
                  {active && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-indigo-600" />}
                </Link>
              )
            })}
          </div>

          <div className="mx-3 my-6 h-px bg-slate-100" />
          <p className="mb-3 px-3 text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">Security</p>
          <div className="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4">
            <div className="flex items-center gap-2 text-sm font-semibold text-emerald-800">
              <span className="relative flex h-2.5 w-2.5">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-50" />
                <span className="relative h-2.5 w-2.5 rounded-full bg-emerald-500" />
              </span>
              All systems secure
            </div>
            <p className="mt-2 text-xs leading-5 text-emerald-700/80">Encryption and monitoring are active.</p>
          </div>
        </nav>

        <div className="border-t border-slate-100 p-4">
          <div className="flex items-center gap-3 rounded-xl p-2">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-slate-900 text-xs font-bold text-white">{initials || 'AM'}</span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold">{displayName}</p>
              <p className="truncate text-xs text-slate-400">{email}</p>
            </div>
            <button aria-label="Log out" className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" onClick={logout} type="button">
              <LogOut size={17} />
            </button>
          </div>
        </div>
      </aside>

      <div className="lg:pl-[272px]">
        <header className="sticky top-0 z-30 flex h-20 items-center justify-between border-b border-slate-200/70 bg-white/90 px-4 backdrop-blur-xl sm:px-6 lg:px-8">
          <div className="flex items-center gap-3">
            <button className="rounded-xl border border-slate-200 p-2.5 text-slate-600 lg:hidden" onClick={() => setIsMenuOpen(true)} type="button">
              <Menu size={20} />
            </button>
            <div className="relative hidden md:block">
              <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" size={17} />
              <input className="h-10 w-72 rounded-xl border border-slate-200 bg-slate-50/70 pl-10 pr-4 text-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-300 focus:bg-white focus:ring-4 focus:ring-indigo-100" placeholder="Search assets, IDs, records..." />
            </div>
          </div>
          <div className="flex items-center gap-2 sm:gap-3">
            <button aria-label="Notifications" className="relative rounded-xl border border-slate-200 p-2.5 text-slate-500 transition hover:bg-slate-50 hover:text-slate-800" type="button">
              <Bell size={19} />
              <span className="absolute right-2 top-2 h-2 w-2 rounded-full border-2 border-white bg-rose-500" />
            </button>
            <button className="hidden items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 sm:flex" type="button">
              <span className="grid h-6 w-6 place-items-center rounded-full bg-indigo-100 text-[10px] font-bold text-indigo-700">{initials || 'AM'}</span>
              {displayName.split(' ')[0]}
              <ChevronDown size={14} className="text-slate-400" />
            </button>
          </div>
        </header>
        <main className="mx-auto max-w-[1600px] p-4 sm:p-6 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

import { useEffect, useState } from 'react'
import {
  Files,
  LayoutDashboard,
  LogOut,
  Menu,
  Search,
  ShieldCheck,
  X,
} from 'lucide-react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '../hooks/useAuth'

const navigation = [
  { label: 'Overview', icon: LayoutDashboard, href: '/dashboard' },
  { label: 'Assets', icon: Files, href: '/assets' },
]

export default function DashboardLayout() {
  const { currentUser, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [search, setSearch] = useState('')

  const displayName = currentUser?.fullName || currentUser?.name || currentUser?.username || 'Account owner'
  const email = currentUser?.email || currentUser?.username || ''
  const initials = displayName
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  useEffect(() => {
    if (!location.pathname.startsWith('/assets')) setSearch('')
  }, [location.pathname])

  const submitSearch = (event) => {
    event.preventDefault()
    const value = search.trim()
    navigate(value ? `/assets?search=${encodeURIComponent(value)}` : '/assets')
  }

  return (
    <div className="min-h-screen bg-[#f4f7f6] text-slate-950">
      {isMenuOpen && (
        <button
          aria-label="Close navigation"
          className="fixed inset-0 z-40 bg-slate-950/35 backdrop-blur-sm lg:hidden"
          onClick={() => setIsMenuOpen(false)}
          type="button"
        />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-50 flex w-[272px] flex-col border-r border-slate-200/80 bg-[#07111f] text-white transition-transform duration-300 lg:translate-x-0 ${
          isMenuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-20 items-center justify-between px-6">
          <Link className="flex items-center gap-3" to="/dashboard">
            <span className="grid h-10 w-10 place-items-center rounded-xl bg-teal-400 text-slate-950 shadow-lg shadow-teal-400/15">
              <ShieldCheck size={21} strokeWidth={2.3} />
            </span>
            <span>
              <span className="block text-base font-bold tracking-tight">AuthVault</span>
              <span className="block text-[10px] font-semibold uppercase tracking-[0.2em] text-slate-400">File integrity</span>
            </span>
          </Link>
          <button className="rounded-lg p-2 text-slate-400 hover:bg-white/10 hover:text-white lg:hidden" onClick={() => setIsMenuOpen(false)} type="button">
            <X size={19} />
          </button>
        </div>

        <nav className="flex-1 px-4 py-5">
          <p className="mb-3 px-3 text-[11px] font-bold uppercase tracking-[0.16em] text-slate-500">Workspace</p>
          <div className="space-y-1">
            {navigation.map(({ label, icon: Icon, href }) => {
              const active = location.pathname === href
                || (href !== '/dashboard' && location.pathname.startsWith(`${href}/`))
              return (
                <Link
                  key={label}
                  className={`group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                    active ? 'bg-white/10 text-white' : 'text-slate-400 hover:bg-white/[0.06] hover:text-white'
                  }`}
                  onClick={() => setIsMenuOpen(false)}
                  to={href}
                >
                  <Icon className={active ? 'text-teal-300' : 'text-slate-500 group-hover:text-slate-300'} size={19} />
                  {label}
                  {active && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-teal-300" />}
                </Link>
              )
            })}
          </div>

          <div className="mx-3 my-6 h-px bg-white/10" />
          <div className="rounded-2xl border border-teal-300/15 bg-teal-300/[0.07] p-4">
            <div className="flex items-center gap-2 text-sm font-semibold text-teal-200">
              <ShieldCheck size={16} /> Owner-only workspace
            </div>
            <p className="mt-2 text-xs leading-5 text-slate-400">Asset requests are authorized with your signed session. Only your files are returned.</p>
          </div>
        </nav>

        <div className="border-t border-white/10 p-4">
          <div className="flex items-center gap-3 rounded-xl p-2">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-teal-300 text-xs font-bold text-slate-950">{initials || 'AV'}</span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-white">{displayName}</p>
              {email && <p className="truncate text-xs text-slate-500">{email}</p>}
            </div>
            <button aria-label="Log out" className="rounded-lg p-2 text-slate-500 transition hover:bg-white/10 hover:text-white" onClick={logout} type="button">
              <LogOut size={17} />
            </button>
          </div>
        </div>
      </aside>

      <div className="lg:pl-[272px]">
        <header className="sticky top-0 z-30 flex h-20 items-center justify-between border-b border-slate-200/70 bg-white/90 px-4 backdrop-blur-xl sm:px-6 lg:px-8">
          <div className="flex items-center gap-3">
            <button aria-label="Open navigation" className="rounded-xl border border-slate-200 p-2.5 text-slate-600 lg:hidden" onClick={() => setIsMenuOpen(true)} type="button">
              <Menu size={20} />
            </button>
            <form className="relative hidden md:block" onSubmit={submitSearch} role="search">
              <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" size={17} />
              <input
                aria-label="Search assets"
                className="h-10 w-72 rounded-xl border border-slate-200 bg-slate-50/70 pl-10 pr-4 text-sm outline-none transition placeholder:text-slate-400 focus:border-teal-400 focus:bg-white focus:ring-4 focus:ring-teal-100"
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search your assets…"
                value={search}
              />
            </form>
          </div>
          <div className="flex items-center gap-3">
            <span className="hidden text-right sm:block">
              <span className="block text-xs font-semibold text-slate-900">{displayName}</span>
              <span className="mt-0.5 block text-[11px] text-slate-400">Authenticated owner</span>
            </span>
            <span className="grid h-9 w-9 place-items-center rounded-full bg-slate-900 text-[11px] font-bold text-white">{initials || 'AV'}</span>
          </div>
        </header>
        <main className="mx-auto max-w-[1500px] p-4 sm:p-6 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

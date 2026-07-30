import { LayoutDashboard, LogOut } from 'lucide-react'
import { Link, Outlet } from 'react-router-dom'

import { useAuth } from '../hooks/useAuth'

export default function DashboardLayout() {
  const { logout } = useAuth()

  return (
    <div className="flex min-h-screen bg-slate-50">
      <aside className="w-60 border-r border-slate-200 bg-white p-4">
        <Link className="flex items-center gap-2 font-semibold text-slate-900" to="/dashboard">
          <LayoutDashboard size={18} />
          AuthVault
        </Link>
        <nav className="mt-8 text-sm text-slate-600">Sidebar placeholder</nav>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6">
          <span className="text-sm text-slate-600">Navbar placeholder</span>
          <button className="flex items-center gap-2 text-sm text-slate-600" onClick={logout} type="button">
            <LogOut size={16} />
            Logout
          </button>
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

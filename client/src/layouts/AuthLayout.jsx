import { Link, Outlet } from 'react-router-dom'

export default function AuthLayout() {
  return (
    <main className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white px-6 py-4">
        <Link className="font-semibold text-slate-900" to="/">
          VeriVault
        </Link>
      </header>
      <Outlet />
    </main>
  )
}

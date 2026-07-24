import { Outlet } from 'react-router-dom'

export default function MainLayout() {
  return (
    <main className="min-h-screen bg-slate-50">
      <Outlet />
    </main>
  )
}

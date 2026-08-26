import { Navigate, Outlet } from 'react-router-dom'

import { useAuth } from '../hooks/useAuth'

export default function RoleProtectedRoute({ roles }) {
  const { currentUser, isProfileLoading } = useAuth()

  if (isProfileLoading) {
    return <div className="p-8 text-sm text-slate-500">Loading authorized workspace…</div>
  }

  if (!roles.includes(currentUser?.role)) {
    return <Navigate replace to="/dashboard" />
  }

  return <Outlet />
}

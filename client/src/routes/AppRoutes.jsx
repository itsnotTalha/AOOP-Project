import { Route, Routes } from 'react-router-dom'

import AuthLayout from '../layouts/AuthLayout'
import DashboardLayout from '../layouts/DashboardLayout'
import MainLayout from '../layouts/MainLayout'
import Dashboard from '../pages/Dashboard/Dashboard'
import Assets from '../pages/Assets/Assets'
import AssetDetails from '../pages/Assets/AssetDetails'
import Landing from '../pages/Landing/Landing'
import Login from '../pages/Login/Login'
import Register from '../pages/Register/Register'
import ProtectedRoute from './ProtectedRoute'
import RoleProtectedRoute from './RoleProtectedRoute'
import AuthenticatorReviews from '../pages/Authenticator/AuthenticatorReviews'

export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route element={<Landing />} path="/" />
      </Route>

      <Route element={<AuthLayout />}>
        <Route element={<Login />} path="/login" />
        <Route element={<Register />} path="/register" />
      </Route>

      <Route element={<ProtectedRoute />}>
        <Route element={<DashboardLayout />}>
          <Route element={<Dashboard />} path="/dashboard" />
          <Route element={<Assets />} path="/assets" />
          <Route element={<AssetDetails />} path="/assets/:assetId" />
          <Route element={<RoleProtectedRoute roles={['AUTHENTICATOR', 'ADMIN']} />}>
            <Route element={<AuthenticatorReviews />} path="/authenticator/reviews" />
          </Route>
        </Route>
      </Route>
    </Routes>
  )
}

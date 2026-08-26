import { createContext, useCallback, useEffect, useMemo, useState } from 'react'

import { getToken, getUser, removeToken, setToken, setUser } from '../utils/tokenStorage'
import * as authService from '../services/authService'

export const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setAuthToken] = useState(() => getToken())
  const [currentUser, setCurrentUser] = useState(() => getUser())
  const [isProfileLoading, setIsProfileLoading] = useState(() => Boolean(getToken()))

  const login = useCallback((authResponse) => {
    const authToken = authResponse?.token ?? authResponse?.accessToken

    if (!authToken) {
      return
    }

    setToken(authToken)
    setAuthToken(authToken)

    if (authResponse.user) {
      setUser(authResponse.user)
      setCurrentUser(authResponse.user)
    }
    setIsProfileLoading(true)
  }, [])

  const logout = useCallback(() => {
    removeToken()
    setAuthToken(null)
    setCurrentUser(null)
    setIsProfileLoading(false)
  }, [])

  const isAuthenticated = useCallback(() => Boolean(token), [token])

  useEffect(() => {
    const handleUnauthorized = () => logout()
    window.addEventListener('authvault:unauthorized', handleUnauthorized)

    return () => window.removeEventListener('authvault:unauthorized', handleUnauthorized)
  }, [logout])

  useEffect(() => {
    if (!token) {
      setIsProfileLoading(false)
      return undefined
    }

    let active = true
    setIsProfileLoading(true)
    authService.getCurrentUser()
      .then((user) => {
        if (!active) return
        setUser(user)
        setCurrentUser(user)
      })
      .catch(() => {
        if (active) setCurrentUser(null)
      })
      .finally(() => active && setIsProfileLoading(false))

    return () => { active = false }
  }, [token])

  const value = useMemo(
    () => ({ token, login, logout, isAuthenticated, currentUser, isProfileLoading }),
    [token, login, logout, isAuthenticated, currentUser, isProfileLoading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

import { createContext, useCallback, useEffect, useMemo, useState } from 'react'

import { getToken, getUser, removeToken, setToken, setUser } from '../utils/tokenStorage'

export const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setAuthToken] = useState(() => getToken())
  const [currentUser, setCurrentUser] = useState(() => getUser())

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
  }, [])

  const logout = useCallback(() => {
    removeToken()
    setAuthToken(null)
    setCurrentUser(null)
  }, [])

  const isAuthenticated = useCallback(() => Boolean(token), [token])

  useEffect(() => {
    const handleUnauthorized = () => logout()
    window.addEventListener('verivault:unauthorized', handleUnauthorized)

    return () => window.removeEventListener('verivault:unauthorized', handleUnauthorized)
  }, [logout])

  const value = useMemo(
    () => ({ token, login, logout, isAuthenticated, currentUser }),
    [token, login, logout, isAuthenticated, currentUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

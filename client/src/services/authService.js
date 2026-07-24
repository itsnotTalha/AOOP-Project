import api from './api'

export async function login(credentials) {
  const response = await api.post('/auth/login', credentials)
  return response.data.data
}

export async function register(userData) {
  const response = await api.post('/auth/register', userData)
  return response.data.data
}

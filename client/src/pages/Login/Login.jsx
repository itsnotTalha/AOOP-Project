import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import FormField from '../../components/ui/FormField'
import SubmitButton from '../../components/ui/SubmitButton'
import { useAuth } from '../../hooks/useAuth'
import * as authService from '../../services/authService'

function getErrorMessage(error) {
  const response = error.response?.data
  const validationErrors = response?.errors

  if (Array.isArray(validationErrors) && validationErrors.length > 0) {
    return validationErrors.join(' ')
  }

  return response?.message ?? 'Unable to sign in. Please check your credentials.'
}

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()
  const [serverError, setServerError] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    mode: 'onBlur',
  })

  const onSubmit = async (credentials) => {
    setServerError('')

    try {
      const authResponse = await authService.login(credentials)
      login(authResponse)
      navigate(location.state?.from?.pathname ?? '/dashboard', { replace: true })
    } catch (error) {
      setServerError(getErrorMessage(error))
    }
  }

  return (
    <section className="mx-auto flex min-h-[calc(100vh-73px)] max-w-md items-center px-6 py-12">
      <div className="w-full rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <div className="mb-8">
          <p className="text-sm font-semibold text-indigo-600">Welcome back</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">Sign in to VeriVault</h1>
          <p className="mt-2 text-sm text-slate-500">Access your secure verification workspace.</p>
        </div>

        {location.state?.message && (
          <div className="mb-5 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700" role="status">
            {location.state.message}
          </div>
        )}

        {serverError && (
          <div className="mb-5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
            {serverError}
          </div>
        )}

        <form className="space-y-5" onSubmit={handleSubmit(onSubmit)}>
          <FormField
            autoComplete="username"
            error={errors.emailOrUsername}
            id="emailOrUsername"
            label="Email or username"
            placeholder="you@example.com"
            {...register('emailOrUsername', { required: 'Email or username is required.' })}
          />
          <FormField
            autoComplete="current-password"
            error={errors.password}
            id="password"
            label="Password"
            placeholder="Enter your password"
            type="password"
            {...register('password', {
              required: 'Password is required.',
              minLength: { value: 8, message: 'Password must be at least 8 characters.' },
            })}
          />
          <SubmitButton loading={isSubmitting}>Sign in</SubmitButton>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          New to VeriVault?{' '}
          <Link className="font-semibold text-indigo-600 hover:text-indigo-700" to="/register">
            Create an account
          </Link>
        </p>
      </div>
    </section>
  )
}

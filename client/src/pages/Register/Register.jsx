import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'

import FormField from '../../components/ui/FormField'
import SubmitButton from '../../components/ui/SubmitButton'
import * as authService from '../../services/authService'

function getErrorMessage(error) {
  const response = error.response?.data
  const validationErrors = response?.errors

  if (Array.isArray(validationErrors) && validationErrors.length > 0) {
    return validationErrors.join(' ')
  }

  return response?.message ?? 'Unable to create your account. Please try again.'
}

export default function Register() {
  const navigate = useNavigate()
  const [serverError, setServerError] = useState('')
  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm({
    mode: 'onBlur',
  })

  const onSubmit = async (userData) => {
    setServerError('')

    try {
      await authService.register(userData)
      navigate('/login', { replace: true, state: { message: 'Account created. You can now sign in.' } })
    } catch (error) {
      setServerError(getErrorMessage(error))
    }
  }

  return (
    <section className="mx-auto max-w-2xl px-6 py-12">
      <div className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <div className="mb-8">
          <p className="text-sm font-semibold text-indigo-600">Get started</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">Create your account</h1>
          <p className="mt-2 text-sm text-slate-500">Build a trusted digital identity with AuthVault.</p>
        </div>

        {serverError && (
          <div className="mb-5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
            {serverError}
          </div>
        )}

        <form className="grid gap-5 sm:grid-cols-2" onSubmit={handleSubmit(onSubmit)}>
          <FormField
            autoComplete="name"
            error={errors.fullName}
            id="fullName"
            label="Full name"
            placeholder="Alex Morgan"
            {...register('fullName', { required: 'Full name is required.' })}
          />
          <FormField
            autoComplete="username"
            error={errors.username}
            id="username"
            label="Username"
            placeholder="alexmorgan"
            {...register('username', {
              required: 'Username is required.',
              minLength: { value: 3, message: 'Username must be at least 3 characters.' },
            })}
          />
          <FormField
            autoComplete="email"
            error={errors.email}
            id="email"
            label="Email"
            placeholder="you@example.com"
            type="email"
            {...register('email', {
              required: 'Email is required.',
              pattern: { value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: 'Enter a valid email address.' },
            })}
          />
          <FormField
            autoComplete="tel"
            error={errors.phone}
            id="phone"
            label="Phone (optional)"
            placeholder="+1 555 000 0000"
            {...register('phone')}
          />
          <FormField
            autoComplete="new-password"
            error={errors.password}
            id="password"
            label="Password"
            placeholder="At least 8 characters"
            type="password"
            {...register('password', {
              required: 'Password is required.',
              minLength: { value: 8, message: 'Password must be at least 8 characters.' },
            })}
          />
          <FormField
            autoComplete="new-password"
            error={errors.confirmPassword}
            id="confirmPassword"
            label="Confirm password"
            placeholder="Repeat your password"
            type="password"
            {...register('confirmPassword', {
              required: 'Please confirm your password.',
              validate: (value) => value === watch('password') || 'Passwords do not match.',
            })}
          />
          <div className="sm:col-span-2">
            <SubmitButton loading={isSubmitting}>Create account</SubmitButton>
          </div>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          Already have an account?{' '}
          <Link className="font-semibold text-indigo-600 hover:text-indigo-700" to="/login">
            Sign in
          </Link>
        </p>
      </div>
    </section>
  )
}

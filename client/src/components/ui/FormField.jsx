export default function FormField({ label, error, ...inputProps }) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700" htmlFor={inputProps.id}>
        {label}
      </label>
      <input
        {...inputProps}
        aria-invalid={Boolean(error)}
        className={`w-full rounded-xl border bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:ring-4 ${
          error
            ? 'border-rose-300 focus:border-rose-500 focus:ring-rose-100'
            : 'border-slate-200 focus:border-indigo-500 focus:ring-indigo-100'
        }`}
      />
      {error && <p className="mt-1.5 text-xs text-rose-600">{error.message}</p>}
    </div>
  )
}

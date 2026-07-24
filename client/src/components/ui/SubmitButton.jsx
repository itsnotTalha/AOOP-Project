import { LoaderCircle } from 'lucide-react'

export default function SubmitButton({ children, loading }) {
  return (
    <button
      className="flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
      disabled={loading}
      type="submit"
    >
      {loading && <LoaderCircle className="animate-spin" size={17} />}
      {loading ? 'Please wait...' : children}
    </button>
  )
}

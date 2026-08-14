import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Check, CheckCircle2, Clipboard, ShieldCheck } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'

import * as assetService from '../../services/assetService'
import AssetFilters from './AssetFilters'
import AssetList from './AssetList'
import AssetUploadForm from './AssetUploadForm'

export default function Assets() {
  const [searchParams, setSearchParams] = useSearchParams()
  const currentQuery = searchParams.toString()
  const filters = useMemo(
    () => readFilters(new URLSearchParams(currentQuery)),
    [currentQuery],
  )
  const canonicalQuery = buildSearchParams(filters).toString()
  const [searchValue, setSearchValue] = useState(filters.search)
  const [assets, setAssets] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [uploadedAsset, setUploadedAsset] = useState(null)
  const [copiedHash, setCopiedHash] = useState(false)
  const [verifyingAssetId, setVerifyingAssetId] = useState(null)
  const [verificationResults, setVerificationResults] = useState({})
  const [verificationErrors, setVerificationErrors] = useState({})
  const requestSequence = useRef(0)

  const criteria = useMemo(() => ({
    search: filters.search || undefined,
    type: filters.type || undefined,
    status: filters.status || undefined,
    sort: filters.sort === 'oldest' ? 'oldest' : undefined,
  }), [filters.search, filters.sort, filters.status, filters.type])
  const criteriaRef = useRef(criteria)
  criteriaRef.current = criteria

  const hasRestrictiveFilters = Boolean(filters.search || filters.type || filters.status)
  const hasActiveFilters = hasRestrictiveFilters || filters.sort === 'oldest'
  const listQuery = canonicalQuery ? `?${canonicalQuery}` : ''

  const loadAssets = useCallback(async ({ silent = false } = {}) => {
    const requestId = ++requestSequence.current
    if (!silent) setIsLoading(true)
    setListError('')

    try {
      const result = await assetService.getAssets(criteriaRef.current)
      if (requestId === requestSequence.current) setAssets(result)
    } catch (error) {
      if (requestId === requestSequence.current) {
        setListError(error.message || 'Assets could not be loaded. Please try again.')
      }
    } finally {
      if (requestId === requestSequence.current) setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    loadAssets()
  }, [criteria, loadAssets])

  useEffect(() => {
    if (currentQuery !== canonicalQuery) {
      setSearchParams(buildSearchParams(filters), { replace: true })
    }
  }, [canonicalQuery, currentQuery, filters, setSearchParams])

  useEffect(() => {
    setSearchValue(filters.search)
  }, [filters.search])

  useEffect(() => {
    const normalizedSearch = searchValue.trim()
    if (normalizedSearch === filters.search) return undefined

    const timer = window.setTimeout(() => {
      setSearchParams(buildSearchParams({ ...filters, search: normalizedSearch }))
    }, 350)

    return () => window.clearTimeout(timer)
  }, [filters, searchValue, setSearchParams])

  const handleFilterChange = (name, value) => {
    setSearchParams(buildSearchParams({
      ...filters,
      search: searchValue.trim(),
      [name]: value,
    }))
  }

  const clearFilters = () => {
    setSearchValue('')
    setSearchParams(new URLSearchParams())
  }

  const handleUploaded = (asset) => {
    setUploadedAsset(asset)
    setCopiedHash(false)
    loadAssets({ silent: true })
  }

  const handleVerify = async (assetId) => {
    setVerifyingAssetId(assetId)
    setVerificationErrors((current) => ({ ...current, [assetId]: '' }))
    setVerificationResults((current) => ({ ...current, [assetId]: null }))

    try {
      const result = await assetService.verifyIntegrity(assetId)
      setVerificationResults((current) => ({ ...current, [assetId]: result }))
      await loadAssets({ silent: true })
    } catch (error) {
      setVerificationErrors((current) => ({
        ...current,
        [assetId]: error.message || 'Integrity verification could not be completed.',
      }))
    } finally {
      setVerifyingAssetId(null)
    }
  }

  const copyHash = async () => {
    if (!uploadedAsset?.sha256Hash) return

    try {
      await navigator.clipboard.writeText(uploadedAsset.sha256Hash)
      setCopiedHash(true)
      window.setTimeout(() => setCopiedHash(false), 1800)
    } catch {
      setCopiedHash(false)
    }
  }

  return (
    <div className="space-y-6 lg:space-y-8">
      <section className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.14em] text-indigo-600">
            <span className="h-px w-5 bg-indigo-500" /> Integrity workspace
          </div>
          <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Assets</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
            Upload supported files, preserve their SHA-256 fingerprint, and verify stored bytes whenever needed.
          </p>
        </div>
        <div className="flex w-fit items-center gap-2 rounded-xl border border-emerald-100 bg-emerald-50 px-3.5 py-2 text-xs font-semibold text-emerald-700">
          <ShieldCheck size={16} /> Owner-only access
        </div>
      </section>

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(360px,0.72fr)_minmax(0,1.28fr)]">
        <div className="space-y-6">
          <AssetUploadForm onUploaded={handleUploaded} />
          {uploadedAsset && (
            <UploadSuccessCard
              asset={uploadedAsset}
              copied={copiedHash}
              onCopy={copyHash}
            />
          )}
        </div>

        <div className="space-y-4">
          <AssetFilters
            filters={filters}
            hasActiveFilters={hasActiveFilters}
            onChange={handleFilterChange}
            onClear={clearFilters}
            onSearchChange={setSearchValue}
            searchValue={searchValue}
          />
          <AssetList
            assets={assets}
            error={listError}
            isFiltered={hasRestrictiveFilters}
            isLoading={isLoading}
            listQuery={listQuery}
            onClearFilters={clearFilters}
            onRetry={() => loadAssets()}
            onVerify={handleVerify}
            sort={filters.sort}
            verificationErrors={verificationErrors}
            verificationResults={verificationResults}
            verifyingAssetId={verifyingAssetId}
          />
        </div>
      </div>
    </div>
  )
}

const VALID_TYPES = new Set(['IMAGE', 'DOCUMENT'])
const VALID_STATUSES = new Set(['PENDING', 'VERIFIED', 'REJECTED'])

function readFilters(searchParams) {
  const search = (searchParams.get('search') || '').trim()
  const typeValue = searchParams.get('type') || ''
  const statusValue = searchParams.get('status') || ''
  const sortValue = searchParams.get('sort') || 'newest'

  return {
    search,
    type: VALID_TYPES.has(typeValue) ? typeValue : '',
    status: VALID_STATUSES.has(statusValue) ? statusValue : '',
    sort: sortValue === 'oldest' ? 'oldest' : 'newest',
  }
}

function buildSearchParams({ search, type, status, sort }) {
  const params = new URLSearchParams()
  const normalizedSearch = typeof search === 'string' ? search.trim() : ''

  if (normalizedSearch) params.set('search', normalizedSearch)
  if (VALID_TYPES.has(type)) params.set('type', type)
  if (VALID_STATUSES.has(status)) params.set('status', status)
  if (sort === 'oldest') params.set('sort', 'oldest')

  return params
}

function UploadSuccessCard({ asset, copied, onCopy }) {
  return (
    <section className="overflow-hidden rounded-2xl border border-emerald-200 bg-white shadow-sm shadow-emerald-100/50" aria-live="polite">
      <div className="bg-emerald-50 px-5 py-5 sm:px-6">
        <div className="flex items-center gap-3">
          <span className="grid h-11 w-11 place-items-center rounded-xl bg-emerald-600 text-white shadow-lg shadow-emerald-600/20"><CheckCircle2 size={22} /></span>
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.14em] text-emerald-700">VERIFIED / Approved</p>
            <h2 className="mt-1 text-lg font-bold text-emerald-950">Upload completed</h2>
          </div>
        </div>
      </div>

      <div className="space-y-4 p-5 sm:p-6">
        <div>
          <h3 className="text-base font-bold text-slate-900">{asset.title}</h3>
          <p className="mt-1 text-sm text-slate-500">{asset.assetType === 'IMAGE' ? 'Image' : 'Document'} · {asset.originalFilename}</p>
        </div>

        <div className="grid grid-cols-2 gap-3 text-sm">
          <ResultDetail label="File size" value={formatFileSize(asset.fileSize)} />
          <ResultDetail label="Uploaded" value={formatDate(asset.uploadDate)} />
        </div>

        <div className="rounded-xl border border-slate-200 bg-slate-50 p-3.5">
          <div className="flex items-center justify-between gap-3">
            <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500">SHA-256</p>
            <button className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-xs font-semibold text-indigo-600 hover:bg-indigo-50" onClick={onCopy} type="button">
              {copied ? <Check size={14} /> : <Clipboard size={14} />}
              {copied ? 'Copied' : 'Copy hash'}
            </button>
          </div>
          <p className="mt-2 break-all font-mono text-[11px] leading-5 text-slate-700">{asset.sha256Hash}</p>
        </div>
      </div>
    </section>
  )
}

function ResultDetail({ label, value }) {
  return <div><p className="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">{label}</p><p className="mt-1 font-semibold text-slate-700">{value}</p></div>
}

function formatFileSize(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value < 1) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const unitIndex = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  const size = value / (1024 ** unitIndex)
  return `${size.toFixed(unitIndex === 0 || size >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

function formatDate(value) {
  if (!value) return 'Unknown'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Unknown'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

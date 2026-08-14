import { RotateCcw, Search, SlidersHorizontal } from 'lucide-react'

export default function AssetFilters({
  filters,
  hasActiveFilters,
  onChange,
  onClear,
  onSearchChange,
  searchValue,
}) {
  return (
    <section className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-sm shadow-slate-200/30 sm:p-5">
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <SlidersHorizontal className="text-indigo-600" size={18} />
            <h2 className="text-sm font-bold text-slate-900">Find assets</h2>
          </div>
          {hasActiveFilters && (
            <button
              className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-indigo-600 outline-none hover:bg-indigo-50 focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              onClick={onClear}
              type="button"
            >
              <RotateCcw size={14} /> Clear filters
            </button>
          )}
        </div>

        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-[minmax(190px,1.5fr)_repeat(3,minmax(120px,0.75fr))]">
          <div>
            <label className="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500" htmlFor="asset-search">Search</label>
            <div className="relative mt-1.5">
              <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={17} />
              <input
                className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
                id="asset-search"
                onChange={(event) => onSearchChange(event.target.value)}
                placeholder="Search assets..."
                type="search"
                value={searchValue}
              />
            </div>
          </div>

          <FilterSelect
            id="asset-type-filter"
            label="Type"
            onChange={(value) => onChange('type', value)}
            options={[
              { label: 'All types', value: '' },
              { label: 'Images', value: 'IMAGE' },
              { label: 'Documents', value: 'DOCUMENT' },
            ]}
            value={filters.type}
          />

          <FilterSelect
            id="asset-status-filter"
            label="Status"
            onChange={(value) => onChange('status', value)}
            options={[
              { label: 'All statuses', value: '' },
              { label: 'Verified', value: 'VERIFIED' },
              { label: 'Pending', value: 'PENDING' },
              { label: 'Rejected', value: 'REJECTED' },
            ]}
            value={filters.status}
          />

          <FilterSelect
            id="asset-sort-filter"
            label="Sort"
            onChange={(value) => onChange('sort', value)}
            options={[
              { label: 'Newest first', value: 'newest' },
              { label: 'Oldest first', value: 'oldest' },
            ]}
            value={filters.sort}
          />
        </div>
      </div>
    </section>
  )
}

function FilterSelect({ id, label, onChange, options, value }) {
  return (
    <div>
      <label className="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500" htmlFor={id}>{label}</label>
      <select
        className="mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-medium text-slate-700 outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
        id={id}
        onChange={(event) => onChange(event.target.value)}
        value={value}
      >
        {options.map((option) => <option key={option.value || 'all'} value={option.value}>{option.label}</option>)}
      </select>
    </div>
  )
}

import {
  ArrowRight,
  Check,
  CheckCircle2,
  FileCheck2,
  FileText,
  Fingerprint,
  Image as ImageIcon,
  LockKeyhole,
  RefreshCw,
  ShieldCheck,
  UploadCloud,
} from 'lucide-react'
import { Link } from 'react-router-dom'

import { useAuth } from '../../hooks/useAuth'

const steps = [
  {
    number: '01',
    icon: UploadCloud,
    title: 'Upload a supported file',
    text: 'Add a JPEG, PNG, or PDF through your private, authenticated workspace.',
  },
  {
    number: '02',
    icon: Fingerprint,
    title: 'Capture its fingerprint',
    text: 'AuthVault validates the file and calculates SHA-256 while streaming it into controlled storage.',
  },
  {
    number: '03',
    icon: RefreshCw,
    title: 'Verify it again later',
    text: 'Recalculate the stored file’s hash and compare it with the original record, byte for byte.',
  },
]

export default function Landing() {
  const { isAuthenticated } = useAuth()
  const signedIn = isAuthenticated()

  return (
    <div className="min-h-screen overflow-hidden bg-[#f7faf9] text-slate-950">
      <header className="relative z-30 border-b border-white/10 bg-[#07111f] text-white">
        <div className="mx-auto flex h-20 max-w-7xl items-center justify-between px-5 sm:px-8">
          <Link className="flex items-center gap-3" to="/">
            <span className="grid h-10 w-10 place-items-center rounded-xl bg-teal-300 text-slate-950"><ShieldCheck size={21} strokeWidth={2.3} /></span>
            <span>
              <span className="block text-base font-bold tracking-tight">AuthVault</span>
              <span className="block text-[9px] font-bold uppercase tracking-[0.22em] text-slate-400">File integrity</span>
            </span>
          </Link>

          <nav className="hidden items-center gap-7 text-sm font-medium text-slate-300 md:flex" aria-label="Main navigation">
            <a className="transition hover:text-white" href="#features">Features</a>
            <a className="transition hover:text-white" href="#how-it-works">How it works</a>
            <a className="transition hover:text-white" href="#supported-files">Supported files</a>
          </nav>

          <div className="flex items-center gap-2 sm:gap-3">
            {!signedIn && <Link className="hidden px-3 py-2 text-sm font-semibold text-slate-300 transition hover:text-white sm:block" to="/login">Sign in</Link>}
            <Link className="inline-flex items-center gap-2 rounded-xl bg-teal-300 px-4 py-2.5 text-sm font-bold text-slate-950 transition hover:bg-teal-200" to={signedIn ? '/dashboard' : '/register'}>
              {signedIn ? 'Open dashboard' : 'Get started'} <ArrowRight size={16} />
            </Link>
          </div>
        </div>
      </header>

      <main>
        <section className="relative bg-[#07111f] pb-24 pt-16 text-white sm:pb-28 sm:pt-20 lg:pb-36 lg:pt-24">
          <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
            <div className="absolute -right-40 top-0 h-[520px] w-[520px] rounded-full bg-teal-300/10 blur-3xl" />
            <div className="absolute -left-48 bottom-0 h-96 w-96 rounded-full bg-sky-500/10 blur-3xl" />
            <div className="absolute inset-0 opacity-[0.045]" style={{ backgroundImage: 'linear-gradient(rgba(255,255,255,.8) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.8) 1px, transparent 1px)', backgroundSize: '52px 52px' }} />
          </div>

          <div className="relative mx-auto grid max-w-7xl items-center gap-14 px-5 sm:px-8 lg:grid-cols-[1.04fr_0.96fr] lg:gap-16">
            <div>
              <div className="inline-flex items-center gap-2 rounded-full border border-teal-300/20 bg-teal-300/10 px-3.5 py-2 text-xs font-bold text-teal-200">
                <CheckCircle2 size={15} /> Verifiable SHA-256 integrity records
              </div>
              <h1 className="mt-7 max-w-3xl text-4xl font-bold leading-[1.08] tracking-[-0.035em] sm:text-5xl lg:text-6xl">
                Know whether your file is <span className="text-teal-300">still the same file.</span>
              </h1>
              <p className="mt-6 max-w-2xl text-base leading-7 text-slate-300 sm:text-lg sm:leading-8">AuthVault records a trusted fingerprint when you upload an image or PDF, then lets you prove whether its stored bytes have changed.</p>
              <div className="mt-8 flex flex-col gap-3 sm:flex-row">
                <Link className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-300 px-5 py-3.5 text-sm font-bold text-slate-950 transition hover:bg-teal-200" to={signedIn ? '/assets' : '/register'}>
                  {signedIn ? 'Upload an asset' : 'Create your free workspace'} <ArrowRight size={17} />
                </Link>
                <a className="inline-flex items-center justify-center gap-2 rounded-xl border border-white/15 bg-white/[0.04] px-5 py-3.5 text-sm font-bold text-white transition hover:bg-white/[0.08]" href="#how-it-works">See how it works</a>
              </div>
              <div className="mt-8 flex flex-wrap gap-x-6 gap-y-3 text-xs font-medium text-slate-400">
                <span className="flex items-center gap-2"><Check className="text-teal-300" size={15} /> Owner-only access</span>
                <span className="flex items-center gap-2"><Check className="text-teal-300" size={15} /> Duplicate detection</span>
                <span className="flex items-center gap-2"><Check className="text-teal-300" size={15} /> Streamed hashing</span>
              </div>
            </div>

            <HeroRecord />
          </div>
        </section>

        <section className="relative z-10 mx-auto -mt-10 max-w-6xl px-5 sm:px-8">
          <div className="grid overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl shadow-slate-900/[0.06] sm:grid-cols-3">
            <TrustPoint icon={LockKeyhole} title="Private by default" text="Every asset request is scoped to the authenticated owner." />
            <TrustPoint icon={Fingerprint} title="Exact comparison" text="SHA-256 detects whether even one byte has changed." />
            <TrustPoint icon={FileCheck2} title="Auditable history" text="Upload and integrity checks create verification records." />
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-5 pb-8 pt-24 sm:px-8 sm:pt-28" id="features">
          <SectionHeading eyebrow="Implemented features" title="Everything you need to register and recheck a file" text="Every feature below is available now through the authenticated asset workspace." />
          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            <FeatureCard icon={UploadCloud} title="Validated uploads" text="Upload JPEG, PNG, and PDF files with content, signature, and size validation." />
            <FeatureCard icon={Fingerprint} title="SHA-256 records" text="Capture a 64-character fingerprint while the file streams into controlled storage." />
            <FeatureCard icon={RefreshCw} title="Integrity rechecks" text="Recalculate the stored file’s hash and compare it with the original fingerprint." />
            <FeatureCard icon={FileCheck2} title="Asset history" text="Browse owner-only records, download files, and review verification history." />
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-5 py-24 sm:px-8 sm:py-28" id="how-it-works">
          <SectionHeading eyebrow="A clear, repeatable process" title="Integrity verification in three steps" text="No vague trust score. AuthVault compares the file you stored with the exact fingerprint captured at upload." />
          <div className="mt-12 grid gap-5 lg:grid-cols-3">
            {steps.map(({ number, icon: Icon, title, text }) => (
              <article className="relative overflow-hidden rounded-2xl border border-slate-200 bg-white p-6 shadow-sm" key={number}>
                <span className="absolute right-5 top-4 text-5xl font-black tracking-tighter text-slate-100">{number}</span>
                <span className="relative grid h-12 w-12 place-items-center rounded-xl bg-[#e2f7f1] text-teal-800"><Icon size={22} /></span>
                <h3 className="relative mt-6 text-lg font-bold">{title}</h3>
                <p className="relative mt-2 text-sm leading-6 text-slate-500">{text}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="bg-[#e8f7f3] py-24 sm:py-28" id="why-authvault">
          <div className="mx-auto grid max-w-7xl items-center gap-12 px-5 sm:px-8 lg:grid-cols-2 lg:gap-20">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.2em] text-teal-800">Evidence with honest boundaries</p>
              <h2 className="mt-4 text-3xl font-bold tracking-tight sm:text-4xl">A precise answer to one important question.</h2>
              <p className="mt-5 text-base leading-7 text-slate-600">Has the stored file changed since it was uploaded? AuthVault answers that with exact hash equality and keeps the result separate from other kinds of evidence.</p>
              <div className="mt-8 space-y-4">
                <BoundaryItem icon={CheckCircle2} title="What VERIFIED means" text="The current stored bytes exactly match the SHA-256 fingerprint recorded at upload." />
                <BoundaryItem icon={AlertCircleIcon} title="What it does not mean" text="It does not prove authorship, copyright ownership, or that the content itself is true." />
              </div>
            </div>

            <div className="rounded-3xl bg-white p-6 shadow-xl shadow-teal-900/[0.08] sm:p-8">
              <div className="flex items-center justify-between border-b border-slate-100 pb-5">
                <div>
                  <p className="text-xs font-bold uppercase tracking-[0.15em] text-slate-400">Integrity check</p>
                  <h3 className="mt-1.5 font-bold">Two fingerprints. One answer.</h3>
                </div>
                <span className="grid h-11 w-11 place-items-center rounded-xl bg-emerald-50 text-emerald-700"><ShieldCheck size={21} /></span>
              </div>
              <HashLine label="Original SHA-256" value="9d7f...a62e" />
              <div className="my-3 flex items-center gap-3"><span className="h-px flex-1 bg-slate-100" /><span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">exactly equals</span><span className="h-px flex-1 bg-slate-100" /></div>
              <HashLine label="Current SHA-256" value="9d7f...a62e" />
              <div className="mt-6 flex items-center gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-emerald-900">
                <CheckCircle2 size={20} />
                <div><p className="text-sm font-bold">VERIFIED</p><p className="mt-0.5 text-xs text-emerald-700">Stored bytes are unchanged.</p></div>
              </div>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-5 py-24 sm:px-8 sm:py-28" id="supported-files">
          <div className="grid items-center gap-12 lg:grid-cols-[0.85fr_1.15fr] lg:gap-16">
            <SectionHeading eyebrow="Focused by design" title="Built for the files that matter now" text="The current MVP supports common images and PDF documents with strict signature validation and configurable size limits." align="left" />
            <div className="grid gap-4 sm:grid-cols-2">
              <FileCard icon={ImageIcon} formats="JPEG · PNG" limit="Up to 25 MB" title="Images" />
              <FileCard icon={FileText} formats="PDF" limit="Up to 50 MB" title="Documents" />
            </div>
          </div>
        </section>

        <section className="px-5 pb-24 sm:px-8 sm:pb-28">
          <div className="relative mx-auto max-w-7xl overflow-hidden rounded-3xl bg-[#07111f] px-6 py-12 text-center text-white sm:px-10 sm:py-16">
            <div className="absolute left-1/2 top-0 h-52 w-96 -translate-x-1/2 rounded-full bg-teal-300/10 blur-3xl" aria-hidden="true" />
            <div className="relative">
              <p className="text-xs font-bold uppercase tracking-[0.2em] text-teal-300">Start with a fingerprint</p>
              <h2 className="mx-auto mt-4 max-w-2xl text-3xl font-bold tracking-tight sm:text-4xl">Give your files an integrity record you can check.</h2>
              <p className="mx-auto mt-4 max-w-xl text-sm leading-6 text-slate-400">Create a private workspace, upload a supported file, and let AuthVault preserve the evidence needed to detect future changes.</p>
              <Link className="mt-8 inline-flex items-center gap-2 rounded-xl bg-teal-300 px-5 py-3.5 text-sm font-bold text-slate-950 hover:bg-teal-200" to={signedIn ? '/assets' : '/register'}>{signedIn ? 'Go to assets' : 'Create an account'} <ArrowRight size={17} /></Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-5 py-8 text-sm text-slate-500 sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <Link className="flex items-center gap-2 font-bold text-slate-900" to="/"><ShieldCheck className="text-teal-700" size={19} /> AuthVault</Link>
          <p>Exact file integrity for images and PDF documents.</p>
        </div>
      </footer>
    </div>
  )
}

function HeroRecord() {
  return (
    <div className="landing-float relative mx-auto w-full max-w-lg lg:ml-auto">
      <div className="absolute -inset-5 rounded-[2rem] border border-white/[0.06] bg-white/[0.025]" />
      <div className="relative overflow-hidden rounded-3xl border border-white/10 bg-white/[0.07] p-3 shadow-2xl shadow-black/30 backdrop-blur-xl sm:p-4">
        <div className="rounded-2xl bg-white p-5 text-slate-950 sm:p-6">
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-center gap-3">
              <span className="grid h-12 w-12 place-items-center rounded-xl bg-sky-50 text-sky-700"><ImageIcon size={22} /></span>
              <div><p className="font-bold">Original artwork.png</p><p className="mt-1 text-xs text-slate-400">PNG · 4.8 MB</p></div>
            </div>
            <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-bold text-emerald-700">VERIFIED</span>
          </div>
          <div className="mt-6 rounded-xl bg-slate-950 p-4 text-white">
            <div className="flex items-center justify-between"><p className="text-[10px] font-bold uppercase tracking-[0.15em] text-slate-400">SHA-256 fingerprint</p><Fingerprint className="text-teal-300" size={17} /></div>
            <p className="mt-3 break-all font-mono text-[11px] leading-5 text-slate-300">9d7f3a184b0e5c8a42e7d19fb6c31112e07684c442c91c3e4599c9a43d85a62e</p>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">Uploaded</p><p className="mt-1 text-xs font-bold">Today, 10:42</p></div>
            <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">Last checked</p><p className="mt-1 text-xs font-bold">Just now</p></div>
          </div>
          <div className="mt-4 flex items-center gap-2 rounded-xl bg-emerald-50 px-4 py-3 text-xs font-bold text-emerald-800"><CheckCircle2 size={17} /> Current bytes match the original record</div>
        </div>
      </div>
    </div>
  )
}

function TrustPoint({ icon: Icon, title, text }) {
  return (
    <div className="flex gap-3.5 border-b border-slate-100 p-5 last:border-0 sm:border-b-0 sm:border-r sm:last:border-r-0 sm:p-6">
      <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-teal-50 text-teal-800"><Icon size={19} /></span>
      <div><h3 className="text-sm font-bold">{title}</h3><p className="mt-1 text-xs leading-5 text-slate-500">{text}</p></div>
    </div>
  )
}

function FeatureCard({ icon: Icon, title, text }) {
  return (
    <article className="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-1 hover:border-teal-200 hover:shadow-lg hover:shadow-teal-900/[0.05]">
      <span className="grid h-11 w-11 place-items-center rounded-xl bg-slate-950 text-teal-300 transition group-hover:bg-teal-700 group-hover:text-white"><Icon size={20} /></span>
      <h3 className="mt-5 font-bold">{title}</h3>
      <p className="mt-2 text-sm leading-6 text-slate-500">{text}</p>
    </article>
  )
}

function SectionHeading({ eyebrow, title, text, align = 'center' }) {
  const alignment = align === 'left' ? '' : 'mx-auto text-center'
  return (
    <div className={`max-w-2xl ${alignment}`}>
      <p className="text-xs font-bold uppercase tracking-[0.2em] text-teal-700">{eyebrow}</p>
      <h2 className="mt-4 text-3xl font-bold tracking-tight sm:text-4xl">{title}</h2>
      <p className="mt-4 text-base leading-7 text-slate-600">{text}</p>
    </div>
  )
}

function BoundaryItem({ icon: Icon, title, text }) {
  return (
    <div className="flex gap-3.5 rounded-2xl border border-teal-900/5 bg-white/60 p-4">
      <Icon className="mt-0.5 shrink-0 text-teal-800" size={20} />
      <div><h3 className="text-sm font-bold">{title}</h3><p className="mt-1 text-sm leading-6 text-slate-600">{text}</p></div>
    </div>
  )
}

function AlertCircleIcon(props) {
  return <span {...props}><span className="grid h-5 w-5 place-items-center rounded-full border-2 border-current text-[11px] font-black">!</span></span>
}

function HashLine({ label, value }) {
  return (
    <div className="mt-5 flex items-center justify-between gap-4 rounded-xl border border-slate-100 bg-slate-50 p-4">
      <div><p className="text-[10px] font-bold uppercase tracking-[0.13em] text-slate-400">{label}</p><p className="mt-1.5 font-mono text-sm font-bold text-slate-800">{value}</p></div>
      <Fingerprint className="shrink-0 text-slate-300" size={19} />
    </div>
  )
}

function FileCard({ icon: Icon, formats, limit, title }) {
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <span className="grid h-12 w-12 place-items-center rounded-xl bg-slate-950 text-teal-300"><Icon size={22} /></span>
      <h3 className="mt-6 text-xl font-bold">{title}</h3>
      <p className="mt-2 text-sm font-semibold text-teal-700">{formats}</p>
      <p className="mt-1 text-sm text-slate-500">{limit}</p>
    </article>
  )
}

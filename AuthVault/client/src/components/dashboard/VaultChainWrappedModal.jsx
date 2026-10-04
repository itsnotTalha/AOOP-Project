import { ArrowRight, ChevronLeft, ChevronRight, Lock, Share2, ShieldCheck, Sparkles, Trophy, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import Button from '../ui/Button';
import '../../styles/vault-wrapped.css';

export default function VaultChainWrappedModal({ open, onClose, summary }) {
 const SLIDES = [
  { title: 'Your workspace recap', subtitle: 'Your current collection', metric: String((summary?.totalAssets || 0) + (summary?.totalDocuments || 0)), caption: 'Images and documents in your libraries.', icon: ShieldCheck, class: 'wrapped-slide-0' },
  { title: 'Organized and protected', subtitle: 'Vault coverage', metric: String(summary?.totalOrganizedAssets || 0), caption: `Assets organized across ${summary?.totalVaults || 0} password-protected vaults.`, icon: Lock, class: 'wrapped-slide-1' },
  { title: 'Verification history', subtitle: 'Saved technical evidence', metric: String(summary?.totalVerificationReports || 0), caption: 'Image and document verification reports saved to your account.', icon: Trophy, class: 'wrapped-slide-2' },
  { title: 'Your marketplace', subtitle: 'Current account activity', metric: `${summary?.walletBalance || 0} cr`, caption: `${summary?.activeListings || 0} active marketplace listings.`, icon: Sparkles, class: 'wrapped-slide-3' },
 ];
 function download() {
  const text = SLIDES.map(slide => `${slide.title}: ${slide.metric}\n${slide.caption}`).join('\n\n');
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
  const link = document.createElement('a'); link.href = url; link.download = 'authvault-workspace-recap.txt'; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
 }
	const [current, setCurrent] = useState(0);
 useEffect(() => { if(open) setCurrent(0); }, [open]);

	useEffect(() => {
		if (!open) return;
		const timer = setInterval(() => {
			setCurrent((prev) => (prev < SLIDES.length - 1 ? prev + 1 : prev));
		}, 6000);
		return () => clearInterval(timer);
	}, [open, current]);

	if (!open) return null;

	const slide = SLIDES[current];
	const IconComp = slide.icon;

	return (
		<div className="wrapped-backdrop" role="dialog" aria-modal="true">
			<div className={`wrapped-container ${slide.class}`}>
				<div>
					<div className="wrapped-progress-bar-row">
						{SLIDES.map((_, idx) => (
							<div key={idx} className="wrapped-progress-track">
								<div
									className="wrapped-progress-fill"
									style={{
										width: idx < current ? '100%' : idx === current ? '100%' : '0%',
									}}
								/>
							</div>
						))}
					</div>

					<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '14px' }}>
						<div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.72rem', fontWeight: 700, letterSpacing: '0.05em' }}>
							<Sparkles size={15} style={{ color: '#f8bc4e' }} />
							<span>VAULTCHAIN WRAPPED</span>
						</div>
						<button
							type="button"
							onClick={onClose}
							style={{ background: 'none', border: 0, color: '#fff', cursor: 'pointer', padding: '4px' }}
						>
							<X size={20} />
						</button>
					</div>
				</div>

				<div key={current} className="wrapped-slide-content">
					<div style={{ width: '60px', height: '60px', borderRadius: '18px', background: 'rgba(255,255,255,0.15)', backdropFilter: 'blur(8px)', display: 'grid', placeItems: 'center', marginBottom: '16px' }}>
						<IconComp size={30} />
					</div>
					<h2 style={{ fontSize: '1.3rem', fontWeight: 800, margin: '0 0 4px' }}>{slide.title}</h2>
					<span style={{ fontSize: '0.8rem', opacity: 0.8 }}>{slide.subtitle}</span>

					<div className="wrapped-big-number">{slide.metric}</div>

					<p style={{ fontSize: '0.85rem', opacity: 0.9, maxWidth: '280px', lineHeight: 1.5, margin: 0 }}>
						{slide.caption}
					</p>
				</div>

				<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
					<button
						type="button"
						className="icon-button"
						style={{ color: '#fff' }}
						disabled={current === 0}
						onClick={() => setCurrent((c) => Math.max(0, c - 1))}
					>
						<ChevronLeft size={20} />
					</button>

					{current === SLIDES.length - 1 ? (
						<Button onClick={download} icon={Share2}>
							Download recap
						</Button>
					) : (
						<span style={{ fontSize: '0.68rem', opacity: 0.6 }}>
							{current + 1} of {SLIDES.length}
						</span>
					)}

					<button
						type="button"
						className="icon-button"
						style={{ color: '#fff' }}
						disabled={current === SLIDES.length - 1}
						onClick={() => setCurrent((c) => Math.min(SLIDES.length - 1, c + 1))}
					>
						<ChevronRight size={20} />
					</button>
				</div>
			</div>
		</div>
	);
}

import { ShieldCheck } from 'lucide-react';

export default function BrandLogo({ compact = false, className = '' }) {
	return (
		<span className={`brand-logo ${className}`.trim()} aria-label="AuthVault">
			<span className="brand-logo__mark" aria-hidden="true">
				<svg viewBox="0 0 40 40" role="img" fill="none">
					<path
						d="M20 3.5 34 9v10.2c0 8.3-5.8 14.1-14 17.3-8.2-3.2-14-9-14-17.3V9l14-5.5Z"
						fill="rgba(65,217,255,0.08)"
						stroke="currentColor"
						strokeWidth="1.5"
					/>
					<circle cx="20" cy="18" r="7.5" stroke="currentColor" strokeWidth="1.5" strokeDasharray="3 2" />
					<circle cx="20" cy="18" r="3.2" fill="currentColor" fillOpacity="0.25" stroke="currentColor" strokeWidth="1.5" />
					<path d="M20 10.5v2M20 23.5v2M12.5 18h2M25.5 18h2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
				</svg>
				<ShieldCheck size={12} strokeWidth={2.6} />
			</span>
			{compact ? null : <span className="brand-logo__text">AuthVault</span>}
		</span>
	);
}

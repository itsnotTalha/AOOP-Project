import {
	CheckCircle2, Clock, Copy, Cpu, Database, Fingerprint,
	Hash, Layers, Lock, ShieldCheck, Sparkles,
} from 'lucide-react';
import CopyButton from '../ui/CopyButton';

export default function BlockchainFlow({ flow, title = 'Cryptographic Blockchain Flow' }) {
	if (!flow) {
		return (
			<div className="blockchain-flow-wrapper" style={{ padding: '16px', background: 'var(--bg-subtle)', borderRadius: '12px', border: '1px solid var(--border)' }}>
				<div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--text-muted)' }}>
					<Clock size={16} />
					<span style={{ fontSize: '0.78rem' }}>Waiting for blockchain block generation...</span>
				</div>
			</div>
		);
	}

	const steps = flow.steps || [];
	const block = flow.latestBlock;

	return (
		<div className="blockchain-flow-wrapper">
			<div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
				<div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
					<span style={{ color: 'var(--primary)', display: 'grid', placeItems: 'center' }}>
						<Layers size={18} />
					</span>
					<h3 style={{ margin: 0, fontSize: '0.88rem', fontWeight: 650 }}>{title}</h3>
				</div>
				<span className="blockchain-block-badge">
					<ShieldCheck size={12} /> Ledger Verified
				</span>
			</div>

			{/* Flow Steps */}
			<div className="blockchain-steps-list">
				{steps.map((s, idx) => (
					<div key={idx} className="blockchain-step-item">
						<div className={`blockchain-step-icon ${s.status === 'verified' || s.status === 'confirmed' || s.status === 'minted' ? 'is-completed' : 'is-active'}`}>
							{s.step === 1 ? <Fingerprint size={18} /> :
							 s.step === 2 ? <Cpu size={18} /> :
							 s.step === 3 ? <Layers size={18} /> :
							 <ShieldCheck size={18} />}
						</div>
						<div className="blockchain-step-body">
							<h4>
								<span>{s.step}. {s.title}</span>
								<span style={{ fontSize: '0.68rem', color: 'var(--success)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
									<CheckCircle2 size={12} /> {s.status}
								</span>
							</h4>
							<p>{s.detail}</p>
							{s.hash ? (
								<div style={{ marginTop: '6px', fontSize: '0.7rem', display: 'flex', alignItems: 'center', gap: '6px' }}>
									<span style={{ color: 'var(--text-muted)' }}>Hash:</span>
									<code style={{ fontSize: '0.68rem', wordBreak: 'break-all' }}>{s.hash}</code>
								</div>
							) : null}
						</div>
					</div>
				))}
			</div>

			{/* Minted Block Card */}
			{block ? (
				<div className="blockchain-block-card">
					<div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
						<div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
							<span className="status-badge status-badge--success" style={{ fontWeight: 'bold' }}>
								Block #{block.blockIndex}
							</span>
							<span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
								{block.action || 'REGISTERED'}
							</span>
						</div>
						<span style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
							{block.createdAt ? new Date(block.createdAt).toLocaleTimeString() : 'Just now'}
						</span>
					</div>

					<div style={{ display: 'grid', gap: '8px' }}>
						<div>
							<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2px' }}>
								<span style={{ fontSize: '0.64rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Current Block Hash</span>
								<CopyButton value={block.currentHash} />
							</div>
							<code style={{ fontSize: '0.7rem', wordBreak: 'break-all', display: 'block', padding: '6px 8px', background: 'var(--surface-sunken)', borderRadius: '6px', border: '1px solid var(--border)' }}>
								{block.currentHash}
							</code>
						</div>

						<div>
							<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2px' }}>
								<span style={{ fontSize: '0.64rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Previous Block Hash</span>
								<CopyButton value={block.previousHash} />
							</div>
							<code style={{ fontSize: '0.7rem', wordBreak: 'break-all', display: 'block', padding: '6px 8px', background: 'var(--surface-sunken)', borderRadius: '6px', border: '1px solid var(--border)' }}>
								{block.previousHash}
							</code>
						</div>
					</div>
				</div>
			) : null}
		</div>
	);
}

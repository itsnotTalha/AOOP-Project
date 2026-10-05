import { AlertCircle, AlertTriangle, CheckCircle2, Flag, Send } from 'lucide-react';
import { useState } from 'react';

import { useAuth } from '../../context/AuthContext';
import { verificationService } from '../../services/verificationService';
import Button from '../ui/Button';
import Modal from '../ui/Modal';
import { formatReportDate } from './verificationUtils';

const DISPUTE_REASONS = [
	{ id: 'unauthorized_upload', label: 'I am the original creator — Uploaded without my permission' },
	{ id: 'stolen_copyright', label: 'Copyright / IP Infringement — Stolen or copied asset' },
	{ id: 'impersonation', label: 'Impersonation or unauthorized commercialization' },
	{ id: 'other', label: 'Other ownership dispute' },
];

export default function DisputeAssetModal({ open, onClose, match, verificationReference, onSuccess }) {
	const { user } = useAuth();
	const [reason, setReason] = useState('unauthorized_upload');
	const [evidence, setEvidence] = useState('');
	const [contactEmail, setContactEmail] = useState(user?.email || '');
	const [submitting, setSubmitting] = useState(false);
	const [error, setError] = useState('');

	if (!open || !match) return null;

	const assetId = match.assetId || match.asset?.id;
	const ownerName = match.ownerName || 'Unknown User';
	const ownerUniqueId = match.ownerUniqueId || match.ownerReference || (match.ownerId ? `USR-${String(match.ownerId).padStart(6, '0')}` : 'Unknown');

	async function handleSubmit(e) {
		e?.preventDefault?.();
		if (!evidence.trim() || evidence.trim().length < 5) {
			setError('Please provide detailed evidence or explanation of your ownership claim (at least 5 characters).');
			return;
		}
		if (!assetId) {
			setError('Matched asset reference could not be resolved.');
			return;
		}

		setSubmitting(true);
		setError('');
		try {
			const result = await verificationService.reportDispute({
				verificationReference,
				assetId,
				reason,
				evidence: evidence.trim(),
				contactEmail: contactEmail.trim() || user?.email,
				matchType: match.matchType || (match.sha256Match ? 'exact' : 'visual'),
				confidence: match.sha256Match ? 100 : Math.max(50, 100 - (match.distance || 0) * 5),
			});
			if (onSuccess) onSuccess(result);
			onClose();
		} catch (err) {
			setError(err.message || 'Failed to submit ownership dispute.');
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Modal
			open={open}
			title="Report Asset & Claim Ownership"
			description="Submit an official ownership claim to platform administrators. Administrators will investigate the cryptographic fingerprints, creation timestamps, and submitted evidence."
			onClose={onClose}
		>
			<form onSubmit={handleSubmit} className="dispute-modal-form" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
				{error ? (
					<div className="error-banner" role="alert" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
						<AlertCircle size={16} />
						<span>{error}</span>
					</div>
				) : null}

				<div className="dispute-target-summary" style={{
					padding: '12px 14px',
					borderRadius: '10px',
					background: 'var(--bg-subtle, rgba(255,255,255,0.03))',
					border: '1px solid var(--border)',
					fontSize: '0.82rem',
					display: 'grid',
					gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
					gap: '10px'
				}}>
					<div>
						<span style={{ color: 'var(--text-muted)', fontSize: '0.72rem', display: 'block' }}>Target Asset</span>
						<strong>{match.assetReference}</strong> {match.assetTitle ? `(${match.assetTitle})` : ''}
					</div>
					<div>
						<span style={{ color: 'var(--text-muted)', fontSize: '0.72rem', display: 'block' }}>Current Registered Owner</span>
						<strong>{ownerName}</strong> <span style={{ color: 'var(--primary)' }}>({ownerUniqueId})</span>
					</div>
					<div>
						<span style={{ color: 'var(--text-muted)', fontSize: '0.72rem', display: 'block' }}>Registration Date</span>
						<strong>{formatReportDate(match.registeredAt)}</strong>
					</div>
					<div>
						<span style={{ color: 'var(--text-muted)', fontSize: '0.72rem', display: 'block' }}>Match Signal</span>
						<strong style={{ color: match.sha256Match ? 'var(--success)' : 'inherit' }}>
							{match.sha256Match ? 'Exact Cryptographic Match (SHA-256)' : `${match.distance} bit perceptual distance`}
						</strong>
					</div>
				</div>

				<div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
					<label style={{ fontSize: '0.8rem', fontWeight: 600 }}>Dispute Category</label>
					<select
						value={reason}
						onChange={(e) => setReason(e.target.value)}
						className="input-select"
						style={{
							padding: '9px 12px',
							borderRadius: '8px',
							background: 'var(--surface)',
							border: '1px solid var(--border)',
							color: 'inherit',
							fontSize: '0.85rem'
						}}
					>
						{DISPUTE_REASONS.map((r) => (
							<option key={r.id} value={r.id}>{r.label}</option>
						))}
					</select>
				</div>

				<div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
					<label style={{ fontSize: '0.8rem', fontWeight: 600 }}>
						Ownership Proof & Details <span style={{ color: 'var(--danger, #ef4444)' }}>*</span>
					</label>
					<textarea
						rows={4}
						value={evidence}
						onChange={(e) => setEvidence(e.target.value)}
						placeholder="Explain why this asset belongs to you. Mention your original creation date, source project files (RAW, PSD, SVG), public links where you published it first, or copyright registrations..."
						style={{
							padding: '10px 12px',
							borderRadius: '8px',
							background: 'var(--surface)',
							border: '1px solid var(--border)',
							color: 'inherit',
							fontSize: '0.82rem',
							resize: 'vertical',
							fontFamily: 'inherit'
						}}
						required
					/>
				</div>

				<div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
					<label style={{ fontSize: '0.8rem', fontWeight: 600 }}>Contact Email for Admin Investigation</label>
					<input
						type="email"
						value={contactEmail}
						onChange={(e) => setContactEmail(e.target.value)}
						placeholder="your.email@example.com"
						style={{
							padding: '9px 12px',
							borderRadius: '8px',
							background: 'var(--surface)',
							border: '1px solid var(--border)',
							color: 'inherit',
							fontSize: '0.85rem'
						}}
					/>
				</div>

				<footer style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}>
					<Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
						Cancel
					</Button>
					<Button type="submit" variant="danger" icon={Flag} disabled={submitting || !evidence.trim()}>
						{submitting ? 'Submitting Dispute...' : 'Submit Report to Admin'}
					</Button>
				</footer>
			</form>
		</Modal>
	);
}


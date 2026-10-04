import { Trash2, X } from 'lucide-react';
import { useEffect, useState } from 'react';

import Button from '../ui/Button';

export default function AssetConfirmModal({ open, title, description, confirmLabel = 'Delete Asset', onClose, onConfirm }) {
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState('');

	useEffect(() => {
		if (open) {
			setLoading(false);
			setError('');
		}
	}, [open]);

	if (!open) return null;

	async function confirm() {
		setLoading(true);
		setError('');
		try {
			await onConfirm();
			onClose();
		} catch (confirmError) {
			setError(confirmError.message || 'Failed to delete asset');
		} finally {
			setLoading(false);
		}
	}

	return (
		<div className="modal" role="dialog" aria-modal="true" aria-labelledby="asset-confirm-title">
			<button type="button" className="modal__backdrop" onClick={onClose} aria-label="Cancel" />
			<div className="modal__card vault-confirm-modal">
				<header className="modal__header">
					<div>
						<span
							className="modal__icon vault-confirm-modal__icon"
							style={{ background: 'rgba(239, 68, 68, 0.15)', color: '#ef4444' }}
						>
							<Trash2 size={18} />
						</span>
						<div>
							<h2 id="asset-confirm-title">{title}</h2>
							<p>{description}</p>
						</div>
					</div>
					<button type="button" className="icon-button" onClick={onClose} aria-label="Close">
						<X size={17} />
					</button>
				</header>
				{error ? <div className="error-banner">{error}</div> : null}
				<footer>
					<Button type="button" variant="ghost" onClick={onClose}>
						Cancel
					</Button>
					<Button type="button" variant="danger" disabled={loading} onClick={confirm}>
						{loading ? 'Deleting…' : confirmLabel}
					</Button>
				</footer>
			</div>
		</div>
	);
}


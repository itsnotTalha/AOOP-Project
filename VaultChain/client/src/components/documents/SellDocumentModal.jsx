import { AlertCircle, CheckCircle2, Coins, FileText, ShoppingBag, X } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';

import { marketplaceService } from '../../services/marketplaceService';
import Button from '../ui/Button';

export default function SellDocumentModal({ document, onClose, onListed }) {
	const [title, setTitle] = useState(document?.originalName?.replace(/\.[^.]+$/, '') || '');
	const [description, setDescription] = useState(`Verified document: ${document?.originalName || ''}`);
	const [price, setPrice] = useState(10);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState('');
	const [listing, setListing] = useState(null);

	if (!document) return null;

	async function handleSubmit(e) {
		e.preventDefault();
		setError('');
		if (!title.trim()) {
			setError('Listing title is required.');
			return;
		}
		if (price <= 0) {
			setError('Price must be greater than 0.');
			return;
		}
		setLoading(true);
		try {
			const res = await marketplaceService.createListing({
				documentId: document.id,
				title: title.trim(),
				description: description.trim(),
				price: Number(price),
			});
			setListing(res);
			onListed?.(res);
		} catch (err) {
			setError(err.message || 'Failed to list document for sale.');
		} finally {
			setLoading(false);
		}
	}

	return (
		<div className="modal" role="dialog" aria-modal="true" aria-labelledby="sell-doc-title">
			<button className="modal__backdrop" aria-label="Close" onClick={onClose} />
			<section className="modal__card" style={{ maxWidth: '520px' }}>
				<header className="modal__header">
					<div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
						<span className="modal__icon"><ShoppingBag size={20} /></span>
						<div>
							<h2 id="sell-doc-title">List Document for Sale</h2>
							<p>{document.originalName}</p>
						</div>
					</div>
					<button className="button button--ghost" aria-label="Close modal" onClick={onClose}><X size={16} /></button>
				</header>

				{listing ? (
					<div className="upload-success">
						<span><CheckCircle2 size={32} /></span>
						<h3>Document Listed on Marketplace!</h3>
						<p>
							Your document has been successfully listed under reference{' '}
							<code>{listing.reference}</code> for <strong>{Number(listing.price).toLocaleString()} Credits</strong>.
						</p>
						<div style={{ marginTop: '20px', display: 'flex', gap: '10px' }}>
							<Link className="button button--primary" to={`/marketplace/${listing.reference}`}>
								View in Marketplace
							</Link>
							<Button variant="secondary" onClick={onClose}>Close</Button>
						</div>
					</div>
				) : (
					<form className="modal__form" onSubmit={handleSubmit}>
						{error ? <div className="error-banner" style={{ marginBottom: '16px' }}><AlertCircle size={16} />{error}</div> : null}

						<div style={{ padding: '12px', background: 'var(--bg-subtle)', borderRadius: '10px', border: '1px solid var(--border)', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '10px' }}>
							<FileText size={24} color="var(--primary)" />
							<div>
								<strong style={{ fontSize: '0.82rem', display: 'block' }}>{document.originalName}</strong>
								<span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
									{document.mimeType === 'application/pdf' ? `${document.pageCount || 1} pages · PDF` : 'Image document'}
								</span>
							</div>
						</div>

						<div className="field">
							<label htmlFor="listing-title">Listing Title</label>
							<input
								id="listing-title"
								className="input"
								value={title}
								onChange={(e) => setTitle(e.target.value)}
								required
								maxLength={120}
								placeholder="e.g. Verified Financial Statement 2026"
							/>
						</div>

						<div className="field" style={{ marginTop: '12px' }}>
							<label htmlFor="listing-desc">Description (optional)</label>
							<textarea
								id="listing-desc"
								className="input"
								style={{ minHeight: '80px', resize: 'vertical' }}
								value={description}
								onChange={(e) => setDescription(e.target.value)}
								maxLength={1000}
								placeholder="Describe what makes this verified document valuable..."
							/>
						</div>

						<div className="field" style={{ marginTop: '12px' }}>
							<label htmlFor="listing-price">Price in VaultChain Credits</label>
							<div style={{ position: 'relative' }}>
								<input
									id="listing-price"
									className="input"
									type="number"
									min="1"
									step="0.01"
									value={price}
									onChange={(e) => setPrice(e.target.value)}
									required
								/>
							</div>
							<small style={{ color: 'var(--text-muted)', display: 'block', marginTop: '4px' }}>
								Minimum 1 Credit. When sold, platform fee applies according to system settings.
							</small>
						</div>

						<footer className="modal__footer" style={{ marginTop: '20px' }}>
							<Button variant="secondary" onClick={onClose} type="button" disabled={loading}>
								Cancel
							</Button>
							<Button type="submit" icon={Coins} disabled={loading}>
								{loading ? 'Listing...' : 'List for Sale'}
							</Button>
						</footer>
					</form>
				)}
			</section>
		</div>
	);
}

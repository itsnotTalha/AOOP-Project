import { AlertCircle, CheckCircle2, FileImage, UploadCloud, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { assetService } from '../../services/assetService';
import BlockchainFlow from '../blockchain/BlockchainFlow';
import Button from '../ui/Button';

export default function UploadAssetModal({ open, onClose, onUploaded, initialFile = null }) {
	const inputRef = useRef(null);
	const [title, setTitle] = useState('');
	const [category, setCategory] = useState('image');
	const [description, setDescription] = useState('');
	const [file, setFile] = useState(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState('');
	const [result, setResult] = useState(null);

	useEffect(() => {
		if (!open) return;
		setFile(initialFile);
		setTitle(initialFile?.name?.replace(/\.[^.]+$/, '') || '');
		setDescription('');
		setError('');
		setResult(null);
	}, [open, initialFile]);

	if (!open) return null;

	async function handleSubmit(event) {
		event.preventDefault();
		setError('');
		setResult(null);
		if (!file) { setError('Choose a JPG, PNG, or WebP image to continue.'); return; }
		setLoading(true);
		try {
			const response = await assetService.uploadAsset({ title, category, description, file });
			setResult(response);
			onUploaded?.(response);
		} catch (uploadError) {
			setError(uploadError.message);
		} finally {
			setLoading(false);
		}
	}

	function closeModal() {
		if (!loading) onClose();
	}

	return (
		<div className="modal" role="dialog" aria-modal="true" aria-labelledby="upload-title">
			<button className="modal__backdrop" aria-label="Close upload dialog" onClick={closeModal} />
			<section className="modal__card" style={{ maxWidth: result ? '680px' : '560px', width: '95vw', maxHeight: '90vh' }}>
				<header className="modal__header"><div><span className="modal__icon"><UploadCloud size={19} /></span><div><h2 id="upload-title">{result ? 'Asset Registered & Minted' : 'Upload digital asset'}</h2><p>{result ? 'Cryptographic fingerprint and blockchain flow generated.' : 'Create a cryptographic fingerprint and inspect image metadata.'}</p></div></div><button type="button" className="icon-button" onClick={closeModal} aria-label="Close"><X size={18} /></button></header>
				{result ? (
					<div className="modal__form" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
						<div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '14px', background: 'rgba(66, 214, 157, 0.08)', borderRadius: '12px', border: '1px solid rgba(66, 214, 157, 0.2)' }}>
							<CheckCircle2 size={26} color="var(--success)" style={{ flexShrink: 0 }} />
							<div>
								<strong style={{ fontSize: '0.88rem', display: 'block' }}>{result.asset?.title}</strong>
								<span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>Registered as #{result.asset?.id} · Perceptual hash & metadata stored</span>
							</div>
						</div>

						<div className="upload-success__hash" style={{ margin: 0 }}>
							<small>SHA-256 HASH</small>
							<code>{result.hash?.sha256}</code>
						</div>

						{result.blockchain ? (
							<BlockchainFlow flow={result.blockchain} title="Image Ledger Provenance Flow" />
						) : null}

						<footer className="modal__footer" style={{ marginTop: '6px' }}>
							<Button onClick={closeModal}>Done</Button>
						</footer>
					</div>
				) : (
					<form className="form-grid modal__form" onSubmit={handleSubmit}>
						<button type="button" className={`file-drop ${file ? 'has-file' : ''}`} onClick={() => inputRef.current?.click()}>
							<span className="file-drop__icon"><FileImage size={24} /></span><strong>{file ? file.name : 'Choose an image to protect'}</strong><span>{file ? `${(file.size / 1024 / 1024).toFixed(2)} MB selected` : 'JPG, PNG or WebP · maximum 20 MB'}</span>
						</button>
						<input ref={inputRef} className="sr-only" type="file" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" onChange={(event) => setFile(event.target.files?.[0] || null)} />
						<div className="form-row"><div className="field"><label htmlFor="asset-title">Asset title</label><input id="asset-title" className="input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Northern Lights" required /></div><div className="field"><label htmlFor="asset-category">Category</label><select id="asset-category" className="select" value={category} onChange={(e) => setCategory(e.target.value)}><option value="image">Image</option><option value="artwork">Artwork</option><option value="photography">Photography</option><option value="document-image">Document image</option></select></div></div>
						<div className="field"><label htmlFor="asset-description">Description <span className="field-hint">(optional)</span></label><textarea id="asset-description" className="textarea" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Add context about this asset" /></div>
						{error ? <div className="error-banner" role="alert"><AlertCircle size={16} />{error}</div> : null}
						<footer className="modal__footer"><Button type="button" variant="secondary" onClick={closeModal}>Cancel</Button><Button type="submit" icon={UploadCloud} disabled={loading}>{loading ? 'Fingerprinting…' : 'Upload & fingerprint'}</Button></footer>
					</form>
				)}
			</section>
		</div>
	);
}

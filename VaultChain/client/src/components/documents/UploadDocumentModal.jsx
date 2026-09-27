import { AlertCircle, AlertTriangle, CheckCircle2, Copy, FileText, Layers, ShieldCheck, ShoppingBag, UploadCloud, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { documentService } from '../../services/documentService';
import BlockchainFlow from '../blockchain/BlockchainFlow';
import Button from '../ui/Button';
import CopyButton from '../ui/CopyButton';

const ACCEPTED_TYPES = new Set(['application/pdf', 'image/png', 'image/jpeg']);

export default function UploadDocumentModal({ open, onClose, onUploaded, onOpenSell }) {
	const inputRef = useRef(null);
	const [file, setFile] = useState(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState('');
	const [result, setResult] = useState(null);

	useEffect(() => {
		if (!open) return;
		setFile(null);
		setError('');
		setResult(null);
		setLoading(false);
	}, [open]);

	if (!open) return null;

	async function submit(event) {
		event.preventDefault();
		setError('');
		if (!file) {
			setError('Choose a PDF, PNG, JPG, or JPEG document.');
			return;
		}
		if (!ACCEPTED_TYPES.has(file.type)) {
			setError('Only PDF, PNG, JPG, and JPEG documents are supported.');
			return;
		}
		if (file.size > 20 * 1024 * 1024) {
			setError('Document size must not exceed 20 MB.');
			return;
		}
		setLoading(true);
		try {
			const document = await documentService.upload(file);
			setResult(document);
			onUploaded?.(document);
		} catch (uploadError) {
			setError(uploadError.message || 'Failed to upload document');
		} finally {
			setLoading(false);
		}
	}

	const dup = result?.duplicateInfo;

	return (
		<div className="modal" role="dialog" aria-modal="true" aria-labelledby="document-upload-title">
			<button className="modal__backdrop" aria-label="Close" onClick={loading ? undefined : onClose} />
			<section className="modal__card" style={{ maxWidth: result ? '720px' : '580px', width: '95vw', maxHeight: '90vh' }}>
				<header className="modal__header">
					<div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
						<span className="modal__icon"><UploadCloud size={19} /></span>
						<div>
							<h2 id="document-upload-title">{result ? 'Document Uploaded & Minted' : 'Upload Document'}</h2>
							<p>{result ? 'Extraction and blockchain flow complete.' : 'Store document, compute multi-hashes, extract OCR, and mint ledger block.'}</p>
						</div>
					</div>
					<button type="button" className="icon-button" onClick={onClose} disabled={loading} aria-label="Close"><X size={18} /></button>
				</header>

				{result ? (
					<div className="modal__form" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
						{/* Success Header */}
						<div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '14px', background: 'rgba(66, 214, 157, 0.08)', borderRadius: '12px', border: '1px solid rgba(66, 214, 157, 0.2)' }}>
							<CheckCircle2 size={28} color="var(--success)" style={{ flexShrink: 0 }} />
							<div>
								<strong style={{ fontSize: '0.88rem', display: 'block' }}>{result.originalName}</strong>
								<span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
									Reference: <code>{result.reference}</code> · OCR status: <strong style={{ color: 'var(--success)' }}>{result.ocrStatus}</strong>
								</span>
							</div>
						</div>

						{/* Duplicate / Modified Document Banner */}
						{dup && dup.isDuplicate ? (
							<div className={`document-dup-alert ${dup.matchType === 'exact_sha256' ? 'is-danger' : ''}`}>
								<AlertTriangle size={20} style={{ flexShrink: 0, marginTop: '2px', color: dup.matchType === 'exact_sha256' ? 'var(--danger)' : 'var(--warning)' }} />
								<div>
									<strong>
										{dup.matchType === 'exact_sha256'
											? 'Duplicate Document Alert'
											: dup.matchType === 'semantic_ocr'
											? 'Content Duplicate Alert'
											: `Modified Document Detected (${dup.modificationPercent}% changed)`}
									</strong>
									<p>{dup.message}</p>
								</div>
							</div>
						) : (
							<div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 14px', borderRadius: '8px', background: 'rgba(66, 214, 157, 0.08)', border: '1px solid rgba(66, 214, 157, 0.2)' }}>
								<ShieldCheck size={18} color="var(--success)" />
								<span style={{ fontSize: '0.78rem', color: 'var(--text)' }}><strong>Original & Authentic:</strong> No duplicates or modified versions detected.</span>
							</div>
						)}

						{/* Multi-Hash Badges */}
						<div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '8px' }}>
							<div style={{ padding: '8px 12px', background: 'var(--surface-sunken)', borderRadius: '8px', border: '1px solid var(--border)' }}>
								<span style={{ fontSize: '0.64rem', color: 'var(--primary)', fontWeight: 'bold' }}>SHA-256 HASH</span>
								<code style={{ fontSize: '0.68rem', display: 'block', overflow: 'hidden', textOverflow: 'ellipsis' }}>{result.hashes?.sha256 || result.sha256}</code>
							</div>
							{result.hashes?.semanticHash && (
								<div style={{ padding: '8px 12px', background: 'var(--surface-sunken)', borderRadius: '8px', border: '1px solid var(--border)' }}>
									<span style={{ fontSize: '0.64rem', color: 'var(--success)', fontWeight: 'bold' }}>SEMANTIC OCR HASH</span>
									<code style={{ fontSize: '0.68rem', display: 'block', overflow: 'hidden', textOverflow: 'ellipsis' }}>{result.hashes.semanticHash}</code>
								</div>
							)}
						</div>

						{/* Blockchain Ledger Flow */}
						<BlockchainFlow flow={result.blockchain} title="Document Ledger Provenance Flow" />

						<footer className="modal__footer" style={{ marginTop: '10px', display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
							{onOpenSell ? (
								<Button size="sm" variant="secondary" icon={ShoppingBag} onClick={() => { onClose(); onOpenSell(result); }}>
									Sell in Marketplace
								</Button>
							) : null}
							<Button size="sm" onClick={onClose}>Done</Button>
						</footer>
					</div>
				) : (
					<form className="form-grid modal__form" onSubmit={submit}>
						<button type="button" className={`file-drop ${file ? 'has-file' : ''}`} onClick={() => inputRef.current?.click()}>
							<span className="file-drop__icon"><FileText size={24} /></span>
							<strong>{file ? file.name : 'Choose a document'}</strong>
							<span>{file ? `${(file.size / 1024 / 1024).toFixed(2)} MB selected` : 'PDF, PNG, JPG or JPEG · maximum 20 MB'}</span>
						</button>
						<input ref={inputRef} className="sr-only" type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" onChange={(event) => setFile(event.target.files?.[0] || null)} />
						{error ? <div className="error-banner" role="alert"><AlertCircle size={16} />{error}</div> : null}
						<footer className="modal__footer">
							<Button type="button" variant="secondary" onClick={onClose} disabled={loading}>Cancel</Button>
							<Button type="submit" icon={UploadCloud} disabled={loading}>{loading ? 'Uploading, extracting & minting…' : 'Upload document'}</Button>
						</footer>
					</form>
				)}
			</section>
		</div>
	);
}

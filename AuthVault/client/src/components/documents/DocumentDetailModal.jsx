import {
	AlertCircle, AlertTriangle, CheckCircle2, Copy, Cpu, Database,
	Download, Eye, FileSearch, FileText, Fingerprint, Hash, Layers,
	Lock, ShieldCheck, ShoppingBag, X,
} from 'lucide-react';
import { useEffect, useState } from 'react';

import { documentService } from '../../services/documentService';
import BlockchainFlow from '../blockchain/BlockchainFlow';
import Button from '../ui/Button';
import CopyButton from '../ui/CopyButton';
import LoadingState from '../ui/LoadingState';
import StatusBadge from '../ui/StatusBadge';

const TONES = { completed: 'success', failed: 'warning', processing: 'info', pending: 'info' };

export default function DocumentDetailModal({ documentId, onClose, onOpenText, onOpenPreview, onOpenSell }) {
	const [doc, setDoc] = useState(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState('');
	const [activeTab, setActiveTab] = useState('overview'); // 'overview' | 'metadata' | 'hashes' | 'blockchain'

	useEffect(() => {
		let active = true;
		setLoading(true);
		setError('');
		documentService
			.get(documentId)
			.then((data) => {
				if (active) setDoc(data);
			})
			.catch((err) => {
				if (active) setError(err.message || 'Failed to load document details');
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, [documentId]);

	if (!documentId) return null;

	const dup = doc?.duplicateInfo;

	return (
		<div className="modal" role="dialog" aria-modal="true" aria-labelledby="doc-detail-title">
			<button className="modal__backdrop" aria-label="Close" onClick={onClose} />
			<section className="modal__card" style={{ maxWidth: '800px', width: '95vw', maxHeight: '90vh' }}>
				<header className="modal__header">
					<div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
						<span className="modal__icon"><FileText size={20} /></span>
						<div>
							<h2 id="doc-detail-title">{doc ? doc.originalName : 'Document details'}</h2>
							<p>{doc ? `${doc.reference} · ${doc.mimeType}` : 'Loading...'}</p>
						</div>
					</div>
					<button className="button button--ghost" aria-label="Close modal" onClick={onClose}><X size={16} /></button>
				</header>

				{loading ? (
					<div style={{ padding: '40px' }}><LoadingState label="Inspecting document and blockchain proof..." /></div>
				) : error ? (
					<div style={{ padding: '24px' }} className="error-banner"><AlertCircle size={16} />{error}</div>
				) : doc ? (
					<div className="modal__form" style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
						{/* Duplicate / Modified Document Alert */}
						{dup && dup.isDuplicate ? (
							<div className={`document-dup-alert ${dup.matchType === 'exact_sha256' ? 'is-danger' : ''}`}>
								<AlertTriangle size={20} style={{ flexShrink: 0, marginTop: '2px', color: dup.matchType === 'exact_sha256' ? 'var(--danger)' : 'var(--warning)' }} />
								<div style={{ flex: 1 }}>
									<strong>
										{dup.matchType === 'exact_sha256'
											? 'Exact Duplicate Document'
											: dup.matchType === 'semantic_ocr'
											? 'Content Duplicate (Different Encoding)'
											: `Modified Document (${dup.modificationPercent}% changed)`}
									</strong>
									<p>{dup.message}</p>
									{dup.matchedDocument ? (
										<div style={{ marginTop: '8px', fontSize: '0.74rem', display: 'flex', gap: '8px', alignItems: 'center' }}>
											<span>Matched with:</span>
											<code>{dup.matchedDocument.reference}</code>
											<span>({dup.matchedDocument.originalName})</span>
										</div>
									) : null}
								</div>
							</div>
						) : (
							<div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 14px', borderRadius: '8px', background: 'rgba(66, 214, 157, 0.08)', border: '1px solid rgba(66, 214, 157, 0.2)' }}>
								<ShieldCheck size={18} color="var(--success)" />
								<span style={{ fontSize: '0.78rem', color: 'var(--text)' }}><strong>Unique Document:</strong> No duplicates or modifications detected on the ledger.</span>
							</div>
						)}

						{/* Tabs */}
						<div className="filter-group" style={{ display: 'flex', gap: '6px', borderBottom: '1px solid var(--border)', paddingBottom: '10px' }}>
							<button type="button" className={`filter-chip ${activeTab === 'overview' ? 'is-active' : ''}`} onClick={() => setActiveTab('overview')}>
								Overview
							</button>
							<button type="button" className={`filter-chip ${activeTab === 'metadata' ? 'is-active' : ''}`} onClick={() => setActiveTab('metadata')}>
								Metadata & Stats
							</button>
							<button type="button" className={`filter-chip ${activeTab === 'hashes' ? 'is-active' : ''}`} onClick={() => setActiveTab('hashes')}>
								Multi-Hashes
							</button>
							<button type="button" className={`filter-chip ${activeTab === 'blockchain' ? 'is-active' : ''}`} onClick={() => setActiveTab('blockchain')}>
								Blockchain Provenance
							</button>
						</div>

						{/* Overview Tab */}
						{activeTab === 'overview' && (
							<div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
								<div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '10px' }}>
									<div style={{ padding: '12px', background: 'var(--bg-subtle)', borderRadius: '10px', border: '1px solid var(--border)' }}>
										<span style={{ display: 'block', fontSize: '0.66rem', color: 'var(--text-muted)' }}>FILE FORMAT</span>
										<strong style={{ fontSize: '0.85rem' }}>{doc.mimeType === 'application/pdf' ? 'PDF Document' : 'Image Document'}</strong>
									</div>
									<div style={{ padding: '12px', background: 'var(--bg-subtle)', borderRadius: '10px', border: '1px solid var(--border)' }}>
										<span style={{ display: 'block', fontSize: '0.66rem', color: 'var(--text-muted)' }}>PAGE COUNT</span>
										<strong style={{ fontSize: '0.85rem' }}>{doc.pageCount || 1} {doc.pageCount === 1 ? 'page' : 'pages'}</strong>
									</div>
									<div style={{ padding: '12px', background: 'var(--bg-subtle)', borderRadius: '10px', border: '1px solid var(--border)' }}>
										<span style={{ display: 'block', fontSize: '0.66rem', color: 'var(--text-muted)' }}>OCR STATUS</span>
										<div style={{ marginTop: '4px' }}><StatusBadge tone={TONES[doc.ocrStatus] || 'neutral'}>OCR {doc.ocrStatus}</StatusBadge></div>
									</div>
									<div style={{ padding: '12px', background: 'var(--bg-subtle)', borderRadius: '10px', border: '1px solid var(--border)' }}>
										<span style={{ display: 'block', fontSize: '0.66rem', color: 'var(--text-muted)' }}>UPLOADED AT</span>
										<strong style={{ fontSize: '0.78rem' }}>{new Date(doc.createdAt).toLocaleString()}</strong>
									</div>
								</div>

								{/* Action Buttons */}
								<div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', marginTop: '6px' }}>
									<Button size="sm" icon={FileSearch} onClick={onOpenText}>View exact OCR text</Button>
									<Button size="sm" variant="secondary" icon={Eye} onClick={onOpenPreview}>Preview file</Button>
									<Button size="sm" variant="secondary" icon={ShoppingBag} onClick={onOpenSell}>Sell in marketplace</Button>
								</div>
							</div>
						)}

						{/* Metadata & Stats Tab */}
						{activeTab === 'metadata' && (
							<div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
								<h3 style={{ fontSize: '0.85rem', margin: 0 }}>Document Text Statistics</h3>
								<div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(100px, 1fr))', gap: '10px' }}>
									<div style={{ padding: '10px', background: 'var(--surface-sunken)', borderRadius: '8px', border: '1px solid var(--border)', textAlign: 'center' }}>
										<span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Words</span>
										<div style={{ fontSize: '1.1rem', fontWeight: 'bold' }}>{doc.metadata?.wordCount || 0}</div>
									</div>
									<div style={{ padding: '10px', background: 'var(--surface-sunken)', borderRadius: '8px', border: '1px solid var(--border)', textAlign: 'center' }}>
										<span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Characters</span>
										<div style={{ fontSize: '1.1rem', fontWeight: 'bold' }}>{doc.metadata?.characterCount || 0}</div>
									</div>
									<div style={{ padding: '10px', background: 'var(--surface-sunken)', borderRadius: '8px', border: '1px solid var(--border)', textAlign: 'center' }}>
										<span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Lines</span>
										<div style={{ fontSize: '1.1rem', fontWeight: 'bold' }}>{doc.metadata?.lineCount || 0}</div>
									</div>
									<div style={{ padding: '10px', background: 'var(--surface-sunken)', borderRadius: '8px', border: '1px solid var(--border)', textAlign: 'center' }}>
										<span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Paragraphs</span>
										<div style={{ fontSize: '1.1rem', fontWeight: 'bold' }}>{doc.metadata?.paragraphCount || 0}</div>
									</div>
								</div>

								{doc.metadata && (
									<>
										<h3 style={{ fontSize: '0.85rem', margin: '10px 0 0' }}>Extracted File Metadata</h3>
										<div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '8px' }}>
											{Object.entries(doc.metadata)
												.filter(([k]) => !['wordCount', 'characterCount', 'lineCount', 'paragraphCount'].includes(k))
												.map(([key, val]) => (
													<div key={key} style={{ padding: '8px 12px', background: 'var(--bg-subtle)', borderRadius: '6px', border: '1px solid var(--border)', fontSize: '0.75rem' }}>
														<span style={{ color: 'var(--text-muted)', textTransform: 'capitalize' }}>{key.replace(/_/g, ' ')}:</span>{' '}
														<strong style={{ wordBreak: 'break-all' }}>{String(val)}</strong>
													</div>
												))}
										</div>
									</>
								)}
							</div>
						)}

						{/* Multi-Hashes Tab */}
						{activeTab === 'hashes' && (
							<div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
								<p style={{ margin: 0, fontSize: '0.75rem', color: 'var(--text-muted)' }}>
									Multiple cryptographic and perceptual algorithms ensure byte-level integrity, content equivalence, and structural layout tracking.
								</p>

								<div style={{ padding: '12px', background: 'var(--surface-sunken)', borderRadius: '10px', border: '1px solid var(--border)' }}>
									<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
										<span style={{ fontSize: '0.72rem', fontWeight: 'bold', color: 'var(--primary)' }}>SHA-256 (Binary Integrity)</span>
										<CopyButton value={doc.hashes?.sha256 || doc.sha256} />
									</div>
									<code style={{ fontSize: '0.72rem', wordBreak: 'break-all' }}>{doc.hashes?.sha256 || doc.sha256}</code>
								</div>

								{doc.hashes?.md5 && (
									<div style={{ padding: '12px', background: 'var(--surface-sunken)', borderRadius: '10px', border: '1px solid var(--border)' }}>
										<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
											<span style={{ fontSize: '0.72rem', fontWeight: 'bold', color: 'var(--text-secondary)' }}>MD5 (Legacy Binary Checksum)</span>
											<CopyButton value={doc.hashes.md5} />
										</div>
										<code style={{ fontSize: '0.72rem', wordBreak: 'break-all' }}>{doc.hashes.md5}</code>
									</div>
								)}

								{doc.hashes?.semanticHash && (
									<div style={{ padding: '12px', background: 'var(--surface-sunken)', borderRadius: '10px', border: '1px solid var(--border)' }}>
										<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
											<span style={{ fontSize: '0.72rem', fontWeight: 'bold', color: 'var(--success)' }}>Semantic OCR Hash (Content Invariance)</span>
											<CopyButton value={doc.hashes.semanticHash} />
										</div>
										<code style={{ fontSize: '0.72rem', wordBreak: 'break-all' }}>{doc.hashes.semanticHash}</code>
										<p style={{ margin: '6px 0 0', fontSize: '0.68rem', color: 'var(--text-muted)' }}>
											Normalized across case, unicode, and whitespace. Matches even if the file was re-scanned or re-encoded.
										</p>
									</div>
								)}

								{doc.hashes?.structureHash && (
									<div style={{ padding: '12px', background: 'var(--surface-sunken)', borderRadius: '10px', border: '1px solid var(--border)' }}>
										<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
											<span style={{ fontSize: '0.72rem', fontWeight: 'bold', color: 'var(--warning)' }}>Structure SimHash (Locality Sensitive)</span>
											<CopyButton value={doc.hashes.structureHash} />
										</div>
										<code style={{ fontSize: '0.72rem', wordBreak: 'break-all' }}>{doc.hashes.structureHash}</code>
										<p style={{ margin: '6px 0 0', fontSize: '0.68rem', color: 'var(--text-muted)' }}>
											Enables fast hamming distance comparison to identify modified or edited document revisions.
										</p>
									</div>
								)}
							</div>
						)}

						{/* Blockchain Provenance Tab */}
						{activeTab === 'blockchain' && (
							<div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
								<BlockchainFlow flow={doc.blockchain} />
							</div>
						)}
					</div>
				) : null}

				<footer className="modal__footer" style={{ padding: '16px 20px', borderTop: '1px solid var(--border)' }}>
					<Button variant="secondary" onClick={onClose}>Close</Button>
				</footer>
			</section>
		</div>
	);
}

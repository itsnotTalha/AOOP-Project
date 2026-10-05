import { ArrowUpRight, Fingerprint, LockKeyhole, Trash2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import StatusBadge from '../ui/StatusBadge';
import AssetThumbnail from './AssetThumbnail';

function formatFileSize(bytes) {
	if (!Number.isFinite(bytes)) return null;
	if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
	return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

export default function AssetCard({ asset, onInspect, onPreview, onDelete, view = 'grid' }) {
	const navigate = useNavigate();
	const dimensions = asset.width && asset.height ? `${asset.width} × ${asset.height}` : null;
	const uploadedAt = asset.createdAt ? new Date(asset.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : null;
	const locked = asset.vaultProtection?.isLocked;

	function handleCardClick(event) {
		if (event.target.closest('button') || event.target.closest('a')) {
			return;
		}
		onPreview?.(asset);
	}

	return (
		<article
			className={`asset-card asset-card--${view}`}
			onClick={handleCardClick}
			style={{ cursor: 'pointer' }}
			role="button"
			tabIndex={0}
			onKeyDown={(e) => {
				if (e.key === 'Enter' || e.key === ' ') {
					e.preventDefault();
					handleCardClick(e);
				}
			}}
		>
			<div className="asset-card__visual">
				<AssetThumbnail asset={asset} />
				{locked ? (
					<StatusBadge tone="warning">
						<LockKeyhole size={10} /> Locked
					</StatusBadge>
				) : asset.hasHash ? (
					<StatusBadge tone="success">
						<Fingerprint size={10} /> Verified identity
					</StatusBadge>
				) : null}
			</div>
			<div className="asset-card__body">
				<div className="asset-card__heading">
					<div>
						<h3>{asset.title}</h3>
						<p>Asset #{asset.id} · {asset.category || asset.mimeType || 'File'}</p>
					</div>
					<ArrowUpRight size={17} aria-hidden="true" />
				</div>
				<div className="asset-card__meta">
					<span>{dimensions || asset.mimeType || 'File'}</span>
					<span>{formatFileSize(asset.fileSize) || uploadedAt || 'Stored asset'}</span>
				</div>
				<div className="asset-card__footer">
					{locked ? (
						<span className="asset-card__locked-message">
							<LockKeyhole size={12} /> Protected by Vault — Unlock to access
						</span>
					) : (
						<>
							<span>{uploadedAt ? `Uploaded ${uploadedAt}` : 'Upload date unavailable'}</span>
							<div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
								<button
									type="button"
									className="text-button"
									onClick={(event) => {
										event.stopPropagation();
										navigate(`/assets/${asset.id}/inspect`);
									}}
									aria-label={`Inspect ${asset.title}`}
								>
									Inspect asset
								</button>
								{onDelete ? (
									<button
										type="button"
										className="text-button text-button--danger"
										onClick={(event) => {
											event.stopPropagation();
											onDelete(asset);
										}}
										aria-label={`Delete ${asset.title}`}
										title="Delete asset"
										style={{ color: '#ef4444', display: 'inline-flex', alignItems: 'center', gap: '3px' }}
									>
										<Trash2 size={13} />
										<span>Delete</span>
									</button>
								) : null}
							</div>
						</>
					)}
				</div>
			</div>
		</article>
	);
}

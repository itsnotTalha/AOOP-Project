import { LockKeyhole, ShieldCheck, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';

import { useAuth } from '../../context/AuthContext';
import { assetService } from '../../services/assetService';
import { formatReportDate } from './verificationUtils';

export default function OwnerBadge({ match, confidenceLevel }) {
	const { user } = useAuth();
	const [transfers, setTransfers] = useState([]);

	useEffect(() => {
		let active = true;
		if (!match?.ownerIsCurrentUser || !match.asset?.id) { setTransfers([]); return undefined; }
		assetService.getOwnershipHistory(match.asset.id).then((records) => { if (active) setTransfers(records); }).catch(() => { if (active) setTransfers([]); });
		return () => { active = false; };
	}, [match?.asset?.id, match?.ownerIsCurrentUser]);

	if (!match || confidenceLevel === 'low') {
		return <div className="owner-badge owner-badge--private">
			<span><LockKeyhole size={17}/></span>
			<div>
				<small>Registered owner</small>
				<strong>No matching owner</strong>
				<p>No reliable match is available.</p>
			</div>
		</div>;
	}

	if (match.ownerIsCurrentUser) {
		const selfName = user?.fullName || match.ownerName || 'You';
		const selfUniqueId = match.ownerUniqueId || (user?.id ? `USR-${String(user.id).padStart(6, '0')}` : 'You');
		return <div className="owner-badge owner-badge--self owner-badge--expanded">
			<span><ShieldCheck size={17}/></span>
			<div>
				<small>Registered owner · You (Authenticated)</small>
				<strong>{selfName} <span style={{ color: 'var(--primary)', fontWeight: 500, fontSize: '0.88em' }}>({selfUniqueId})</span></strong>
				<p>Verified since {new Date(match.registeredAt || user?.createdAt || Date.now()).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}</p>
			</div>
			<div className="owner-records">
				<strong>Owner-only transfer records</strong>
				{transfers.length ? transfers.slice(0, 3).map((record) => <div key={record.transactionReference}><span>{record.transferType || 'Transfer'} · {new Date(record.transferredAt).toLocaleDateString()}</span><small>{record.transactionReference} · {Number(record.price || 0).toLocaleString()} {record.currency || 'credits'}</small></div>) : <p>No ownership transfers recorded. This account holds the original registered record.</p>}
			</div>
		</div>;
	}

	const ownerName = match.ownerName || 'Registered User';
	const ownerUniqueId = match.ownerUniqueId || match.ownerReference || (match.ownerId ? `USR-${String(match.ownerId).padStart(6, '0')}` : 'Unknown ID');

	return <div className="owner-badge owner-badge--other">
		<span><UserRound size={17}/></span>
		<div>
			<small>Registered asset owner</small>
			<strong>{ownerName} <span style={{ color: 'var(--primary)', fontWeight: 600, fontSize: '0.88em' }}>({ownerUniqueId})</span></strong>
			<p>Asset registered {formatReportDate(match.registeredAt)}{match.ownerUsername ? ` · @${match.ownerUsername}` : ''}</p>
		</div>
	</div>;
}

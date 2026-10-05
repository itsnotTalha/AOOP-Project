import { AlertCircle, Ban, Check, CheckCircle2, Copy, Eye, FileText, Flag, Info, RotateCcw, ScanSearch, Search, ShieldAlert, X } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { formatInteger, formatPercent } from '../../admin/adminUtils';
import useAdminData from '../../admin/useAdminData';
import { AdminEmpty, AdminError, AdminLoading } from '../../components/admin/AdminDataState';
import AdminPageHeader, { downloadReport } from '../../components/admin/AdminPageHeader';
import AnalyticsCard from '../../components/admin/AnalyticsCard';
import StatCard from '../../components/admin/StatCard';
import Button from '../../components/ui/Button';
import Modal from '../../components/ui/Modal';
import StatusBadge from '../../components/ui/StatusBadge';
import Toast from '../../components/ui/Toast';
import { adminService } from '../../services/adminService';

const STATUS_TONES = {
	pending: 'warning',
	under_review: 'amber',
	resolved_transferred: 'success',
	resolved_removed: 'danger',
	rejected: 'neutral',
};

const STATUS_LABELS = {
	pending: 'Pending Review',
	under_review: 'Under Review',
	resolved_transferred: 'Resolved (Transferred)',
	resolved_removed: 'Resolved (Suspended)',
	rejected: 'Rejected',
};

export default function AdminVerificationPage() {
	const { range, setRange, data, loading, error, reload } = useAdminData(adminService.getVerification);
	const [selectedDispute, setSelectedDispute] = useState(null);
	const [adminNotes, setAdminNotes] = useState('');
	const [actionLoading, setActionLoading] = useState(false);
	const [actionError, setActionError] = useState('');
	const [toast, setToast] = useState('');
	const [disputeQuery, setDisputeQuery] = useState('');
	const [statusFilter, setStatusFilter] = useState('All');

	const header = (
		<AdminPageHeader
			eyebrow="Trust & safety"
			title="Verification & Ownership Claims"
			description="Audit cryptographic fingerprints, verification performance, and contested asset ownership reports."
			range={range}
			onRangeChange={setRange}
			exportName={data ? 'vaultchain-verification' : undefined}
			onExport={() => downloadReport('vaultchain-verification', data?.trend || [])}
		/>
	);

	const disputes = useMemo(() => data?.disputes || [], [data?.disputes]);

	const filteredDisputes = useMemo(() => {
		return disputes.filter((d) => {
			const matchesFilter =
				statusFilter === 'All' ||
				(statusFilter === 'Pending' && (d.status === 'pending' || d.status === 'under_review')) ||
				d.status === statusFilter;
			const text = `${d.dispute_reference} ${d.asset_title} ${d.claimant_name} ${d.claimant_email} ${d.owner_name} ${d.reason}`.toLowerCase();
			const matchesQuery = !disputeQuery.trim() || text.includes(disputeQuery.toLowerCase());
			return matchesFilter && matchesQuery;
		});
	}, [disputes, statusFilter, disputeQuery]);

	if (error) return <>{header}<AdminError message={error} onRetry={reload}/></>;
	if (loading || !data) return <>{header}<AdminLoading/></>;

	const { summary, trend, confidence } = data;
	const successRate = summary.total ? (summary.successful / summary.total) * 100 : 0;

	async function handleDisputeResolution(targetStatus) {
		if (!selectedDispute) return;
		setActionLoading(true);
		setActionError('');
		try {
			await adminService.updateDispute(selectedDispute.id, {
				status: targetStatus,
				adminNotes: adminNotes.trim(),
			});
			setToast(`Dispute ${selectedDispute.dispute_reference} updated to ${STATUS_LABELS[targetStatus] || targetStatus}.`);
			setSelectedDispute(null);
			setAdminNotes('');
			await reload();
		} catch (err) {
			setActionError(err.message || 'Failed to update dispute status.');
		} finally {
			setActionLoading(false);
		}
	}

	function openDisputeModal(dispute) {
		setSelectedDispute(dispute);
		setAdminNotes(dispute.admin_notes || '');
		setActionError('');
	}

	return (
		<>
			{header}
			<div className="admin-stats-grid">
				<StatCard
					label="Verification reports"
					value={formatInteger(summary.total)}
					detail="Created in selected period"
					icon={ScanSearch}
				/>
				<StatCard
					label="Processed successfully"
					value={formatInteger(summary.successful)}
					detail={`${formatPercent(successRate)} processing success`}
					icon={CheckCircle2}
					tone="green"
				/>
				<StatCard
					label="Exact duplicates"
					value={formatInteger(summary.duplicates)}
					detail="SHA-256 matches"
					icon={Copy}
					tone="amber"
				/>
				<StatCard
					label="Pending claims"
					value={formatInteger(summary.pendingDisputes ?? 0)}
					detail={`${formatInteger(summary.totalDisputes ?? 0)} total disputes`}
					icon={Flag}
					tone={(summary.pendingDisputes ?? 0) > 0 ? 'red' : 'neutral'}
				/>
			</div>

			<section className="admin-card" style={{ marginTop: '24px' }}>
				<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', flexWrap: 'wrap', gap: '12px' }}>
					<div>
						<h3 style={{ fontSize: '1.05rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px' }}>
							<Flag size={18} style={{ color: 'var(--danger, #ef4444)' }} />
							Asset Ownership Disputes & Prior Claims ({disputes.length})
						</h3>
						<p style={{ color: 'var(--text-muted)', fontSize: '0.78rem', margin: '4px 0 0' }}>
							Users submit these claims when an exact matching asset was uploaded before them without authorization.
						</p>
					</div>
					<div className="admin-table-toolbar" style={{ margin: 0 }}>
						<label>
							<Search size={16}/>
							<input
								value={disputeQuery}
								onChange={(e) => setDisputeQuery(e.target.value)}
								placeholder="Search claimant, uploader, or asset..."
							/>
						</label>
						<select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
							<option value="All">All statuses</option>
							<option value="Pending">Pending / Under Review</option>
							<option value="resolved_transferred">Resolved (Transferred)</option>
							<option value="resolved_removed">Resolved (Suspended)</option>
							<option value="rejected">Rejected</option>
						</select>
					</div>
				</div>

				{filteredDisputes.length ? (
					<div className="table-responsive">
						<table className="data-table" style={{ width: '100%', fontSize: '0.8rem', borderCollapse: 'collapse' }}>
							<thead>
								<tr style={{ borderBottom: '1px solid var(--border)', textAlign: 'left', color: 'var(--text-muted)' }}>
									<th style={{ padding: '10px 12px' }}>Reference / Date</th>
									<th style={{ padding: '10px 12px' }}>Contested Asset</th>
									<th style={{ padding: '10px 12px' }}>Claimant (Reporter)</th>
									<th style={{ padding: '10px 12px' }}>Existing Uploader</th>
									<th style={{ padding: '10px 12px' }}>Match Basis</th>
									<th style={{ padding: '10px 12px' }}>Status</th>
									<th style={{ padding: '10px 12px', textAlign: 'right' }}>Action</th>
								</tr>
							</thead>
							<tbody>
								{filteredDisputes.map((d) => (
									<tr key={d.id} style={{ borderBottom: '1px solid var(--border)' }}>
										<td style={{ padding: '12px' }}>
											<strong>{d.dispute_reference}</strong>
											<div style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}>
												{new Date(d.created_at).toLocaleDateString()}
											</div>
										</td>
										<td style={{ padding: '12px' }}>
											<strong>{d.asset_title || `Asset #${d.asset_id}`}</strong>
											<div style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}>
												AV-A{String(d.asset_id).padStart(6, '0')} · {d.asset_status}
											</div>
										</td>
										<td style={{ padding: '12px' }}>
											<strong>{d.claimant_name}</strong>
											<div style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}>
												USR-{String(d.claimant_id).padStart(6, '0')} · {d.claimant_email}
											</div>
										</td>
										<td style={{ padding: '12px' }}>
											<strong>{d.owner_name}</strong>
											<div style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}>
												USR-{String(d.registered_owner_id).padStart(6, '0')} · {d.owner_email}
											</div>
										</td>
										<td style={{ padding: '12px' }}>
											<span style={{ fontWeight: 600, color: d.match_type === 'exact' ? 'var(--success)' : 'inherit' }}>
												{d.match_type === 'exact' ? 'Exact SHA-256' : `${d.confidence ?? 100}% visual`}
											</span>
											<div style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}>
												{d.reason?.replace('_', ' ')}
											</div>
										</td>
										<td style={{ padding: '12px' }}>
											<StatusBadge tone={STATUS_TONES[d.status] || 'neutral'}>
												{STATUS_LABELS[d.status] || d.status}
											</StatusBadge>
										</td>
										<td style={{ padding: '12px', textAlign: 'right' }}>
											<Button
												size="sm"
												variant={d.status === 'pending' || d.status === 'under_review' ? 'primary' : 'secondary'}
												onClick={() => openDisputeModal(d)}
											>
												{d.status === 'pending' || d.status === 'under_review' ? 'Review & Handle' : 'View Details'}
											</Button>
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				) : (
					<AdminEmpty
						title="No ownership claims match filters"
						description="When users report unauthorized matching assets in verification, claims appear here for administrator investigation."
					/>
				)}
			</section>

			<div className="admin-dashboard-grid" style={{ marginTop: '24px' }}>
				<AnalyticsCard className="is-wide" title="Verification processing success" description="Persisted reports not marked failed or rejected">
					{summary.total ? (
						<ResponsiveContainer width="100%" height={300}>
							<LineChart data={trend}>
								<CartesianGrid stroke="var(--chart-grid)" vertical={false}/>
								<XAxis dataKey="label" axisLine={false} tickLine={false}/>
								<YAxis domain={[0,100]} tickFormatter={(value) => `${value}%`} axisLine={false} tickLine={false}/>
								<Tooltip formatter={(value) => `${value}%`}/>
								<Line type="monotone" dataKey="rate" stroke="var(--success)" strokeWidth={2.5} dot={{ fill:'var(--surface)', strokeWidth:2 }}/>
							</LineChart>
						</ResponsiveContainer>
					) : (
						<AdminEmpty title="No verification reports"/>
					)}
				</AnalyticsCard>
				<AnalyticsCard title="Confidence coverage" description="Scores derived from stored hash evidence">
					<div className="admin-model-score">
						<strong>{formatInteger(summary.scored)}</strong>
						<span>Reports with calculable confidence</span>
						<i><b style={{ width: summary.total ? `${(summary.scored/summary.total)*100}%` : '0%' }}/></i>
					</div>
					<div className="admin-health-list">
						<div><span>Total reports</span><strong>{formatInteger(summary.total)}</strong></div>
						<div><span>Scored reports</span><strong>{formatInteger(summary.scored)}</strong></div>
						<div><span>Unscored reports</span><strong>{formatInteger(summary.total-summary.scored)}</strong></div>
						<div><span>Exact hash matches</span><strong>{formatInteger(summary.duplicates)}</strong></div>
					</div>
				</AnalyticsCard>
			</div>

			<AnalyticsCard title="AI confidence distribution" description="Similarity calculated from stored SHA-256 or perceptual hash distance">
				{summary.scored ? (
					<ResponsiveContainer width="100%" height={290}>
						<BarChart data={confidence}>
							<CartesianGrid stroke="var(--chart-grid)" vertical={false}/>
							<XAxis dataKey="range" axisLine={false} tickLine={false}/>
							<YAxis allowDecimals={false} axisLine={false} tickLine={false}/>
							<Tooltip/>
							<Bar dataKey="count" fill="var(--admin-accent)" radius={[6,6,0,0]} maxBarSize={70}/>
						</BarChart>
					</ResponsiveContainer>
				) : (
					<AdminEmpty title="No confidence scores available" description="Reports with fingerprint comparison evidence will populate this distribution."/>
				)}
			</AnalyticsCard>

			{selectedDispute ? (
				<Modal
					open={Boolean(selectedDispute)}
					title={`Dispute Review: ${selectedDispute.dispute_reference}`}
					description="Investigate the ownership dispute between the claimant and original uploader. Reassigning ownership transfers the asset on the blockchain."
					onClose={() => setSelectedDispute(null)}
				>
					<div style={{ display: 'flex', flexDirection: 'column', gap: '16px', fontSize: '0.85rem' }}>
						{actionError ? (
							<div className="error-banner" role="alert" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
								<AlertCircle size={16} />
								<span>{actionError}</span>
							</div>
						) : null}

						<div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px', background: 'var(--bg-subtle)', padding: '12px', borderRadius: '10px' }}>
							<div>
								<span style={{ color: 'var(--text-muted)', fontSize: '0.72rem', display: 'block' }}>Claimant (Asserted Creator)</span>
								<strong>{selectedDispute.claimant_name}</strong>
								<div style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>
									Unique ID: USR-{String(selectedDispute.claimant_id).padStart(6, '0')}<br/>
									Email: {selectedDispute.claimant_email}
								</div>
							</div>
							<div>
								<span style={{ color: 'var(--text-muted)', fontSize: '0.72rem', display: 'block' }}>Current Uploader</span>
								<strong>{selectedDispute.owner_name}</strong>
								<div style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>
									Unique ID: USR-{String(selectedDispute.registered_owner_id).padStart(6, '0')}<br/>
									Email: {selectedDispute.owner_email}
								</div>
							</div>
						</div>

						<div style={{ background: 'var(--bg-subtle)', padding: '12px', borderRadius: '10px' }}>
							<span style={{ color: 'var(--text-muted)', fontSize: '0.72rem', display: 'block' }}>Contested Asset & Cryptographic Proof</span>
							<strong>{selectedDispute.asset_title} (AV-A{String(selectedDispute.asset_id).padStart(6, '0')})</strong>
							<div style={{ color: 'var(--text-muted)', fontSize: '0.78rem', marginTop: '4px' }}>
								Match Basis: <strong style={{ color: selectedDispute.match_type === 'exact' ? 'var(--success)' : 'inherit' }}>
									{selectedDispute.match_type === 'exact' ? 'Exact SHA-256 Identical Fingerprint' : `${selectedDispute.confidence}% Perceptual Similarity`}
								</strong>
							</div>
						</div>

						<div>
							<span style={{ color: 'var(--text-muted)', fontSize: '0.72rem', display: 'block', marginBottom: '4px' }}>Claimant's Submitted Evidence & Rationale</span>
							<div style={{ padding: '10px 12px', borderRadius: '8px', background: 'var(--surface)', border: '1px solid var(--border)', whiteSpace: 'pre-wrap', maxHeight: '160px', overflowY: 'auto' }}>
								{selectedDispute.evidence || 'No details provided.'}
							</div>
						</div>

						<div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
							<label style={{ fontSize: '0.8rem', fontWeight: 600 }}>Administrator Resolution Notes</label>
							<textarea
								rows={3}
								value={adminNotes}
								onChange={(e) => setAdminNotes(e.target.value)}
								placeholder="Record investigation findings, source file verification, copyright certificates, or justification..."
								style={{ padding: '8px 12px', borderRadius: '8px', background: 'var(--surface)', border: '1px solid var(--border)', color: 'inherit', resize: 'vertical' }}
							/>
						</div>

						<div style={{ borderTop: '1px solid var(--border)', paddingTop: '14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
							<div style={{ display: 'flex', gap: '8px' }}>
								<Button
									variant="secondary"
									size="sm"
									disabled={actionLoading || selectedDispute.status === 'under_review'}
									onClick={() => handleDisputeResolution('under_review')}
								>
									Mark Under Review
								</Button>
								<Button
									variant="ghost"
									size="sm"
									disabled={actionLoading || selectedDispute.status === 'rejected'}
									onClick={() => handleDisputeResolution('rejected')}
								>
									Reject Claim
								</Button>
							</div>
							<div style={{ display: 'flex', gap: '8px' }}>
								<Button
									variant="danger"
									size="sm"
									disabled={actionLoading || selectedDispute.status === 'resolved_removed'}
									onClick={() => handleDisputeResolution('resolved_removed')}
								>
									Suspend Asset
								</Button>
								<Button
									variant="primary"
									size="sm"
									icon={Check}
									disabled={actionLoading || selectedDispute.status === 'resolved_transferred'}
									onClick={() => handleDisputeResolution('resolved_transferred')}
								>
									Transfer Ownership to Claimant
								</Button>
							</div>
						</div>
					</div>
				</Modal>
			) : null}

			<Toast message={toast} onClose={() => setToast('')} />
		</>
	);
}

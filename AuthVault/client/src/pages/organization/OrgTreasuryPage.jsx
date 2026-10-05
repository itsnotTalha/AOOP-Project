import { useEffect, useState } from 'react';
import { ArrowDownRight, ArrowUpRight, RefreshCw, Users, Wallet } from 'lucide-react';
import Button from '../../components/ui/Button';
import PageHeader from '../../components/ui/PageHeader';
import { organizationService } from '../../services/organizationService';
import '../../styles/organizations.css';
import '../../styles/organization-finance.css';
export default function OrgTreasuryPage() {
 const [org, setOrg] = useState(() => organizationService.getOrganization(organizationService.getActiveWorkspace().id));
 const [action, setAction] = useState('');
 const [amount, setAmount] = useState('');
 const [busy, setBusy] = useState(false);
 const [error, setError] = useState('');
 const [notice, setNotice] = useState('');
 const [filter, setFilter] = useState('all');
 useEffect(() => { const update = () => setOrg(organizationService.getOrganization(organizationService.getActiveWorkspace().id)); window.addEventListener('vaultchain:org_updated', update); return () => window.removeEventListener('vaultchain:org_updated', update); }, []);
 async function submit(event) {
  event.preventDefault(); if(busy) return; setBusy(true); setError(''); setNotice('');
  try { await organizationService[action === 'deposit' ? 'depositTreasury' : 'withdrawTreasury'](org.id, Number(amount)); setNotice(`${Number(amount).toLocaleString()} Credits transferred ${action === 'deposit' ? 'from your wallet to the treasury' : 'from the treasury to your wallet'}.`); setAction(''); setAmount(''); }
  catch(err) { setError(err.message); } finally { setBusy(false); }
 }
 if(!org) return <p>Organization unavailable.</p>;
 const transactions = org.treasuryTransactions || [];
 const inflow = transactions.filter(tx => tx.type === 'Inflow').reduce((n,tx) => n + Number(tx.amount),0);
 const outflow = transactions.filter(tx => tx.type === 'Outflow').reduce((n,tx) => n + Number(tx.amount),0);
 return <div className="org-industry-dashboard">
  <PageHeader eyebrow={`Organization finance · ${org.name}`} title="Treasury" description="Manage shared credits and review every transfer." action={<Button icon={RefreshCw} variant="secondary" onClick={() => organizationService.refresh().catch(err => setError(err.message))}>Refresh</Button>}/>
  {error && <p role="alert" className="error-banner">{error}</p>}{notice && <p role="status" className="success-banner">{notice}</p>}
  <div className="org-kpi-grid">{[[Wallet,'Treasury balance',org.treasuryBalance],[ArrowDownRight,'Total inflows',inflow],[ArrowUpRight,'Total outflows',outflow],[Users,'Contributors',org.members.length]].map(([Icon,label,value]) => <section className="org-kpi-card" key={label}><div className="kpi-header"><span className="kpi-label">{label}</span><Icon size={20}/></div><div className="kpi-value">{Number(value).toLocaleString()}</div><p>{label === 'Contributors' ? 'Registered members' : 'VaultChain Credits'}</p></section>)}</div>
  <section className="org-section-card"><h2>Transfer credits</h2><p>Only the organization owner can move treasury funds. Withdrawals return credits to the owner’s wallet. Contributor payouts are available in Revenue.</p><div className="page-actions"><Button disabled={!org.isOwner || busy} onClick={() => { setAction('deposit'); setError(''); }}>Deposit from wallet</Button><Button variant="secondary" disabled={!org.isOwner || busy} onClick={() => { setAction('withdraw'); setError(''); }}>Withdraw to wallet</Button></div>
   {action && <form className="org-form" onSubmit={submit}><label htmlFor="treasury-amount">{action === 'deposit' ? 'Deposit' : 'Withdrawal'} amount (Credits)</label><input id="treasury-amount" className="input" type="number" min="0.01" max="1000000000" step="0.01" required value={amount} onChange={event => setAmount(event.target.value)}/><div className="page-actions"><Button type="submit" disabled={busy}>{busy ? 'Transferring…' : 'Confirm transfer'}</Button><Button type="button" variant="secondary" disabled={busy} onClick={() => setAction('')}>Cancel</Button></div></form>}
  </section>
  <section className="org-section-card"><div className="org-section-header"><h2>Treasury transactions</h2><select aria-label="Transaction type" className="input" value={filter} onChange={e => setFilter(e.target.value)}><option value="all">All transfers</option><option value="Inflow">Inflows</option><option value="Outflow">Outflows</option></select></div><div className="table-scroll"><table className="data-table"><thead><tr><th>Date</th><th>Type</th><th>Details</th><th>Credits</th><th>Reference</th></tr></thead><tbody>{transactions.filter(tx => filter === 'all' || tx.type === filter).map(tx => <tr key={tx.id}><td>{new Date(tx.date).toLocaleString()}</td><td>{tx.type}</td><td>{tx.note || tx.category}</td><td>{Number(tx.amount).toLocaleString()}</td><td><code>{tx.id}</code></td></tr>)}</tbody></table>{!transactions.length && <p>No treasury activity yet.</p>}</div></section>
 </div>;
}

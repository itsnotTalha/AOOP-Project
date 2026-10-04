import { useEffect, useState } from 'react';
import BlockchainVisualization from '../../components/blockchain/BlockchainVisualization';
import PageHeader from '../../components/ui/PageHeader';
import SectionCard from '../../components/ui/SectionCard';
import Button from '../../components/ui/Button';
import { blockchainService } from '../../services/blockchainService';
export default function BlockchainPage() {
 const [data, setData] = useState(null);
 const [error, setError] = useState('');
 async function load() { try { const [stats, blocks] = await Promise.all([blockchainService.getStats(), blockchainService.getBlocks()]); setData({ stats, blocks }); setError(''); } catch(err) { setError(err.message); } }
 useEffect(() => { load(); }, []);
 return <><PageHeader eyebrow="Integrity ledger" title="Blockchain explorer" description="Inspect recorded asset events and learn how linked hashes expose changes." action={<Button onClick={load}>Refresh ledger</Button>}/>
 {error && <p className="error-banner" role="alert">{error}</p>}
 <SectionCard title="Live backend ledger" description={data ? `${data.stats.totalBlocks} blocks · Chain integrity ${data.stats.validChain ? 'valid' : 'failed'}` : 'Loading ledger…'}>
 <div style={{ overflowX: 'auto' }}><table className="data-table"><thead><tr><th>Block</th><th>Event</th><th>Asset</th><th>Hash</th></tr></thead><tbody>{data?.blocks.map(block => <tr key={block.blockIndex ?? block.id}><td>{block.blockIndex}</td><td>{block.action}</td><td>{block.assetId}</td><td><code>{block.currentHash}</code></td></tr>)}</tbody></table></div>{data && !data.blocks.length && <p>No recorded blocks yet. Upload or verify an asset to create an event.</p>}
 </SectionCard><SectionCard title="Interactive learning demo" description="This simulation is separate from your stored ledger."><BlockchainVisualization/></SectionCard></>;
}

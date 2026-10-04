import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import Button from '../ui/Button';
import { marketplaceService } from '../../services/marketplaceService';
import { authService } from '../../services/authService';
import '../../styles/negotiation-chat.css';
export default function NegotiationChatModal({ open, onClose, listing, onAcceptOffer }) {
 const [messages, setMessages] = useState([]);
 const [text, setText] = useState('');
 const [amount, setAmount] = useState('');
 const [buyerId, setBuyerId] = useState('');
 const [error, setError] = useState('');
 const [busy, setBusy] = useState(false);
 const userId = authService.getCurrentUserId();
 const seller = listing?.seller?.isCurrentUser;
 async function load() { setMessages(await marketplaceService.getMessages(listing.reference)); }
 useEffect(() => {
  if (!open || !listing) return;
  let active = true;
  const refresh = () => marketplaceService.getMessages(listing.reference).then(data => { if(active) setMessages(data); }).catch(err => { if(active) setError(err.message); });
  setMessages([]); setBuyerId(''); setError(''); refresh();
  const timer = setInterval(refresh, 5000);
  return () => { active = false; clearInterval(timer); };
 }, [open, listing?.reference]);
 if (!open || !listing) return null;
 async function send(event) {
  event.preventDefault(); if(busy) return; setBusy(true); setError('');
  try { await marketplaceService.sendMessage(listing.reference, { text, ...(amount ? { amount: Number(amount) } : {}), ...(seller ? { buyerId: Number(buyerId) } : {}) }); setText(''); setAmount(''); await load(); }
  catch(err) { setError(err.message); } finally { setBusy(false); }
 }
 async function accept(message) {
  setBusy(true); setError('');
  try { const result = await marketplaceService.acceptOffer(listing.reference, message.id); await load(); if(!seller) { onAcceptOffer?.(result.price); onClose(); } else setError('Offer accepted. The buyer can now purchase at this price.'); }
  catch(err) { setError(err.message); } finally { setBusy(false); }
 }
 const buyers = [...new Map(messages.map(m => [m.buyerId, m.senderId === m.buyerId ? m.senderName : `Buyer ${m.buyerId}`])).entries()];
 return <div className="negotiation-modal" role="dialog" aria-modal="true" aria-label="Listing negotiation"><div className="negotiation-backdrop" onClick={onClose}/><div className="negotiation-card">
  <header className="negotiation-header"><h3>Negotiate: {listing.title}</h3><button className="icon-button" aria-label="Close negotiation" onClick={onClose}><X size={18}/></button></header>
  {seller && <label>Buyer conversation<select className="input" value={buyerId} onChange={e => setBuyerId(e.target.value)}><option value="">Choose a buyer</option>{buyers.map(([id,name]) => <option key={id} value={id}>{name}</option>)}</select></label>}
  {error && <p role="status" className="error-banner">{error}</p>}
  <div className="negotiation-messages">{messages.filter(m => !seller || String(m.buyerId) === buyerId).map(m => <div key={m.id} className={`chat-bubble ${m.senderId === userId ? 'mine' : 'theirs'}`}><p>{m.text}</p>{m.amount != null && <><strong>{m.amount} Credits</strong>{m.senderId !== userId && <Button disabled={busy} onClick={() => accept(m)}>Accept offer</Button>}</>}<small>{m.senderId === userId ? 'You' : seller ? m.senderName : 'Seller'} · {m.time}</small></div>)}{!messages.length && <p>No messages yet. Send a message or price offer to start.</p>}</div>
  <form className="negotiation-footer" onSubmit={send}><label>Message<input className="input" maxLength={2000} value={text} onChange={e => setText(e.target.value)}/></label><label>Price offer (optional)<input className="input" type="number" min="1" step="0.01" value={amount} onChange={e => setAmount(e.target.value)}/></label><Button type="submit" disabled={busy || (!text.trim() && !amount) || (seller && !buyerId)}>{busy ? 'Sending…' : 'Send'}</Button></form>
 </div></div>;
}

import { API_BASE_URL } from '../constants/api';
import { authService } from './authService';
export const PERSONAL_WORKSPACE = Object.freeze({ id: 'personal', name: 'Personal workspace', type: 'personal' });
export const organizationPath = (id, section = 'overview') => `/organizations/${encodeURIComponent(id)}/${section}`;
let accountId = null;
let organizations = [];
let loadError = '';
const notify = () => ['organizations_changed', 'org_updated', 'company_vaults_changed'].forEach(event => window.dispatchEvent(new CustomEvent(`vaultchain:${event}`)));
async function request(path = '', body) {
 const response = await fetch(`${API_BASE_URL}/organizations${path}`, { method: body === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${authService.getToken()}`, 'Content-Type': 'application/json' }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
 const data = await response.json();
 if (!response.ok) throw new Error(data.message || 'Organization request failed');
 return data;
}
async function mutate(id, action, body) {
 const current = accountId;
 const data = await request(`/${encodeURIComponent(id)}/${action}`, body);
 if (current === accountId) { organizations = data.organizations; notify(); }
 return data.result;
}
export const organizationService = {
 setAccount(id) { accountId = id; organizations = []; loadError = ''; notify(); },
 getOrganizations: () => organizations,
 getOrganization: id => organizations.find(o => o.id === id) || null,
 getError: () => loadError,
 async refresh() {
  const current = accountId;
  try { const data = await request(); if (current === accountId) { organizations = data.organizations; loadError = ''; notify(); } }
  catch (error) { if (current === accountId) { loadError = error.message; notify(); } throw error; }
 },
 getActiveWorkspace() {
  try { const saved = JSON.parse(sessionStorage.getItem(`authvault-workspace:${accountId}`)); const org = organizations.find(o => o.id === saved?.id); if (org) return { id: org.id, name: org.name, type: 'organization' }; } catch {}
  return PERSONAL_WORKSPACE;
 },
 setActiveWorkspace(workspace) {
  const org = workspace?.type === 'organization' && organizations.find(o => o.id === workspace.id);
  const next = org ? { id: org.id, name: org.name, type: 'organization' } : PERSONAL_WORKSPACE;
  sessionStorage.setItem(`authvault-workspace:${accountId}`, JSON.stringify(next));
  window.dispatchEvent(new CustomEvent('vaultchain:workspace_changed', { detail: next })); return next;
 },
 async searchUsers(query, orgId) {
  const { users } = await request(`/users?q=${encodeURIComponent(query)}`);
  const members = this.getOrganization(orgId)?.members || [];
  return users.map(u => ({ ...u, isAlreadyMember: members.some(m => String(m.id) === String(u.id)) }));
 },
 async createOrganization(body) { const { organization } = await request('', body); organizations = [organization, ...organizations]; notify(); return organization; },
 getCompanyVaults(id) { return this.getOrganization(id)?.companyVaults || []; },
 createCompanyVault: (id, body) => mutate(id, 'createCompanyVault', body),
 toggleCompanyVaultLock: (id, vaultRef, password) => mutate(id, 'toggleCompanyVaultLock', { vaultRef, password }),
 inviteMember: (id, body) => mutate(id, 'inviteMember', body),
 removeMember: (id, memberId) => mutate(id, 'removeMember', { memberId }),
 depositTreasury: (id, amount, note) => mutate(id, 'depositTreasury', { amount, note }),
 withdrawTreasury: (id, amount, recipient, note) => mutate(id, 'withdrawTreasury', { amount, recipient, note }),
 batchDisburseDividends: (id, amount) => mutate(id, 'batchDisburseDividends', { amount }),
 addListing: (id, body) => mutate(id, 'addListing', body),
 updateListingPrice: (id, listingId, newPrice) => mutate(id, 'updateListingPrice', { listingId, newPrice }),
};

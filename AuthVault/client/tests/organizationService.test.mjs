import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
const values = new Map();
globalThis.sessionStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
globalThis.window = new EventTarget();
let token = 'alice';
let response;
globalThis.fetch = async (url, options) => { assert.equal(options.headers.Authorization, `Bearer ${token}`); return response(url, options); };
globalThis.testToken = () => token;
const source = (await readFile(new URL('../src/services/organizationService.js', import.meta.url), 'utf8'))
 .replace("import { API_BASE_URL } from '../constants/api';", "const API_BASE_URL = '/api';")
 .replace("import { authService } from './authService';", 'const authService = { getToken: globalThis.testToken };');
const { organizationService: service } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
const ok = body => ({ ok: true, json: async () => body });

test('server records hydrate the workspace, and account changes clear them', async () => {
 service.setAccount('alice');
 response = async () => ok({ organizations: [{ id: 'one', name: 'Studio' }] });
 await service.refresh();
 service.setActiveWorkspace({ id: 'one', name: 'Forged name', type: 'organization' });
 assert.equal(service.getActiveWorkspace().name, 'Studio');
 token = 'bob'; service.setAccount('bob');
 assert.deepEqual(service.getOrganizations(), []);
 assert.equal(service.getActiveWorkspace().type, 'personal');
});

test('rejected mutations do not change the cache or report success', async () => {
 service.setAccount('bob');
 response = async () => ({ ok: false, json: async () => ({ message: 'Only the owner can create a vault' }) });
 await assert.rejects(service.createCompanyVault('one', { name: 'Vault' }), /Only the owner/);
 assert.deepEqual(service.getOrganizations(), []);
});

test('an old account request cannot populate the new account cache', async () => {
 let finish;
 token = 'alice'; service.setAccount('alice');
 response = () => new Promise(resolve => { finish = resolve; });
 const pending = service.refresh();
 token = 'bob'; service.setAccount('bob');
 finish(ok({ organizations: [{ id: 'private', name: 'Alice only' }] }));
 await pending;
 assert.deepEqual(service.getOrganizations(), []);
});

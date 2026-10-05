import { API_BASE_URL } from '../constants/api';
import { authService } from './authService';

async function request(path, options = {}) {
	const headers = { ...options.headers };
	const token = authService.getToken();
	if (token) {
		headers.Authorization = `Bearer ${token}`;
	}
	const response = await fetch(`${API_BASE_URL}${path}`, {
		...options,
		headers,
	});
	const data = await response.json();
	if (!response.ok) {
		throw new Error(data.message || 'Blockchain request failed');
	}
	return data;
}

export const blockchainService = {
	getBlocks: (limit = 50) => request(`/blockchain/blocks?limit=${limit}`),
	getBlockByIndex: (index) => request(`/blockchain/blocks/${index}`),
	getBlocksForAsset: (assetId) => request(`/blockchain/asset/${assetId}`),
	getStats: () => request('/blockchain/stats'),
	getFlow: (assetId, hash) =>
		request(`/blockchain/flow/${assetId}${hash ? `?hash=${encodeURIComponent(hash)}` : ''}`),
};

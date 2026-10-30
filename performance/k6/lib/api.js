import { apiPrefix } from './config.js';

// Finance has its own API root (confirmed from the Finance HARs); WebAPI uses the tenant's apiPrefix.
export const financeApiPrefix = '/Finance/api';

// Turns an endpoint of lib/generated/endpoints.generated.js ({ host, path }) into the request path.
// The registry is generated from the Java WebApiEndpoints, so a path is never written twice.
export function pathOf(endpoint) {
  const prefix = endpoint.host === 'FINANCE_API' ? financeApiPrefix : apiPrefix;
  return `${prefix}${endpoint.path}`;
}

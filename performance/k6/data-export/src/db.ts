import { Kysely, PostgresDialect } from 'kysely';
import { Pool } from 'pg';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import type { DB } from './types.js';

const __dirname = dirname(fileURLToPath(import.meta.url));

interface TenantDbConfig {
  host: string;
  port: number;
  database: string;
  user: string;
  password: string;
  // Azure Postgres Flexible Server (e.g. qaprod) requires TLS; existing
  // on-prem tenants (qafeature, qahbros, idealdb) don't use it - per-tenant
  // opt-in rather than a global default so those keep working unchanged.
  ssl?: boolean;
}

function loadTenantConfig(tenant: string): TenantDbConfig {
  const path = join(__dirname, 'config', `${tenant}.json`);
  return JSON.parse(readFileSync(path, 'utf-8'));
}

// One Kysely instance per tenant, cached for the lifetime of the process -
// this tool is a one-shot script (query, assemble JSON, exit), so a small
// in-memory cache is enough; no need for a long-lived connection pool.
const instances = new Map<string, Kysely<DB>>();

export function getDbForTenant(tenant: string): Kysely<DB> {
  const cached = instances.get(tenant);
  if (cached) return cached;

  const config = loadTenantConfig(tenant);
  const dialect = new PostgresDialect({
    pool: new Pool({
      host: config.host,
      port: config.port,
      database: config.database,
      user: config.user,
      password: config.password,
      max: 5,
      ssl: config.ssl ? true : undefined,
    }),
  });

  const db = new Kysely<DB>({ dialect });
  instances.set(tenant, db);
  return db;
}

export async function closeAll(): Promise<void> {
  for (const db of instances.values()) {
    await db.destroy();
  }
  instances.clear();
}

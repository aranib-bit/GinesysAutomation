import { SharedArray } from 'k6/data';
import { usersFileName } from './config.js';

export const credentials = new SharedArray('credentials', function () {
  return JSON.parse(open(`../data/users/${usersFileName}`));
});

export function assignCredential(vuId) {
  if (credentials.length === 0) {
    throw new Error(
      `No credentials loaded for tenant - populate data/users/${usersFileName}`
    );
  }
  return credentials[(vuId - 1) % credentials.length];
}

const PLACEHOLDER_PATTERN = /^REPLACE_WITH_/i;

// Call from a scenario's setup() so a misconfigured pool fails the whole run
// immediately instead of two VUs colliding on the same login mid-test, or
// silently hammering the real server with leftover scaffold placeholders
// (seen live: rows left as "REPLACE_WITH_PERF_USER_N" fail every login with a
// genuine bad-credentials error, indistinguishable from a real typo until you
// read the row).
export function assertPoolCapacity(maxConcurrentVUs) {
  if (credentials.length < maxConcurrentVUs) {
    throw new Error(
      `Credential pool for tenant has ${credentials.length} user(s) but this scenario needs ` +
        `${maxConcurrentVUs} concurrent VUs. Add more rows to data/users/${usersFileName} - ` +
        `each concurrent VU must own a distinct login, or the app will force a kill-session conflict.`
    );
  }

  // Only the rows assignCredential() will actually hand to VUs 1..maxConcurrentVUs
  // matter here (with maxConcurrentVUs <= credentials.length, that's rows 0..N-1,
  // no wraparound) - a placeholder sitting in an unused tail row shouldn't block
  // a smaller run from using the real credentials that precede it.
  const placeholderRows = credentials
    .slice(0, maxConcurrentVUs)
    .map((c, i) => ({ row: i + 1, username: c.username }))
    .filter((c) => PLACEHOLDER_PATTERN.test(c.username));

  if (placeholderRows.length > 0) {
    const rows = placeholderRows.map((c) => `row ${c.row} ("${c.username}")`).join(', ');
    throw new Error(
      `data/users/${usersFileName} still has unreplaced placeholder credentials among the rows this ` +
        `run needs: ${rows}. Fill these in with real dedicated accounts, or lower TARGET_VUS/SMOKE_VUS ` +
        `to stay within the real credentials configured before the first placeholder row.`
    );
  }
}

// Central, reusable k6 "ramping-vus" stage builder - the one place traffic-
// shape logic lives for the whole project, instead of every scenario hand-
// rolling its own `stages` array (which is what scenarios/performance/load.js used to
// do before migrating to this module).
//
// Pure function: does NOT read __ENV itself. Each scenario reads its own env
// vars (matching the project-wide convention of every scenario doing
// `Number(__ENV.X) || default` at module scope) and passes the parsed values
// in here - keeps this module independently reusable/testable and lets a
// caller hardcode a value without it being overridable via env var.
//
// Supports two shapes with one function:
//  - burst + ramp-up + ramp-down (no hold): scenarios/performance/testLoginLogout.js's
//    ramp mode - e.g. 50 VUs in 1m, then up to target over 2m, then down
//    over 2m.
//  - ramp-up + hold + ramp-down (no burst): scenarios/performance/load.js's existing
//    shape - straight 0->target, hold at target, then down to 0.
export function buildRampingVusProfile({
  targetVUs,
  burstVUs,
  burstDuration,
  rampUpDuration,
  holdDuration,
  rampDownDuration,
} = {}) {
  const stages = [];

  if (burstVUs && burstVUs > 0 && burstVUs < targetVUs) {
    stages.push({ duration: burstDuration, target: burstVUs });
    stages.push({ duration: rampUpDuration, target: targetVUs });
  } else {
    stages.push({ duration: rampUpDuration, target: targetVUs });
  }

  if (holdDuration) {
    stages.push({ duration: holdDuration, target: targetVUs });
  }

  stages.push({ duration: rampDownDuration, target: 0 });

  return {
    executor: 'ramping-vus',
    startVUs: 0,
    stages,
  };
}

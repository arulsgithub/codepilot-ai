/**
 * Production environment configuration.
 *
 * Replace `apiBaseUrl` with the deployed backend origin at build/deploy time
 * (e.g. via CI environment substitution). Do not commit real production
 * hosts here if this repo is public — prefer injecting via a build step.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'https://api.codepilot.example.com',
};

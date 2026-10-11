export const environment = {
  production: false,
  // Empty: every request becomes relative, so the dev proxy forwards it and the
  // browser only ever sees localhost:4200 - one origin, so no CORS and the session
  // cookie is sent normally. An absolute http://localhost:8080 here would bypass
  // the proxy entirely and bring both problems back.
  apiBaseUrl: ''
};
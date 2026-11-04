# Gatekeeper Admin Console

Vite + React dashboard for the Gatekeeper route-management API.

## Run
```bash
npm install
cp .env.example .env
npm run dev
```
Open http://localhost:5173. Start the Spring Boot backend first. Default local demo credentials are admin / change-me-now; change them using APP_ADMIN_USER and APP_ADMIN_PASSWORD before exposing the backend. Configure GATEWAY_SHARED_KEY on the backend and send the same value in X-Gateway-Key for forwarded requests.

The UI supports listing, creating, enabling/disabling and deleting routes. It is a development console, not a production admin portal.

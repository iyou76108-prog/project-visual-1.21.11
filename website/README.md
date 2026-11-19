# PROJECT VISUAL website — private development scaffold

This directory contains the **unpublished** website/API prototype. No production site has been deployed and no public URL is active.

## Included foundations

- Registration with password hashing and mandatory email verification.
- Browser sessions and account dashboard.
- One-time website-to-Minecraft pairing code. In-game use /pv login and enter the dashboard code; the site password is never typed into Minecraft chat.
- Minecraft bearer tokens, friend requests, presence, profile cosmetics and turn-based chess/tic-tac-toe room APIs.
- Cosmetic case catalogue with visible drop chances, inventory and optional Stripe Checkout.
- Checkout is disabled until Stripe secrets and a signed webhook are configured.

## Local development

Requires Node.js 24+ (Node.js 26 LTS is suitable), PostgreSQL and SMTP credentials for real email delivery.

    cd website
    cp .env.example .env
    npm install
    npx prisma migrate dev --name initial
    npm run db:seed
    npm run dev

Set APP_URL=http://localhost:3000. Without SMTP, the verification URL is printed to the local development server console only.

The Minecraft client defaults to http://localhost:3000. To point it at a future HTTPS deployment, edit config/project_visual_server.properties:

    api_url=https://your-domain.example

Protect the Minecraft config directory because it contains the account token.

## Production work still required

Before deployment: select a domain and hosting/database provider; configure SMTP, HTTPS, Stripe and its webhook; add API rate limits, abuse monitoring, account deletion/export, privacy/terms and legal review of paid random rewards and regional age requirements; add token revocation and full integration/security tests. This scaffold is not launch-ready.

# Notes Keep Local — Landing Page

Static, dependency-free product landing page for Notes Keep Local.

## Preview locally

```bash
cd landing-page
python3 -m http.server 5567
```

Then open <http://localhost:5567>.

## Deploy to Vercel

Import the Git repository in Vercel and use these project settings:

- **Root Directory:** `landing-page`
- **Framework Preset:** `Other`
- **Build Command:** leave empty
- **Output Directory:** leave empty
- **Install Command:** leave empty

Vercel reads `vercel.json` from this directory and serves the static files directly. Every push to the connected production branch can deploy automatically; pull requests receive preview deployments when the Vercel Git integration is enabled.

## Files

- `index.html` — semantic page structure and product content
- `styles.css` — responsive layout, animations, and reduced-motion support
- `app.js` — scroll reveal, interactive checklist, spotlight, and product tilt
- `vercel.json` — clean URLs and production security headers

The page links to this repository and its releases. It has no package dependencies, environment variables, server functions, or build step.

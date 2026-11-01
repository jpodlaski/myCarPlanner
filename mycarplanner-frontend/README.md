# myCarPlanner frontend

React + Vite single-page app for myCarPlanner. The root [README](../README.md) covers the
full stack, the demo walkthrough and the API.

## Commands

```bash
npm install
npm run dev      # Vite dev server on :5173
npm run build    # production build to dist/
npm run lint     # ESLint
```

The dev server expects the API on `http://localhost:8080` (see `src/services/api.js`).
Running the whole stack from the repo root with `docker compose up` is usually simpler:
the frontend container bind-mounts this directory, so UI edits hot-reload.

## Layout

| Path | Purpose |
|------|---------|
| `src/pages/` | Route components: `Landing`, `auth/`, `client/`, `employee/`, `admin/` |
| `src/components/` | Shared UI: page shells (`AppShell`, `AuthLayout`, `GlobalNav`, `LocalNav`, `SiteFooter`), `ExpandableRow`, `TextField`, pickers |
| `src/theme/` | Light/dark theme provider, hook and toggle |
| `src/services/` | Axios client and localStorage session helpers |
| `src/constants/` | Role names, workshop details, model copy, nav links |
| `src/hooks/` | Page title and focus management |
| `src/utils/` | Date/time and formatting helpers |
| `public/` | `logo.png` (source logo), car photos, favicon; `public/img/` holds the web-sized derivatives |

## Theming

`src/index.css` defines both palettes as CSS custom properties (`:root` = light,
`[data-theme="dark"]` = dark). Light is the default. `src/theme/ThemeProvider.jsx`
persists a toggle to `localStorage["mycarplanner_theme"]`. `index.html` applies it
before first paint.

The look follows apple.com: SF Pro type stack, `#1d1d1f` / `#f5f5f7` neutrals, `#0071e3`
pill buttons, a translucent global nav and a sticky local nav per area. Every text colour
pair in both palettes meets WCAG 2.2 AA contrast.

`Logo.jsx` renders crops of `public/logo.png` (transparent, so it works on both themes):
`img/logo-lockup.png` (wings + wordmark) and `img/logo-emblem.png` (wings only, global nav).
Car photos are served from `img/<model>-800.jpg` / `-1600.jpg`. Regenerate these if the
source PNGs change.

## Accessibility

Pages target WCAG 2.2 AA and valid HTML (W3C Nu validator): a skip link, landmarks, one
`h1` per view, a per-route `document.title`, real `<label>`s with `autocomplete` on every
field, `role="alert"` / `role="status"` for messages, visible `:focus-visible` rings,
44px touch targets and `prefers-reduced-motion` support. Keep toggle buttons free of
block elements (`<div>`, `<p>`); `ExpandableRow` exists for that.

Routes under `/client`, `/employee` and `/admin` are gated client-side by `PrivateRoute`.
The API enforces access on every call, so the guard is UX only.

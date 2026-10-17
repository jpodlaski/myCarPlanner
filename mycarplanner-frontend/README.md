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
| `src/components/` | Shared UI: `Logo`, `ThemeToggle`, pickers, forms |
| `src/theme/` | Light/dark theme provider, hook and toggle |
| `src/services/` | Axios client and localStorage session helpers |
| `src/constants/` | Role names and workshop details |
| `src/utils/` | Date/time helpers |
| `public/` | `logo.svg`, `logo-inverse.svg`, favicon |

## Theming

`src/index.css` defines both palettes as CSS custom properties (`:root` = dark,
`[data-theme="light"]` = light). `src/theme/ThemeProvider.jsx` follows the OS setting until
the user toggles, then persists the choice to `localStorage["mycarplanner_theme"]`.
`index.html` applies it before first paint.

Routes under `/client`, `/employee` and `/admin` are gated client-side by `PrivateRoute`.
The API enforces access on every call, so the guard is UX only.

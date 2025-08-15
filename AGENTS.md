# Agent Guidelines for gen3d

## Build Commands
- **Development**: `npm run dev:all` (watches all builds: viewer, ai, functions)
- **CSS Development**: `npm run dev:css:viewer` (watches Tailwind CSS)
- **Release Viewer**: `npm run release:viewer`
- **Firebase Emulators**: `npm run emulators` or `npm run emulators:keep-state`
- **Shadow-cljs REPL**: `shadow-cljs clj-repl` (nREPL on port 25000)

## Code Style & Structure
- **Namespaces**: Use kebab-case with dots (e.g., `gen3d.viewer.core`, `gen3d.functions.ai`)
- **Imports**: Group by type - core libs first, external deps, then project namespaces
- **Functions**: Use kebab-case naming, prefer pure functions
- **State Management**: Use Reagent atoms, centralized in `gen3d.viewer.state`
- **Error Handling**: Use `try/catch` blocks, log errors appropriately
- **Firebase**: Use `anr.fire.*` namespace utilities for Firebase operations

## Architecture
- **Frontend**: ClojureScript + Reagent + Tailwind CSS + DaisyUI
- **Backend**: Firebase Functions (ClojureScript compiled to Node.js)
- **AI Integration**: Genkit with Gemini API
- **Routing**: Reitit for client-side routing
- **Build Tool**: Shadow-cljs with separate builds for viewer, functions, and ai modules
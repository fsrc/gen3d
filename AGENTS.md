# Agent Guidelines for gen3d

## Build Commands
- `npm run dev:all` - Watch all builds (viewer, ai, functions)
- `npm run dev:css:viewer` - Watch CSS compilation for viewer
- `npm run release:viewer` - Production build for viewer
- `shadow-cljs watch <build-id>` - Watch specific build (viewer, ai, functions)
- `shadow-cljs release <build-id>` - Release specific build
- `shadow-cljs compile <build-id>` - One-time compile

## Firebase/Emulator Commands
- `npm run emulators` - Start Firebase emulators
- `npm run emulators:keep-state` - Start emulators with persistent state

## Code Style Guidelines
- **Namespaces**: Use kebab-case (e.g., `gen3d.viewer.core`, `anr.ui.layout`)
- **Functions**: Use kebab-case with descriptive names (e.g., `template-areas`, `fs->clj`)
- **Imports**: Group by type - core libs first, then project namespaces
- **Indentation**: 2 spaces, align function parameters vertically
- **Threading**: Use `->>` for data transformations, `->` for object operations
- **Error Handling**: Use explicit exceptions with descriptive messages
- **File Structure**: Separate concerns - core.cljs for main logic, separate files for views/utils

## Project Structure
- `src/main/gen3d/` - Main application code (viewer, functions)
- `src/main/anr/` - Shared library code (fire, ui, effects, js utils)
- `dist/` - Compiled output (viewer, functions)
- Shadow-CLJS builds: `:viewer` (browser), `:functions` (node), `:ai` (node)
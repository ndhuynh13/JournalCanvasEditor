# Journal Canvas Editor

Android coding test implementation for a journal/freeform canvas editor.

## Features

- Add text and image objects.
- Select, move, resize, rotate, flip, hide/show, lock/unlock, and delete objects.
- Drag empty canvas space to pan the viewport.
- Pinch empty canvas space to zoom around the gesture focus.
- Pinch a selected unlocked object to resize it; text objects scale both bounds and text size.
- Custom selected state with resize/rotate/delete/flip handles.
- Locked objects can still be selected, but only the unlock action is available.
- Local persistence with atomic JSON save/restore.
- Undo/redo with bounded history.
- Layer ordering through the Layers dialog.
- Snap guidelines for object/canvas center and edge alignment.
- Export canvas as PNG to `Pictures/JournalCanvasEditor`.
- Dark mode colors via `values-night`.

## Tech Stack

- **Language:** Kotlin
- **UI:** XML + ViewBinding + Custom View (`JournalCanvasView`)
- **Architecture:** MVI/Redux style: `EditorAction` -> `EditorReducer` -> `StateFlow`
- **DI:** Hilt (`@HiltViewModel`, `@Inject constructor`, `EditorModule`)
- **Async:** Coroutine + `StateFlow`
- **Persistence:** Gson for document serialization, atomic file write
- **Image:** ExifInterface for orientation, `LruCache` for bitmap caching

## Architecture

```text
domain/ -> Pure editor logic (Actions, Reducer, Models, SnapEngine, HistoryStore)
data/   -> Persistence and I/O (StateStore, ImageStore, Exporter)
render/ -> Canvas rendering, selection rendering, viewport transforms, hit testing, and gesture handling
ui/     -> Presentation (Activity, ViewModel, dialogs)
di/     -> Hilt DI module
```

The editor follows a unidirectional data flow:

1. UI dispatches `EditorAction` events to the `ViewModel`.
2. `ViewModel` applies actions through `EditorReducer`.
3. The new `EditorState` is emitted via `StateFlow`.
4. `JournalCanvasView` observes state and re-renders the document.

`JournalCanvasView` is a single custom-rendered canvas. It draws the document, grid, and snap guides while delegating selection UI to `SelectionRenderer`. Viewport math is isolated in `ViewportTransformer`, while object and handle hit testing is handled by `ObjectHitTester` with inverse matrices. The view caches visible objects in draw order and rebuilds a `CanvasObjectSpatialIndex` quadtree only when object data or canvas bounds change, avoiding repeated sorting and full-list hit testing during touch events.

Persistence is handled by `CanvasStateStore`, which writes through a temporary file before replacing the current JSON document. `ImageStore` copies picked images into app storage and reads orientation metadata. `CanvasExporter` renders the logical document to a bitmap and saves it with MediaStore.

## Tradeoffs

- **Custom View vs View-per-object:** A single rendered canvas scales better with many objects and gives precise control over gesture conflict, hit testing, export, and selection rendering.
- **Cached render order + quadtree hit testing:** Visible objects are sorted once when object data changes. Hit testing queries a quadtree for nearby candidates, then uses precise inverse-matrix checks and z-index comparison to choose the topmost object.
- **Command history:** Undo/redo stores compact editor commands for common edits, such as add/delete object, object updates, layer reorder, and viewport changes. Complex unknown document changes can still fall back to a document replace command.
- **StaticLayout caching:** Text layouts are cached by a composite key `(text, size, width, style, color)` to avoid layout allocation during `onDraw()`.
- **Snap model:** Snap guidelines use logical object bounds. This keeps the implementation predictable, but rotated-edge snapping would require a more advanced geometry model.
- **Export resolution:** Export renders the logical document size, not the current viewport, so the output is stable regardless of zoom/pan.

## Discussion Questions

**If the canvas contains 1000 objects, what would become the first bottleneck?**  
Rendering and bitmap/text layout pressure become the first bottlenecks. This implementation avoids 1000 Android Views, caches text layout, and uses a quadtree to reduce object hit-test candidates before exact matrix checks.

**How would you handle very large images?**  
Selected images are copied to internal storage and decoded through a bounded bitmap cache with downsampling. A production version should generate editing thumbnails/previews and keep full-resolution images only for export.

**How would you optimize undo/redo memory usage?**  
Use command diffs instead of full document snapshots. This implementation stores before/after object payloads for transforms/text/lock/visibility, object payloads for add/delete, ID order for layer changes, and viewport before/after for pan/zoom. Image files are referenced by path, not duplicated in memory.

**How would the architecture change if cloud sync was added?**  
Add a repository layer with local-first persistence, document versioning, sync state tracking, and conflict resolution. The ViewModel would continue to observe a `StateFlow` while the repository reconciles local and remote edits asynchronously.

**How would you support realtime collaboration?**  
Represent edits as ordered operations and synchronize them through a realtime channel such as WebSocket or Firebase Realtime Database. Conflict handling would use server ordering, Operational Transformation, or CRDT-style object properties depending on consistency requirements.

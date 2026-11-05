# Generation scenarios

## Graph lifetime and reuse

`VisualizerState.generatedGraph` owns the active generated topology independently
of playback frames. Changing algorithm or exploration mode resets costs, paths,
visited/frontier flags and playback, while retaining the graph and endpoints.
The viewport is preserved for these switches. `ResetSimulation` follows the same
rule. Selecting an already-active algorithm or mode is a no-op.

`InitialSimulationFrameFactory.create(graph, knowledgeMode)` builds a fresh initial
frame without invoking the generator. Its arrays and ID sets are fresh per reset,
so returning to local exploration cannot retain global knowledge or search results.
This reset is O(nodes + edges); it does not rerun layout, triangulation or edge selection.

`GenerateGraph` uses a session-local `GeneratedGraphCache`, keyed by resolved
generation options (including the seed). It retains only the most recently generated
graph for each space mode, so memory does not grow with every configuration tried.
An explicit Generate with identical options reuses that graph and resets simulation
frames. Changed settings take effect only on Generate. Switching between grid and
continuous space restores the last generated graph for that mode, or shows an empty
view until one is generated. It never silently runs generation during a mode switch.

This cache avoids repeated work, but initial generation is still synchronous.
Its expensive paths include triangulation, geometric edge checks, and the all-pairs
candidate fallback. Playback also still materializes every frame with `generateAll()`.
Those costs remain separate from graph reuse; this change does not claim to make
first-time generation or very large simulation histories inexpensive.

## Presets

The original layout remains the default. Presets change scenario settings while
keeping the current space, size, simple/advanced mode, and seed:

| Preset | Purpose |
| --- | --- |
| Classic | Original corner-to-corner route and gently varied geometric costs. |
| Side branches | A central start, a goal on the right, and sparse connections; nodes also lie behind the start. |
| Rough terrain | 20% of edges cost 20 times their normal weight. |
| Cheap detours | A central start with 40% of edges costing one tenth of their normal weight. |
| Short trip | Nearby interior endpoints in a larger surrounding map. |

Presets are starting points, not guarantees about which path an algorithm chooses
or how many nodes it explores. Change the seed to try another instance. Editing
controls changes the preset label to Custom when the settings no longer match.

Simple mode offers map size, start/goal choices, Gentle/Varied/Extreme terrain,
density, and planarity. Advanced mode adds exact sizes, relative endpoint
coordinates, individual cost parameters, and the existing geometry constraints.
Entering advanced mode preserves the resolved simple map size.

## Endpoints

Across map uses the original minimum/maximum x+y pair. From center aims from the
middle to the right edge, leaving space opposite the goal. Nearby selects two
interior positions; Random selects two distinct nodes reproducibly. Custom
coordinates are fractions of the graph's bounding rectangle and snap to the
nearest distinct nodes. They work on both grids and continuous layouts.

`Graph.startNodeId` and `goalNodeId` preserve the selection. `StartGoalSelector`
uses them for both preview and execution, with the original corner rule as a
fallback for graphs constructed without endpoints. Editing generator settings
does not change the endpoints of an already-generated graph.

Directed or explicitly disconnected graphs can have an unreachable goal.

## Weights

Ordinary variation controls the existing geometric cost jitter. Each edge then
independently has the configured chance of receiving an extreme cost. The cheap
share determines how many of those extreme edges divide their base cost by the
factor; the others multiply it. Factors range from 1 to 100. Weights remain
strictly positive and are rounded to two decimal places (minimum 0.01).

Layout, base weights, extremes, and endpoint selection use independent seeded
random streams where appropriate. Changing only endpoint placement or terrain
extremes leaves the topology unchanged, making comparisons easier. A chance of
zero preserves the original weights.

## Larger graphs

Advanced mode allows up to 100,000 total nodes in either space. Grid dimensions can
each reach 50,000 (the other dimension must be at least 2), subject to that total. Invalid sizes are shown in the dialog and
generation is disabled; the backend validates them too. Simple mode retains its
smaller teaching-oriented size range.

This is an absolute maximum, not a performance promise: generation and full-frame simulation are synchronous,
and cached simulation frames consume memory proportional to graph size times
the number of steps. Dense non-planar graphs can be considerably more expensive.
Very large configurations may take substantial time or exhaust memory.

Continuous sampling uses spatial buckets for separation checks, scales spacing
and edge clearance for large maps, and falls back to a complete jittered grid
when rejection sampling saturates. Larger maps need zooming to inspect labels;
screen-sized clearance cannot stay constant while fitting thousands of nodes.

## Size and spacing

The left sidebar's expandable **Size and spacing** section separates two types of
control. Display settings apply immediately to the current graph and playback:

- Node size, edge thickness, and label size can be adjusted independently.
- **Balanced** restores defaults; **Overview** uses smaller symbols and thinner lines.
- Auto-sizing estimates on-screen spacing in O(n), reducing node and line sizes
  in dense views and restoring their normal size when zooming in.
- Crowded labels hide automatically without changing annotation toggles. Zoom in
  or select an item to show its enabled labels; disable automatic hiding for
  manual control. Start, goal, rover, and selected nodes retain a visible minimum.
- Hit testing follows the displayed node sizes, with a small minimum click target.

**Node spacing** changes the sampling minimum for the next continuous graph.
It appears both in the sidebar and generation dialog. Higher values distribute
nodes more evenly; lower values allow tighter clusters. The minimum scales with
node count and is capped to fit all requested nodes. Advanced mode displays that
minimum as a percentage of map width. Overlap mode disables this constraint.
Grid centers are already evenly spaced, so grid overviews use the display controls.

Changing display sizes never changes coordinates, costs, or cached algorithm frames.
Increasing world-space distances alone would not improve a fit-to-screen overview:
fitting the larger bounds would shrink them back into the same canvas area.

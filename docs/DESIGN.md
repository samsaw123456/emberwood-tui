# Terminal presentation

Emberwood TUI is the same forest adventure seen through a terminal window. Its
visual identity comes from the original Sunny Land fox, forest tiles, animated
critters, eight landscape palettes and six guardian silhouettes. The terminal
owns readable lettering; there is no pixel-text menu or decorative landing page.

`TerminalRenderer` draws the existing world art into an offscreen Java image at
two pixels per terminal cell, then encodes each pair with foreground/background
color and the upper-half-block character `▀`. Modern terminals retain 24-bit RGB;
Apple Terminal automatically uses its supported 256-color palette. Set
`EMBERWOOD_TUI_COLOR=256` or `truecolor` to override detection. Palette conversion
chooses the nearest cube or grayscale color. This needs only a UTF-8 terminal
with ANSI color, and never opens a graphics window or requires a terminal image
protocol. Repeated colors reuse ANSI state to reduce output. Raster dimensions
follow the terminal's available playfield. A 768 × 432 crop gives the original
fox about 50% more horizontal detail than fitting the entire desktop viewport.
Horizontal tracking reuses the source camera's easing, bounded so a retry cannot
leave the fox off screen. The vertical crop follows only high jumps; ordinary
jump arcs visibly rise through the scene. Camera bounds include the far-right
exit. This changes presentation only: physics, collision geometry and level data
remain in their original world units and 1152 × 648 reference viewport.

The fox remains orange against green and blue forest silhouettes. Pale green
platform tops indicate solid footing; aqua indicates moving platforms, amber
indicates crumbling platforms, and light red marks spike beds. Campfire flame,
acorn gold, gate glow and guardian weak points keep the original game meanings.
World sign lettering, camp numbers, equipment names and gate instructions are
handled as terminal text instead of being reduced to unreadable colored dots.

A larger terminal gives more sprite detail. A 120-column terminal is a useful
default; native text and the readable control legend take priority at small
sizes. Terminal fonts remain the user's own monospaced font. Bundled source fonts
are retained for asset provenance and any supporting rendered art, not forced on
the user's terminal.

No assets were regenerated or recolored to create a different visual theme. The
source images and their licenses remain unchanged. Half-block reduction is a
presentation transform only; all state and timing come from `Game`.

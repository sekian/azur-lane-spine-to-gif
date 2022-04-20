# AzurLane-getChibiFrames
Extract individual frames for each animation from the Azur Lane spine files.

All the extracted chibi animations will be ready to be built as a .gif or any other media format.

### GIF creation example with ImageMagick

`convert -delay (10/3) -dispose Background -loop 0 "*.png" output.gif`

### GIF creation example with FFmpeg

``ffmpeg -v warning -i %04d.png -filter_complex "split [a][b]; [a] palettegen=reserve_transparent=on:transparency_color=ffffff [p]; [b][p] paletteuse, fps=30" output.gif``

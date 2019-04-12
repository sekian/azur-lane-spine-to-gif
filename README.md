# AzurLane-getChibiFrames
Create a sequence of transparent png or gif images for each animation from the Azur Lane spine files.

All the chibi animation sequences will be ready to be built as a .gif or any other media file.

### GIF creation example with ImageMagick

`convert -delay 5 -dispose Background -loop 0 "*.gif" output.gif`

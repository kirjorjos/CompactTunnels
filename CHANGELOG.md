# Changelog

- Added capability tunnel
  - Added to creative menu
  - Added item support
  - Item form holds side last set in NBT
- Fixed world save bug causing chunks to never finish unloading
- Set tunnels to claim a side of the machine for a 1:1 correspondence
  - Picks first free side on place, failing to place if no free sides remaining
- Ctrl + mouse wheel cycles through free sides when looking at a tunnel
  - Direction depends on which way you scroll
- Shift + left-click with an empty hand to pick up a tunnel
- Creative middle-click grabs a blank tunnel, ctrl + middle-click copies its side data

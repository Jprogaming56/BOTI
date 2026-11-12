# Bigger On The Inside (AiT add-on)

Client-side add-on for Adventures in Time (AIT Forger), Forge 1.20.1. When a TARDIS door is open and
landed, the doorway shows a huge hall in the style of AiT's Renaissance interior (sand-stone block walls with pale blue windows, a sand and slate tiled floor, a hanging lantern cube, a white console with a glass time rotor, and a police box far away) instead of
nothing. The picture is ray traced from where you stand, so moving around outside makes the hall shift with
real depth, like a big space behind a small door. No Immersive Portals, no special GPU needs.

It only draws; nothing changes in the world, so it is safe on any server (the server does not need it).

While you test, a small status line shows above the hotbar near a TARDIS (`debugOverlay`, turn it off later).

Config (`biggerinside-client.toml`): size and position of the doorway picture, range, brightness, animation.
- Picture on the wrong side of the box: set `facingOffsetDegrees` to 90, 180 or 270.
- Picture hidden inside the box or floating in front of it: change `planeOffset`.
- The add-on switches off AiT's own doorway view while running (`disableAitBoti`, in memory only, AiT's
  config file is not touched). Set it to false if you want AiT's own view to draw too.

Build: upload the contents of this folder to a GitHub repo (the `.github/workflows/build.yml` file must be there),
and download the jar from the Actions run.

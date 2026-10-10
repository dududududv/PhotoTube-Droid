# Large-image decoder fixtures

These generated RGB PNG test patterns contain horizontal color bands, with no user media or external licensing dependency:

- `large-camera.png`: 8192 × 6144 (50,331,648 pixels).
- `large-panorama.png`: 16384 × 2048 (33,554,432 pixels).

Each row has RGB `(y % 256, 90, 170)`, PNG filter 0 and zlib compression level 6. The small compressed files still require full dimension handling by the actual image decoder. `LargeImageDecodeTest` serves them over isolated HTTPS and renders the production `RemoteArtwork`, then checks the decoded Coil cache entries.

This verifies original-image decoding bounds, not photographic fidelity, network throughput, transient process/GPU peak memory, JPEG/HEIF/RAW support, or frame-time performance.

## Animated image

`two-frame.gif` is a generated GIF89a with two 1×1 global-palette frames (RGB 240,0,0 and 0,0,240), 300ms frame delays and infinite NETSCAPE2.0 looping. It contains no user media. AnimatedPhotoTest serves it as the original and checks actual rendered red/blue pixels, rather than inferring animation from successful decoding.

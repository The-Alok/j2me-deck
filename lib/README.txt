Optional extra libraries. Every .jar placed in this folder is added to the class path of the loader and of the games.

MP3 and other formats Java Sound cannot read:
  Drop an MP3 decoder that registers itself as a Java Sound service provider, for example
  mp3spi + jlayer + tritonus-share (LGPL). Games that play audio/mpeg then work without ffmpeg.
  (ffmpeg on the PATH is the other supported way; this folder is the no-install alternative.)

These libraries are not bundled because of their licenses; download them yourself.

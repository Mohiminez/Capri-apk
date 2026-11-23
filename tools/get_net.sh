#!/bin/sh
# Fetches the Stockfish 16 network for local builds (CI does this by itself).
mkdir -p app/src/main/assets && curl -fL -o app/src/main/assets/nn-5af11540bbfe.nnue https://github.com/official-stockfish/networks/raw/master/nn-5af11540bbfe.nnue && echo done

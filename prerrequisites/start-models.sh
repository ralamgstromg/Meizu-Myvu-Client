#!/bin/bash

export LD_LIBRARY_PATH="/vendor/lib64:/system/lib64:$PREFIX/lib:$LD_LIBRARY_PATH"

whisper-server \
  -m $HOME/whisper.cpp/models/ggml-small.bin \
  --host 127.0.0.1 \
  --port 8282 \
  -t 4 \
  -l es &


llama-server \
  -m $HOME/llama.cpp/models/gemma-4-E2B-it-qat-q4_0.gguf \
  --mmproj $HOME/llama.cpp/models/gemma-4-E2B-it-mmproj.gguf \
  --host 127.0.0.1 \
  --port 8080 \
  -t 6 \
  -c 4096 \
  -ngl 35 \
  --cache-reuse 256 &

wait
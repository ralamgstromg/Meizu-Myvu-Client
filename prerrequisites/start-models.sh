#!/bin/bash

export LD_LIBRARY_PATH="/vendor/lib64:/system/lib64:$PREFIX/lib:$LD_LIBRARY_PATH"

whisper-server \
  -m $HOME/whisper.cpp/models/ggml-small.bin \
  --host 127.0.0.1 \
  --port 8282 \
  -t 4 \
  -l es &


llama-server \
  -m $HOME/llama.cpp/models/MiniCPM-V-4_6-Q4_K_M.gguf \
  --mmproj $HOME/llama.cpp/models/mmproj-model-f16.gguf \
  --host 127.0.0.1 \
  --port 8080 \
  --api-key "123" \
  -t 6 \
  -c 4096 \
  -ngl 66 \
  --chat-template minicpm-v-4_6 &

wait
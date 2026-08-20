#!/bin/bash
# Script de instalación y arranque de Whisper.cpp (whisper-server) en Termux / Android

pkg update && pkg upgrade -y
pkg install git cmake clang build-essential wget curl ffmpeg pulseaudio termux-api -y

# Clonar y compilar whisper.cpp
if [ ! -d "whisper.cpp" ]; then
  git clone https://github.com/ggerganov/whisper.cpp.git
fi

cd whisper.cpp
cmake -B build -DWHISPER_NO_AVX=ON
cmake --build build --config Release -j$(nproc)

# Descargar modelo GGML recomendado (small: ~460MB)
bash ./models/download-ggml-model.sh small

# Iniciar whisper-server en el puerto 8282 (Servidor HTTP OpenAI compatible)
echo "Iniciando whisper-server en 127.0.0.1:8282..."
./build/bin/whisper-server \
  -m /data/data/com.termux/files/home/whisper.cpp/models/ggml-small.bin \
  --host 127.0.0.1 \
  --port 8282 \
  -t 4 \
  -l es
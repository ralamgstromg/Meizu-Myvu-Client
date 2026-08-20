#!/bin/bash
# Script de instalación y arranque de Whisper.cpp (whisper-server) en Termux / Android

pkg update && pkg upgrade -y
pkg install git cmake clang build-essential wget curl ffmpeg pulseaudio termux-api -y
pkg install ocl-icd opencl-headers opencl-clhpp -y

ln -sf /vendor/lib64/libOpenCL.so $PREFIX/lib/libOpenCL.so

git clone https://github.com/ggerganov/whisper.cpp.git

cd $HOME/whisper.cpp
rm -rf build

cmake -B build \
  -DGGML_OPENCL=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_FLAGS="-march=armv8.2-a+fp16+dotprod -O3" \
  -DCMAKE_CXX_FLAGS="-march=armv8.2-a+fp16+dotprod -O3"

cmake --build build --config Release -j$(nproc)

ln -s $HOME/whisper.cpp/build/bin/whisper-server $PREFIX/bin/

# Descargar modelo GGML recomendado (small: ~460MB)
bash ./models/download-ggml-model.sh small

# Iniciar whisper-server en el puerto 8282 (Servidor HTTP OpenAI compatible)
echo "Iniciando whisper-server en 127.0.0.1:8282..."
export LD_LIBRARY_PATH="/vendor/lib64:/system/lib64:$PREFIX/lib:$LD_LIBRARY_PATH"

whisper-server \
  -m $HOME/whisper.cpp/models/ggml-small.bin \
  --host 127.0.0.1 \
  --port 8282 \
  -t 4 \
  -l es
pkg update && pkg upgrade -y
pkg install git cmake clang wget -y

git clone https://github.com/ggml-org/llama.cpp

ln -sf /vendor/lib64/libOpenCL.so $PREFIX/lib/libOpenCL.so

cd $HOME/llama.cpp
rm -rf build

cmake -B build \
  -DGGML_OPENCL=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_FLAGS="-march=armv8.2-a+fp16+dotprod -O3" \
  -DCMAKE_CXX_FLAGS="-march=armv8.2-a+fp16+dotprod -O3"

cmake --build build --config Release -j$(nproc)

cd $HOME/llama.cpp
mkdir -p models

ln -s $HOME/llama.cpp/build/bin/llama-server $PREFIX/bin/

# export HF_HUB_TOKEN="tu_token_aqui" # Opcional si se requiere autenticacion

# Descargar modelo base cuantizado Gemma 4 E2B QAT Q4_0
# 1. Descargar el modelo LLM cuantizado
wget -c -O models/gemma-4-E2B-it-qat-q4_0.gguf \
  https://huggingface.co/google/gemma-4-E2B-it-qat-q4_0-gguf/resolve/main/gemma-4-E2B_q4_0-it.gguf

# 2. Descargar el proyector visual (mmproj)
wget -c -O models/gemma-4-E2B-it-mmproj.gguf \
  https://huggingface.co/google/gemma-4-E2B-it-qat-q4_0-gguf/resolve/main/gemma-4-E2B-it-mmproj.gguf


llama-server \
  -m $HOME/llama.cpp/models/gemma-4-E2B-it-qat-q4_0.gguf \
  --mmproj $HOME/llama.cpp/models/gemma-4-E2B-it-mmproj.gguf \
  --host 127.0.0.1 \
  --port 8080 \
  -t 6 \
  -c 4096 \
  -ngl 35 \
  --cache-reuse 256
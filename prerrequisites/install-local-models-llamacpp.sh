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

# Descargar modelo base cuantizado
# 1. Descargar el modelo LLM cuantizado (Q4_K_M)
wget -c -O models/MiniCPM-V-4_6-Q4_K_M.gguf \
  https://huggingface.co/openbmb/MiniCPM-V-4.6-gguf/resolve/main/MiniCPM-V-4_6-Q4_K_M.gguf

# 2. Descargar el proyector visual (mmproj)
wget -c -O models/mmproj-model-f16.gguf \
  https://huggingface.co/openbmb/MiniCPM-V-4.6-gguf/resolve/main/mmproj-model-f16.gguf


llama-server \
  -m $HOME/llama.cpp/models/MiniCPM-V-4_6-Q4_K_M.gguf \
  --mmproj $HOME/llama.cpp/models/mmproj-model-f16.gguf \
  --host 127.0.0.1 \
  --port 8080 \
  -t 6 \
  -c 4096 \
  -ngl 66 \
  --chat-template minicpm-v-4_6
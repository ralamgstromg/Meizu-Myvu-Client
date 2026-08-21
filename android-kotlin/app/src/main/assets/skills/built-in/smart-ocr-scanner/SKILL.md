---
id: smart-ocr-scanner
name: Escáner OCR de Texto
description: Realiza reconocimiento de texto (OCR) en imágenes adjuntas, fotos o documentos escaneados.
parameters:
  image_path:
    type: string
    description: Ruta local del archivo de imagen a escanear.
    required: true
  mode:
    type: string
    description: Modo de extracción (full_text, receipt, business_card).
    required: false
---

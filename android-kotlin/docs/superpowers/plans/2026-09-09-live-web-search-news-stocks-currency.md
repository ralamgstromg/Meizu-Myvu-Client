# Plan de Trabajo: Actualización en Vivo de Noticias, Acciones, Divisas y Búsqueda Web

## 1. Diagnóstico del Problema Reportado por el Usuario
- **Problema**: El usuario solicitó *"información de noticias el día de hoy en Colombia"* y el resultado parecía fijo o no actualizado, sintiendo que no está trayendo información viva de la web para noticias, clima, acciones y divisas.
- **Causas Raíz Identificadas**:
  1. **Noticias**: `ExternalInfoService.fetchNewsSearch` limpiaba prefijos con una expresión regular rígida (`^(dame|busca|noticias de)\s+`). Cuando el usuario decía *"noticias el día de hoy en Colombia"* o *"solicite información de noticias..."*, el parámetro de búsqueda enviado a Google News RSS era literalmente `"el dia de hoy en colombia"` o la frase entera con relleno conversacional. Al buscar esas palabras literales, Google News emparejaba artículos viejos (de hace semanas) que contenían la palabra "hoy" en el título. Además, no se usaba la URL de titulares en vivo (`https://news.google.com/rss?hl=es-419&gl=CO&ceid=CO:es-419`) para consultas generales sobre Colombia.
  2. **Acciones / Bolsa / Criptomonedas**: No existía ruta ni proveedor para cotizaciones de acciones bursátiles (Apple, Tesla, Nvidia, Ecopetrol) ni cripto (Bitcoin, Ethereum). Al no tener Fast-Path, caían al LLM remoto, el cual tiene corte de conocimiento fijo y respondía datos desactualizados o negativas de tiempo real.
  3. **Divisas y TRM**: Faltaba soporte explícito para la expresión común en Colombia *"TRM"* / *"tasa representativa del mercado"* y *"dólar hoy en Colombia"*.
  4. **Enrutamiento en `VoiceActionRouter.kt`**: Faltaban sub-rutas explícitas dedicadas para noticias y acciones antes de la búsqueda genérica.

## 2. Solución Integral Propuesta
1. **Módulo de Noticias en Tiempo Real (`ExternalInfoService.kt`)**:
   - Limpieza exhaustiva de partículas conversacionales (*"solicito"*, *"información de"*, *"dame las"*, etc.) y términos temporales (*"el día de hoy"*, *"de hoy"*, *"actuales"*).
   - Si la consulta es sobre Colombia o general, consultar directamente el feed de titulares de última hora de Google News Colombia (`news.google.com/rss?hl=es-419&gl=CO&ceid=CO:es-419`).
   - Si la consulta es temática (*"noticias de tecnología"*, *"noticias de Petro"*, *"noticias de Medellín"*), consultar la búsqueda RSS específica con el tema limpio.
   - Extraer titular, medio emisor y formatear 3 noticias con viñetas concisas: *"Noticias de hoy en Colombia: 1) Titular A (Semana). 2) Titular B (Vanguardia). 3) Titular C (Portafolio)."*.
2. **Módulo de Acciones Bursátiles y Cripto (`ExternalInfoService.kt`)**:
   - Crear `isStockOrMarketQuery(query)` y `fetchStockOrMarket(query)`.
   - Soporte directo para Yahoo Finance API (tiempo real, sin API keys):
     - Mapeo de empresas comunes: Apple (`AAPL`), Tesla (`TSLA`), Microsoft (`MSFT`), Nvidia (`NVDA`), Google (`GOOGL`), Amazon (`AMZN`), Meta (`META`), Ecopetrol (`EC`), Bitcoin (`BTC-USD`), Ethereum (`ETH-USD`), S&P 500 (`^GSPC`), Nasdaq (`^IXIC`).
     - Búsqueda dinámica de tickers para cualquier otra acción con Yahoo Search.
     - Extracción de precio actual, moneda y porcentaje de variación del día (*"Acciones de Apple (AAPL): $310.39 USD (-1.85% hoy)"*).
3. **Ampliación de Divisas / TRM (`ExternalInfoService.kt`)**:
   - Reconocer `"trm"` y `"tasa representativa"`.
   - Si se consulta *"precio del dólar hoy"* o *"dólar en Colombia"*, mapear automáticamente `USD -> COP` y consultar la tasa oficial en vivo desde `open.er-api.com`.
4. **Enrutador de Voz (`VoiceActionRouter.kt`)**:
   - Incorporar rutas directas Fast-Path para Acciones (5c) y Noticias (5d) hacia `isAsyncExternalSearch`, resolviendo todo en **1.0 a 2.0s**.
5. **Pruebas Unitarias y Verificación**:
   - Añadir tests en `ExternalInfoServiceTest.kt` para noticias, acciones, TRM y divisas.
   - Compilar y verificar con `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.

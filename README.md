# JARVIS — Android

Aplicación Android nativa en Kotlin + Jetpack Compose. Las funciones básicas del teléfono no dependen de Google Play Services.

## Funciones de la aplicación
- Interfaz conversacional móvil con tema oscuro futurista.
- Entrada de texto y activación manos libres por la palabra «Jarvis»; la pregunta completa se captura en PCM/WAV y se transcribe en la nube.
- Servicio de micrófono en primer plano con notificación persistente y motor sherpa-onnx para detectar localmente la palabra **«Jarvis»**. Mientras espera, el audio se procesa en el dispositivo y no se envía a la nube.
- El servicio de escucha puede continuar con la pantalla apagada mientras Android y la gestión de batería del fabricante lo permitan. Debe iniciarse desde la aplicación visible después de conceder el permiso de micrófono; Android muestra una notificación permanente.
- Síntesis de voz local en español.
- Historial local persistente de hasta 100 mensajes.
- Comandos locales: hora, fecha, batería, ayuda, identidad y borrar conversación.
- Consultas abiertas enviadas a un modelo de IA en la nube mediante un backend HTTPS.
- Contexto conversacional: se envían los últimos mensajes para que JARVIS pueda continuar el diálogo.
- Workflow de GitHub Actions para compilar el APK debug.

## Conectar la IA en la nube

JARVIS incluye un gateway propio de Cloudflare Workers. La aplicación Android no contiene claves de proveedores: el gateway se comunica directamente con las API oficiales de OpenAI, Anthropic (Claude) o Google Gemini. El proveedor se selecciona en el servidor mediante `JARVIS_PROVIDER`.

### 1. Configurar las claves como secretos

Desde la carpeta `cloudflare-worker`, instala Wrangler e inicia sesión:

```bash
npx wrangler login
npx wrangler secret put OPENAI_API_KEY
npx wrangler secret put ANTHROPIC_API_KEY
npx wrangler secret put GEMINI_API_KEY
```

Introduce únicamente las claves de los proveedores que vayas a utilizar. No las pegues en el código, en GitHub, ni en la aplicación Android. Si una clave se expuso, revócala y genera otra.

### 2. Elegir el proveedor y el modelo

Configura el proveedor activo en el entorno de Cloudflare:

```bash
npx wrangler secret put JARVIS_PROVIDER
```

El valor debe ser `openai`, `anthropic` o `gemini`. Para cambiar modelos, configura las variables de entorno correspondientes en el panel de Cloudflare Workers:

- OpenAI: `OPENAI_MODEL` (predeterminado: `gpt-5.5`).
- Claude: `ANTHROPIC_MODEL` (predeterminado: `claude-sonnet-4-5-20250929`).
- Gemini: `GEMINI_MODEL` (predeterminado: `gemini-2.5-flash`).
- Transcripción de voz: `OPENAI_TRANSCRIBE_MODEL` (predeterminado: `gpt-4o-mini-transcribe`).

La transcripción de audio sigue utilizando OpenAI; las respuestas conversacionales pueden generarse con cualquiera de los tres proveedores.

### 3. Desplegar el gateway

```bash
npx wrangler deploy
```

La URL que muestra Cloudflare es la dirección de transporte del gateway propio de JARVIS, no una URL de un proveedor de IA. El gateway llama directamente a los endpoints oficiales de cada proveedor. La aplicación debe conocer la dirección de su gateway para poder comunicarse por Internet.

### Seguridad y costes

- Las claves permanecen como secretos del Worker y nunca se compilan dentro del APK.
- Una clave dentro de un APK puede extraerse; ofuscarla o guardarla en recursos Android no la vuelve secreta.
- Antes de distribuir JARVIS, protege el gateway con autenticación por instalación/usuario, límites de solicitudes y límites de gasto. CORS no es un mecanismo de autenticación.
- Supervisa el consumo de cada proveedor. Las API se facturan por separado de las suscripciones de ChatGPT o Claude.
- Las conversaciones se envían al proveedor seleccionado para generar respuestas. No envíes contraseñas ni datos sensibles.

## Limitaciones actuales
- El usuario debe desplegar el Worker y proporcionar su URL; el repositorio no contiene secretos ni puede desplegarlo dentro de la cuenta del usuario sin autorización.
- La detección local utiliza un modelo KWS entrenado en inglés. Su precisión con pronunciación y acentos españoles debe validarse y ajustarse en el Huawei Nova Y90.
- Después de detectar «Jarvis», la app conserva unos segundos de audio previos y captura la pregunta hasta detectar silencio. Al detectar la palabra se conserva una ventana local de hasta un segundo para no cortar el inicio de «Jarvis», y se captura la pregunta hasta detectar silencio. Esa pequeña ventana de activación y la pregunta se envían al endpoint `/voice`; el audio de espera anterior no se transmite.
- Android exige que el usuario conceda el permiso de micrófono y que el servicio de micrófono en primer plano se inicie mientras la aplicación está visible. No es posible saltarse esos controles del sistema. EMUI puede aplicar restricciones de batería; hay que validar el comportamiento real con la pantalla apagada en el Huawei Nova Y90.
- La transcripción requiere conexión a Internet, un Worker desplegado y una clave de API con acceso al modelo de transcripción. El reconocimiento local de la palabra de activación no depende de Google Play Services.
- El tono grave depende del motor y las voces TTS disponibles en el dispositivo; no se puede garantizar una voz masculina idéntica en todos los teléfonos.
- Los comandos locales todavía no ejecutan acciones arbitrarias ni controlan ajustes sensibles del teléfono.

## Compilar
El workflow **Android APK** se ejecuta al hacer push a `main` o manualmente desde Actions. Si finaliza correctamente, publica el artefacto `jarvis-debug-apk` con `app-debug.apk`.

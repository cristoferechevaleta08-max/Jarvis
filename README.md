# JARVIS — Android

Aplicación Android nativa en Kotlin + Jetpack Compose. Las funciones básicas del teléfono no dependen de Google Play Services.

## Funciones de la aplicación
- Interfaz conversacional móvil con tema oscuro futurista.
- Entrada de texto y reconocimiento de voz en español mediante el proveedor de voz disponible en Android.
- Escucha manos libres automática al abrir la aplicación: solicita el permiso de micrófono la primera vez y, una vez concedido, espera la palabra **«Jarvis»** sin que el usuario tenga que pulsar un botón. Acepta la pregunta en la misma frase o en la siguiente.
- La escucha se pausa mientras se procesa una consulta a la IA y se reanuda después. En esta versión funciona mientras la aplicación permanece abierta y visible; todavía no es un servicio permanente para escuchar con la aplicación cerrada o la pantalla apagada.
- Síntesis de voz local en español.
- Historial local persistente de hasta 100 mensajes.
- Comandos locales: hora, fecha, batería, ayuda, identidad y borrar conversación.
- Consultas abiertas enviadas a un modelo de IA en la nube mediante un backend HTTPS.
- Contexto conversacional: se envían los últimos mensajes para que JARVIS pueda continuar el diálogo.
- Workflow de GitHub Actions para compilar el APK debug.

## Conectar la IA en la nube

La aplicación incluye un cliente Android y un gateway de Cloudflare Workers. El gateway llama a la **OpenAI Responses API**. La clave privada nunca se introduce en la aplicación ni se guarda en el APK.

### 1. Crear una clave de API
Crea una clave de proyecto desde el panel de OpenAI: https://platform.openai.com/api-keys. La API se factura por separado de una suscripción de ChatGPT; revisa los precios y límites antes de usarla.

### 2. Desplegar el backend
Instala Wrangler y autentícate en Cloudflare. Desde la carpeta `cloudflare-worker`:

```bash
npx wrangler login
npx wrangler secret put OPENAI_API_KEY
npx wrangler deploy
```

Cuando Wrangler solicite el secreto, pega la clave de API. Opcionalmente puedes configurar el modelo mediante la variable `OPENAI_MODEL`; el valor predeterminado del Worker es `gpt-5.5`.

El despliegue mostrará una URL similar a `https://jarvis-cloud.<tu-cuenta>.workers.dev`.

### 3. Configurar la aplicación
Abre JARVIS, toca el icono de ajustes y pega la URL del Worker añadiendo `/chat`, por ejemplo:

`https://jarvis-cloud.<tu-cuenta>.workers.dev/chat`

Guarda la dirección. Con conexión a Internet, escribe o di «Jarvis» seguido de tu pregunta. Las respuestas abiertas se enviarán al modelo de IA y JARVIS las leerá en voz alta.

### Seguridad y costes
- No subas claves de OpenAI a GitHub ni las incluyas en el APK.
- El Worker es un endpoint público: antes de distribuir la aplicación, configura límites de solicitudes y protección contra abuso en Cloudflare (rate limiting) y vigila el consumo de la API.
- Las conversaciones se envían al proveedor para generar respuestas. El Worker usa `store: false`; aun así, no envíes contraseñas ni datos sensibles.
- La API de OpenAI requiere facturación/créditos propios; ChatGPT Plus/Free no incluye automáticamente saldo de API.

## Limitaciones actuales
- El usuario debe desplegar el Worker y proporcionar su URL; el repositorio no contiene secretos ni puede desplegarlo dentro de la cuenta del usuario sin autorización.
- La escucha se inicia automáticamente al abrir la aplicación y después de conceder el permiso de micrófono. Android exige que el usuario conceda ese permiso; ninguna app legítima puede saltarse ese diálogo. Esta versión solo escucha mientras JARVIS está abierto y visible. Para escuchar con la pantalla apagada o la app en segundo plano hace falta un servicio de micrófono en primer plano con notificación persistente y pruebas específicas en Huawei; aún no está implementado.
- El reconocimiento de voz depende del proveedor instalado en Android. No se presupone que Huawei tenga Google Speech Services.
- El tono grave depende del motor y las voces TTS disponibles en el dispositivo; no se puede garantizar una voz masculina idéntica en todos los teléfonos.
- Los comandos locales todavía no ejecutan acciones arbitrarias ni controlan ajustes sensibles del teléfono.

## Compilar
El workflow **Android APK** se ejecuta al hacer push a `main` o manualmente desde Actions. Si finaliza correctamente, publica el artefacto `jarvis-debug-apk` con `app-debug.apk`.

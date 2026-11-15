# JARVIS — Android

Aplicación Android nativa en Kotlin + Jetpack Compose, diseñada para mantener sus funciones básicas sin depender de Google Play Services.

## Funciones implementadas
- Interfaz conversacional móvil con tema oscuro futurista.
- Entrada de texto y reconocimiento de voz en español mediante el proveedor disponible en Android.
- Síntesis de voz local en español, con velocidad y tono ajustados.
- Historial local persistente: conserva los últimos 100 mensajes entre aperturas de la aplicación.
- Comandos sin conexión: consultar hora, fecha y nivel de batería; mostrar ayuda e información del asistente.
- Workflow de GitHub Actions para compilar y publicar un APK debug.

## Limitaciones actuales
- Las respuestas abiertas todavía no usan un modelo de IA: la conexión con el servidor remoto está pendiente.
- El reconocimiento de voz requiere que el dispositivo tenga un proveedor compatible instalado. No se presupone que Huawei tenga Google Speech Services.
- El tono grave depende del motor y las voces TTS disponibles en el dispositivo; no se puede garantizar una voz masculina idéntica en todos los teléfonos.
- Los comandos locales son deliberadamente limitados y no controlan ajustes del teléfono.

## Compilar
El workflow **Android APK** se ejecuta al hacer push a `main` o manualmente desde la pestaña Actions. Si finaliza correctamente, publica el artefacto `jarvis-debug-apk` con `app-debug.apk`.

## Próximas etapas
1. Verificar y corregir la compilación en GitHub Actions.
2. Conectar un modelo de IA mediante un backend HTTPS seguro; nunca incluir claves privadas en el APK.
3. Ampliar los comandos Android con permisos explícitos y confirmaciones para acciones sensibles.
4. Probar instalación, micrófono y voz en dispositivos reales, incluido Huawei Nova Y90.

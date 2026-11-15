# JARVIS — Android

Aplicación Android nativa en Kotlin + Jetpack Compose.

## Estado inicial
- Interfaz móvil futurista.
- Conversación local de demostración.
- Síntesis de voz del sistema en español.
- Preparación de permisos de red y micrófono.
- Flujo de GitHub Actions para compilar un APK debug.

## Compilar
El workflow **Android APK** se ejecuta al hacer push a `main` o manualmente desde Actions. El archivo `app-debug.apk` queda publicado como artefacto de la ejecución.

## Próximas etapas
1. Añadir reconocimiento de voz con alternativa compatible con dispositivos sin Google Play Services.
2. Conectar un backend HTTPS seguro para el modelo de IA; no almacenar claves privadas dentro del APK.
3. Añadir memoria local, preferencias y comandos Android con permisos explícitos.
4. Probar instalación y funcionamiento en dispositivos reales, incluido Huawei.

> La respuesta conversacional actual es una demostración local; todavía no está conectada a un modelo de IA remoto.

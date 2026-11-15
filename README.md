# JARVIS — Android

Aplicación Android nativa en Kotlin + Jetpack Compose, pensada para funcionar sin depender de Google Play Services en sus funciones básicas.

## Implementado
- Interfaz conversacional móvil con tema oscuro futurista.
- Entrada de texto.
- Botón de micrófono con solicitud del permiso `RECORD_AUDIO`.
- Reconocimiento de voz en español mediante el proveedor de reconocimiento instalado en Android.
- Síntesis de voz local en español, con tono y velocidad ajustados.
- Configuración de compilación Android y workflow de GitHub Actions para generar un APK debug.

## Limitaciones actuales
- Las respuestas son demostrativas: todavía no hay conexión a un modelo de IA remoto.
- El reconocimiento de voz depende de que el dispositivo tenga un servicio compatible instalado. No se presupone que Huawei tenga Google Speech Services.
- El tono grave depende de las voces que ofrezca el motor TTS del dispositivo; la aplicación no puede garantizar una voz masculina idéntica en todos los teléfonos.
- No hay memoria persistente ni comandos del sistema implementados todavía.

## Compilar
El workflow **Android APK** se ejecuta al hacer push a `main` o manualmente desde la pestaña Actions. Si finaliza correctamente, publica el artefacto `jarvis-debug-apk` con `app-debug.apk`.

## Próximas etapas
1. Verificar y corregir la primera compilación en GitHub Actions.
2. Añadir un backend HTTPS seguro para el modelo de IA. Las claves privadas no se incluirán en el APK.
3. Añadir memoria local y preferencias persistentes.
4. Implementar comandos Android con permisos explícitos y confirmaciones para acciones sensibles.
5. Probar instalación, micrófono y voz en dispositivos reales, incluido Huawei Nova Y90.

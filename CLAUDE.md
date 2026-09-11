\# Proyecto: Android Ad-Blocker



\## Arquitectura y Tecnologías

\- \*\*Lenguaje:\*\* Kotlin

\- \*\*UI:\*\* Jetpack Compose

\- \*\*Patrón:\*\* MVVM + Clean Architecture

\- \*\*Enfoque de filtrado:\*\* `VpnService` local para filtrado de tráfico en el dispositivo sin acceso ROOT.



\## Seguridad y Privacidad (Estricto)

\- \*\*Zero Telemetry:\*\* Todo el procesamiento ocurre 100% en el dispositivo. Prohibido enviar datos a servidores externos.

\- \*\*Mínimos Privilegios:\*\* Mantener `AndroidManifest.xml` restringido a los permisos estrictamente necesarios.

\- \*\*Cero librerías de terceros:\*\* No incluir SDKs de analítica, rastreo o telemetría.



\## Flujo Autónomo de Pruebas y Corrección

1\. Implementar funcionalidad en módulos limpios.

2\. Crear pruebas unitarias (JUnit) e integración para la lógica de filtrado.

3\. Ejecutar pruebas con `./gradlew test` (o `gradlew.bat test` en Windows).

4\. \*\*Bucle de Auto-Fix:\*\* Si alguna prueba falla o hay excepciones en los logs, analiza el error, corrige el código y vuelve a ejecutar `./gradlew test` hasta que el 100% pase con éxito.



\## Estilo de Respuesta

\- Máxima eficiencia de tokens: Evita explicaciones teóricas e introducciones.

\- Muestra comandos ejecutados, cambios clave en el código y resultados de los tests.


# Project K Native — teste de API

Aplicativo Android nativo em Kotlin. Não usa WebView, navegador, Capacitor ou conteúdo HTML.

## O que faz
- Abre uma tela Android nativa.
- Faz uma requisição HTTP GET para `https://projectspacek.squareweb.app/api/status`.
- Mostra o código HTTP e o corpo da resposta na tela.

## Gerar APK
Requer Android Studio/Android SDK e JDK 17 ou superior:
1. Abra esta pasta no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Vá em Build > Build APK(s).
4. O APK de debug ficará normalmente em `app/build/outputs/apk/debug/app-debug.apk`.

O projeto não contém credenciais. A rota de status é pública; login e rotas protegidas por cookie são uma próxima etapa, pois o site usa sessão por cookie e pode exigir token CSRF/formulário.

# STILO OVERBOOST | Android

Projeto Android **nativo Kotlin** para o Samsung A22 e outros aparelhos Android 8.0+.

## Inclui

- Hub inicial com widgets personalizáveis e persistentes;
- Temas **Claro, Escuro e Azul Racing**;
- Bluetooth Classic SPP para ESP32 (nome sugerido `STILO_OVERBOOST`);
- Monitoramento A/C, RPM, velocidade, tensão e permissivos;
- Painel de frames CAN recebidos, logs e descoberta de dispositivos;
- Modo demonstração para navegar mesmo sem ESP32;
- Build de APK debug com GitHub Actions.

**Atenção:** este aplicativo é a interface Android. Para obter telemetria e controle reais, o firmware do ESP32 precisa implementar o protocolo descrito em `PROTOCOLO.md`. O telefone nunca deve alimentar diretamente o relé do compressor; a validação dos permissivos é feita no ESP32/circuito de segurança apropriado. Não considerar leitura ou comando válidos sem testes instrumentados.

## Compilar pelo GitHub

1. Colocar os arquivos na raiz do repositório (inclusive `.github/workflows/android-apk.yml`).
2. Abrir a guia **Actions** e selecionar **Compilar APK Android** (ou aguardar o evento push na branch main).
3. Na execução concluída, em **Artifacts**, baixar **StiloOverboost-Android-debug**.
4. Extrair o ZIP do artifact e instalar `app-debug.apk` no Samsung A22; Android poderá solicitar autorização para instalar apps dessa origem.

O arquivo **ZIP deste projeto NÃO é o APK**, é o código-fonte. A compilação precisa ser executada no GitHub Actions ou Android Studio/Gradle com Android SDK 35 e JDK 17.

## Compilação local

Use JDK 17, Android SDK 35, Gradle 8.11.1: `gradle :app:assembleDebug`. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Licença e segurança

Build debug para testes. Não publicar ou distribuir como produção sem revisão de segurança, validação de hardware e assinatura de release. A/C comanda apenas **solicitação**: toda arbitragem de pressostatos, temperatura, proteção e relés deve ficar no ESP32/hardware automotivo validado.

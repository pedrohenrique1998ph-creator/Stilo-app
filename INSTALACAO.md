# Como transformar este projeto em APK instalável

## Caminho rápido (computador)

1. Baixe `StiloOverboost-Android-projeto.zip` e **extraia** seu conteúdo.
2. Abra o seu repositório `pedrohenrique1998ph-creator/Stilo-app` no navegador de um computador.
3. Escolha **Add file → Upload files**.
4. Arraste os arquivos e pastas **extraídos** para o GitHub. Os caminhos como `app/src/main/...` e `.github/workflows/...` precisam ser preservados. **Não envie apenas o ZIP**: GitHub Actions não compilará um arquivo ZIP largado na raiz.
5. Conclua com **Commit changes** (branch `main`).
6. Abra **Actions → Compilar APK Android**. Pode iniciar automaticamente após o commit. Se necessário, use **Run workflow**.
7. Aguarde o indicador verde de sucesso. Abra a execução e baixe o artifact `StiloOverboost-Android-debug`.
8. No Samsung A22 extraia o artifact baixado e abra `app-debug.apk` para instalar. O Android poderá solicitar `Permitir desta fonte`.

Se o ícone da execução estiver vermelho, consulte a primeira mensagem de erro nas etapas da execução; não tente instalar o projeto ZIP como APK.

## Importante

O projeto não foi compilado nesta sessão porque o ambiente local não dispõe de Android SDK/Gradle completo e a conexão GitHub recusou a gravação de arquivos (HTTP 403). O ZIP foi validado como arquivo íntegro e o XML Android foi analisado, mas o sucesso do build ainda depende da execução no GitHub Actions.

O app abre e navega sem ESP32 (e possui **modo DEMO**). Telemetria e pedidos A/C reais dependem do firmware ESP32 obedecer a `PROTOCOLO.md`. Não ligar o relé de compressor antes de validar pressostatos e demais proteções em bancada.

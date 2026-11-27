# OpenJarvis Android

Assistente pessoal Android inspirado no OpenJarvis, com processamento via **Gemini (Firebase AI Logic)**, redirecionamento para **Ollama local na rede Wi-Fi**, **Speech-to-Text nativo**, **Foreground Service contínuo** (voz + gatilhos LAN autenticados), integrações com apps do sistema e **atualizações remotas via GitHub Releases + F-Droid**.

---

## 1. Como publicar no seu GitHub

1. No Google AI Studio, abra o menu **Configurações** (ícone de engrenagem) e escolha **Enviar para o GitHub (Push to GitHub)** — ou exporte o projeto em **ZIP** e suba para um repositório no seu GitHub.
2. Crie ou selecione o repositório (ex.: `seu-usuario/OpenJarvis-Android`).
3. No GitHub, acesse **Settings → Pages** e configure:
   - **Source**: `Deploy from a branch`
   - **Branch**: `gh-pages` / `/ (root)`

> O branch `gh-pages` é criado automaticamente pelo workflow na primeira release publicada.

---

## 2. Como lançar novas versões (Versionamento + F-Droid Automático)

O repositório já inclui o workflow `.github/workflows/release-fdroid.yml` e os metadados em `fastlane/metadata/android/pt-BR/`.

Para publicar uma nova atualização:

1. Atualize `versionCode` e `versionName` em `app/build.gradle.kts` (por exemplo: `versionCode = 2` e `versionName = "1.0.1"`).
2. Opcionalmente adicione as novidades em `fastlane/metadata/android/pt-BR/changelogs/2.txt`.
3. Crie e envie uma tag começando com `v`:
   ```bash
   git add .
   git commit -m "Release v1.0.1"
   git tag v1.0.1
   git push origin main --tags
   ```
4. O **GitHub Actions** fará automaticamente:
   - Compilação do APK (`assembleRelease` se os secrets de assinatura estiverem configurados, ou `assembleDebug` como fallback);
   - Criação da **GitHub Release** com o APK anexado;
   - Atualização do índice do **repositório F-Droid** em `https://<seu-usuario>.github.io/<seu-repositorio>/fdroid/repo` no branch `gh-pages`.

### Secrets recomendados no GitHub (Settings → Secrets and variables → Actions)

Para que o Android e o F-Droid aceitem atualizações sem precisar reinstalar o app, configure uma chave de assinatura estável:

- `KEYSTORE_BASE64`: arquivo `.jks` de release codificado em Base64 (`alias = upload`).
- `STORE_PASSWORD`: senha do keystore de release.
- `KEY_PASSWORD`: senha da chave `upload`.
- `FDROID_KEYSTORE_BASE64` *(opcional, recomendado)*: keystore `.p12` para assinar o índice do repositório F-Droid com chave fixa (`alias = openjarvis-fdroid`).
- `FDROID_KEYSTORE_PASS` / `FDROID_KEY_PASS`: senhas do keystore do F-Droid.

---

## 3. Como receber atualizações pelo F-Droid e pelo próprio OpenJarvis

1. No app **OpenJarvis**, abra a aba **Motores → Atualizações Remotas (GitHub & F-Droid)**.
2. Informe seu repositório no formato `seu-usuario/OpenJarvis-Android`.
3. Toque em **Gerar URL GitHub Pages** (gera automaticamente `https://seu-usuario.github.io/OpenJarvis-Android/fdroid/repo`) e toque em **Salvar**.
4. Toque em **Adicionar / Abrir Repositório no F-Droid** para cadastrar seu repositório no aplicativo **F-Droid**.
5. Sempre que você criar uma nova tag `v*` no GitHub:
   - O **F-Droid** detectará e puxará o update automaticamente do seu repositório;
   - O **OpenJarvis** também avisará no topo do app (ou quando você disser *"Jarvis, verificar atualizações"*), permitindo abrir o update direto no F-Droid ou no GitHub.

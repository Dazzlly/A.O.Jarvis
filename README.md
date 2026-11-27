# OpenJarvis Android

Assistente pessoal Android inspirado no OpenJarvis, com processamento via **Gemini (Firebase AI Logic)**, redirecionamento para **Ollama local na rede Wi-Fi**, **Speech-to-Text nativo**, **Foreground Service contínuo** (voz + gatilhos LAN autenticados), **compartilhamento de tela em tempo real**, **controle de configurações do dispositivo**, **Auto-Dev via Pull Requests** e **atualizações remotas via GitHub + F-Droid**.

---

## 1. Como publicar no seu GitHub e ativar o repositório F-Droid

1. No Google AI Studio, abra **Configurações → Push to GitHub** e envie este projeto para um repositório **Público** no seu GitHub (ex.: `seu-usuario/OpenJarvis-Android`).
   > **Importante:** o repositório precisa ser **Público** para que o aplicativo F-Droid consiga baixar o índice e os APKs sem erro `0 bytes` / `404`.
2. Assim que o push cair em `main` (ou quando você rodar manualmente em **Actions → Build APK, GitHub Release & F-Droid Repo → Run workflow**), o GitHub Actions vai:
   - Compilar o APK;
   - Gerar e assinar o índice F-Droid válido (`entry.jar`, `index-v1.jar`, `index-v2.json`) no branch `gh-pages`;
   - Exibir a URL pronta e o **Fingerprint SHA-256** no resumo do job na aba **Actions**.
3. **Sem precisar configurar GitHub Pages**, o seu repositório F-Droid já fica online em:
   ```text
   https://raw.githubusercontent.com/<seu-usuario>/<seu-repositorio>/gh-pages/fdroid/repo
   ```
   *(Se quiser usar também `https://<seu-usuario>.github.io/<seu-repositorio>/fdroid/repo`, basta ativar **Settings → Pages → Branch: gh-pages**.)*

---

## 2. Como adicionar no aplicativo F-Droid (sem erro de conexão)

1. Aguarde o workflow na aba **Actions** do GitHub ficar verde (✅).
2. No app **OpenJarvis**, abra **Motores → Atualizações Remotas (GitHub & F-Droid)**.
3. Informe `seu-usuario/seu-repositorio` e toque em **URL Raw (Sem Pages)** — o app testa automaticamente se `entry.jar` / `index-v1.jar` está online antes de abrir o F-Droid.
4. Quando aparecer **Repositório F-Droid online e válido**, toque em **Abrir no F-Droid** (ou cole a URL `https://raw.githubusercontent.com/.../gh-pages/fdroid/repo` no F-Droid).

> **Por que ocorria `ClosedByteChannelException: required 10 bytes but only 0 available`?**
> Esse erro do F-Droid acontece quando a URL adicionada aponta para a página principal do GitHub (`github.com/usuario/repo`) ou quando o branch `gh-pages` ainda não tinha gerado `entry.jar` / `index-v1.jar` (retornando HTTP 404 / 0 bytes).

---

## 3. Como lançar novas versões (Versionamento)

1. Atualize `versionCode` e `versionName` em `app/build.gradle.kts` (ex.: `versionCode = 2`, `versionName = "1.0.1"`).
2. Crie uma tag `v*` (ex.: `v1.0.1`) ou faça push em `main`:
   ```bash
   git add .
   git commit -m "Release v1.0.1"
   git tag v1.0.1
   git push origin main --tags
   ```
3. Para manter assinatura estável entre atualizações no Android e no F-Droid, configure os secrets em **Settings → Secrets and variables → Actions**:
   - `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` (chave de release `alias = upload`);
   - `FDROID_KEYSTORE_BASE64`, `FDROID_KEYSTORE_PASS`, `FDROID_KEY_PASS` (keystore `.p12` do repositório F-Droid com `alias = openjarvis-fdroid`).

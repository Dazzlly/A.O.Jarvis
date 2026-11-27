# A.O.Jarvis Android

Assistente pessoal de voz Android com processamento via **Gemini (Firebase AI Logic)**, redirecionamento para **Ollama local na rede Wi-Fi**, **detecção leve "Hey Jarvis" (AudioRecord)**, **Speech-to-Text nativo**, **TTS configurável**, **Foreground Service contínuo** (voz + monitor de bateria + gatilhos LAN autenticados), **compartilhamento de tela em tempo real**, **controle de configurações do dispositivo**, **Auto-Dev via Pull Requests** e **atualizações remotas via GitHub Releases + F-Droid**.

---

## 1. Como publicar no seu GitHub e ativar o repositório F-Droid

1. Envie o projeto para um repositório **Público** no seu GitHub (ex.: `seu-usuario/A.O.Jarvis`).
   > **Importante:** o repositório precisa ser **Público** para que o aplicativo F-Droid consiga baixar o índice e os APKs sem erro `0 bytes` / `404`.
2. Assim que o push cair em `main` (ou quando você rodar manualmente em **Actions → Build APK, GitHub Release & F-Droid Repo → Run workflow**), o GitHub Actions vai:
   - Compilar o APK com chave estável;
   - Publicar a GitHub Release (`v<versionName>`) com o APK anexado;
   - Sanitizar e deduplicar versões anteriores (removendo nomes/ícones legados e assinaturas incompatíveis);
   - Gerar, assinar com chave persistente e validar integralmente os hashes SHA-256 do índice F-Droid (`entry.jar`, `index-v1.jar`, `index-v2.json`) no branch `gh-pages`;
   - Exibir a URL oficial e o **Fingerprint SHA-256** no resumo do job na aba **Actions**.
3. **Sem precisar configurar GitHub Pages**, o seu repositório F-Droid fica online em:
   ```text
   https://raw.githubusercontent.com/<seu-usuario>/<seu-repositorio>/gh-pages/fdroid/repo
   ```

---

## 2. Como adicionar ou atualizar no aplicativo F-Droid

1. Aguarde o workflow na aba **Actions** do GitHub concluir com sucesso (✅).
2. No app **A.O.Jarvis**, abra **Motores → Atualizações Remotas (GitHub & F-Droid)**.
3. Informe `seu-usuario/seu-repositorio` e toque em **URL Raw (Sem Pages)** — o app testa automaticamente se `entry.jar` / `index-v1.jar` está online antes de abrir o F-Droid.
4. Quando aparecer **Repositório F-Droid online e válido**, toque em **Abrir no F-Droid** (ou cole a URL `https://raw.githubusercontent.com/.../gh-pages/fdroid/repo` no F-Droid).

> **Se você já tinha o repositório antigo adicionado no F-Droid:**
> Remova o repositório antigo no F-Droid e adicione-o novamente uma única vez após o novo workflow concluir. Isso limpa o cache antigo de chave/hash que causava `IOException: Hash not matching` e versões `1.0` duplicadas.

---

## 3. Como lançar novas versões (Versionamento)

1. Incremente `versionCode` (ex.: `5` → `6`) e atualize `versionName` em `app/build.gradle.kts`, além de adicionar o changelog em `fastlane/metadata/android/pt-BR/changelogs/<versionCode>.txt`.
2. Faça push em `main` ou crie uma tag `v*` (ex.: `v1.3.1`).
3. Para garantia permanente de assinatura mesmo após períodos longos sem builds, configure os secrets em **Settings → Secrets and variables → Actions**:
   - `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` (chave de release `alias = upload`);
   - `FDROID_KEYSTORE_BASE64`, `FDROID_KEYSTORE_PASS`, `FDROID_KEY_PASS` (keystore `.p12` do repositório F-Droid).

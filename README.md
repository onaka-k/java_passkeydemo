# Java Passkey Demo

Java 17 / Spring Boot / Spring Security / Gradle による Passkey デモです。

## 仕様
- ログイン方式
  - ID/PASS
  - ID/Passkey
- 画面
  - ログイン画面
  - Passkey変更画面
- Passkey上限
  - 1 ID あたり最大 5 件

## 開発者向け動作説明（localhost専用）

このデモは localhost での動作確認を前提にしています。

### 認証フロー
- ID/PASS ログイン
  - Spring Security のフォームログインで認証します。
  - 認証成功後は Passkey変更画面に遷移します。
- ID/Passkey ログイン
  - ログイン画面で ID を入力し、Passkey ボタンから WebAuthn を開始します。
  - サーバーは challenge をセッションに保存し、クライアントの clientDataJSON 内 challenge と照合します。
  - demo 実装として、credential 所有確認と challenge 一致を中心に判定します。

### Passkey登録・変更フロー
- Passkey変更画面で登録済み passkey 一覧を表示します。
- 追加時は登録オプションを取得して navigator.credentials.create を実行します。
- 登録完了 API で challenge を検証し、credential を DB に保存します。
- 削除 API で登録済み credential を削除できます。

### データモデルと制約
- app_user と passkey_credential は 1:N 関係です。
- 1ユーザーあたり passkey は最大 5 件です。
- 6件目の登録は業務エラー（HTTP 409）として拒否します。

### localhost前提の理由
- WebAuthn は Secure Context が必要です。
- localhost は例外的に HTTP でも Secure Context 扱いで利用できます。
- 本READMEの手順どおり localhost で起動すれば、そのまま検証できます。

## 主要ライブラリ（開発者向け）

- Spring Boot Starter Web
  - MVCコントローラ、REST API、組み込みTomcatを提供します。
  - このデモではログイン画面遷移と passkey API の土台です。

- Spring Boot Starter Security
  - 認証/認可の基本機能を提供します。
  - このデモでは ID/PASS ログイン、セッション管理、保護URL制御を担当します。

- Spring Boot Starter Thymeleaf
  - サーバーサイドテンプレートでHTMLを描画します。
  - このデモではログイン画面と Passkey変更画面を生成します。

- Spring Boot Starter Data JPA
  - JPA/Hibernate で永続化を実装します。
  - このデモでは app_user / passkey_credential の保存・検索に使います。

- H2 Database
  - 軽量な組み込み/ファイルDBです。
  - このデモではローカル検証用のDBとして利用しています。

- WebAuthn4J Core
  - WebAuthn（Passkey）のサーバー側検証ライブラリです。
  - このデモでは今後の厳密検証（署名/attestation/assertion）の拡張ポイントになります。

- WebAuthn4J Spring Security Core
  - Spring Security 向けの WebAuthn4J 連携機能を提供します。
  - Options Endpoint や認証連携を活用しやすくし、自前実装の削減に寄与します。

- Lombok
  - Getter/Setter などの定型コードを削減します。
  - このデモではエンティティやサービスのボイラープレート削減に使用しています。

- spring-boot-starter-test / spring-security-test
  - 単体・結合テストの基盤と Security テスト支援を提供します。
  - 認証付きエンドポイントのテストで利用します。

## テーブルレイアウト

### app_user

| カラム名 | 型(目安) | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT, NOT NULL | ユーザーID |
| username | VARCHAR(100) | UNIQUE, NOT NULL | ログインID |
| password_hash | VARCHAR(255) | NOT NULL | パスワードハッシュ |
| display_name | VARCHAR(120) | NOT NULL | 表示名 |

### passkey_credential

| カラム名 | 型(目安) | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT, NOT NULL | レコードID |
| user_id | BIGINT | FK(app_user.id), NOT NULL | 紐づくユーザー |
| credential_id | VARCHAR(255) | UNIQUE, NOT NULL | Passkey credential ID |
| label | VARCHAR(120) | NOT NULL | 端末ラベル |
| sign_count | BIGINT | NOT NULL | 署名カウンタ |
| transports | VARCHAR(1000) | NULL | transports情報 |
| user_handle | VARCHAR(255) | NULL | userHandle |
| registration_payload | VARCHAR(4000) | NULL | 登録時ペイロード |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL | 更新日時 |

### リレーション
- app_user (1) - (N) passkey_credential
- 1ユーザーにつき passkey は最大5件（アプリケーションロジックで制約）

## デモユーザー
- ID: demo
- PASS: pass1234

## 起動
1. `./gradlew bootRun`（Windows は `gradlew.bat bootRun`）
2. ブラウザで `http://localhost:8080/login` を開く

## 配布用jar
1. `./gradlew clean bootJar`
2. `java -jar build/libs/java_passkeydemo-0.0.1-SNAPSHOT.jar`

## GitHub Releasesで配布

このリポジトリは、タグをpushすると GitHub Actions が jar をビルドして Releases に添付します。

### リリース作成手順
1. 変更を main に反映
2. バージョンタグを作成（例: `v1.0.0`）
3. タグを push

```bash
git tag v1.0.0
git push origin v1.0.0
```

### 公開される成果物
- Release 名: タグ名（例: v1.0.0）
- 添付ファイル名: `java_passkeydemo-<タグ名>.jar`（例: `java_passkeydemo-v1.0.0.jar`）

### 利用者の実行手順
1. GitHub の Releases ページから最新の jar をダウンロード
2. 次を実行

```bash
java -jar java_passkeydemo-v1.0.0.jar
```

## 検証手順
1. ID/PASS でログイン
2. Passkey変更画面で Passkey を追加
3. ログアウト
4. ログイン画面で同じ ID を入力し「Passkeyでログイン」を実行
5. 最大5件まで追加でき、6件目はエラーになることを確認

## 注意
- 本デモの ID/Passkey ログインは、学習用に challenge と credential 所有確認を主に実装しています。
- 本番運用では、WebAuthn 署名・attestation/assertion の厳密検証を有効にしてください。

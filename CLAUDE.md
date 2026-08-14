# barrel-todo

[barrelkv](https://github.com/heruoji/barrelkv) をライブラリとして利用したシンプルな ToDo 管理 CLI ツールの実装(個人学習目的)。

## 技術スタック
- Java 21
- Gradle 9.7.0 (Kotlin DSL)
- JUnit5
- groupId: io.github.heruoji
- 依存: barrelkv(git submoduleとして`barrelkv/`に同梱し、Gradleコンポジットビルド`includeBuild`でソース参照。Maven座標へのpublishは行わない)

## barrelkvへの依存方針
- なぜ submodule + includeBuild か: barrelkv はまだ発展中(並行アクセス対応など)のプロジェクトであり、変更のたびに publish し直す運用は避けたい。ソースを直接参照することで、barrelkv 側の変更をそのまま取り込める
- `settings.gradle.kts` は `includeBuild("barrelkv")` を指定し、`build.gradle.kts` は `implementation("io.github.heruoji:barrelkv:1.0-SNAPSHOT")` を宣言する。barrelkv 側の `group`/`rootProject.name`/`version` と一致させることで依存置換が効く
- barrelkv 側に変更を加えた場合の更新手順: `barrelkv/`(submodule)内で `git checkout main && git pull` してから、barrel-todo リポジトリ側で `git add barrelkv && git commit` してgitlink(参照先コミット)を更新する
- barrelkv 自体には Todo 固有のロジックを一切持ち込まない。全キー列挙用の `BarrelKv.keys()`(barrelkv 本体への唯一の追加API)以外、barrelkv には手を入れない

## アーキテクチャ方針
- barrelkv はキーを列挙できる単純な String→String の KVストア。barrelkv 自体はキーのプレフィックスという概念を持たないため、barrel-todo 側で全ての Todo キーに `"todo:" + id` というプレフィックスを付与し、`keys()` の結果をクライアント側でフィルタして一覧を実現する(`TodoRepository`)
- Todo の複数フィールド(title/description/done/dueDate/priority/createdAt/updatedAt)は、barrelkv の1つの String value に、自前実装の最小限 JSON エンコーダ/デコーダでシリアライズする。汎用 JSON ライブラリ(Gson/Jackson 等)は意図的に使わない。理由: barrelkv 自身が「区切り文字を使わず長さプレフィックス方式でバイナリを自作する」という設計哲学(依存を増やさず自分で仕組みを理解する、という個人学習目的)と一貫性を保つため
- ID は連番の数値(long)。永続カウンタは持たず、`"todo:"` プレフィックス付きキーを `keys()` で毎回スキャンし、末尾の数値の最大値+1 を採番する(`TodoRepository.nextId()`)
  - この設計の帰結として、全件削除後は次に追加した Todo の ID が 1 から再利用される(意図した挙動であり、バグではない)
  - 単一プロセスの対話 CLI が前提であり、`add()` の並行呼び出しに対する競合制御(ロック・CAS 等)は範囲外とする

## Todo の値の JSON フォーマット

```json
{"id":1,"title":"Buy milk","description":null,"done":false,"dueDate":"2026-08-20","priority":"HIGH","createdAt":1755151200000,"updatedAt":1755151300000}
```

- フィールド順は固定(id, title, description, done, dueDate, priority, createdAt, updatedAt)。`TodoJsonSerializer.encode()` の出力はこの順に固定した回帰テストで保護している
- デコード側(`Cursor`)はフィールド順に依存しない、フラットなオブジェクトのみを扱う軽量パーサー。ネストや配列は非対応(Todo の固定シェイプ専用であり、汎用 JSON ライブラリの代替ではない)
- title/description の文字列エスケープ対象: `"`, `\`, `\n`, `\r`, `\t`(それ以外の制御文字のエスケープは意図的にスコープ外)
- dueDate は `LocalDate.toString()`(ISO-8601、`yyyy-MM-dd`)を JSON 文字列として格納。null は `null` リテラル
- priority は `Priority.name()`(`LOW`/`MEDIUM`/`HIGH`)を JSON 文字列として格納
- done/id/createdAt/updatedAt は JSON の真偽値・数値リテラルとして格納(クォート無し)
- デコード失敗(想定外の壊れた JSON)は `BarrelTodoException` を投げる。barrelkv の CRC 検証済みデータが前提であり、ここでの失敗は想定内の制御フローではなくバグ扱いのため `Optional` ではなく例外で表現する(barrelkv の「クラッシュリカバリ時の壊れたレコード検出は `Optional` で表現し例外を使わない」という方針とは対照的だが、barrelkv 側は必ず起こりうる状況を扱うのに対し、こちらはその前提が崩れていること自体が異常、という違いによる)

## 責務の分離(パッケージ構成)
- `io.github.heruoji.barreltodo.model` : `Todo`(Java `record`)、`Priority`(enum: LOW/MEDIUM/HIGH)。純粋なデータ表現
- `io.github.heruoji.barreltodo.json` : `TodoJsonSerializer`。Todo⇔JSON文字列の変換をこの層に閉じ込め、他層にJSON文字列を漏らさない
- `io.github.heruoji.barreltodo.exception` : `BarrelTodoException`。真に予期しない異常専用の非チェック例外
- `io.github.heruoji.barreltodo.repository` : `TodoRepository`。BarrelKv のラップ、`"todo:"` キープレフィックスの管理、ID採番、CRUD
- `io.github.heruoji.barreltodo.cli` : `Main`。対話的 REPL

## コーディング規約
- barrelkv の CLAUDE.md の規約(例外は非チェック例外にラップする、想定内の制御フローには例外を使わず `Optional` 等で表現する、シリアライズ責務を他層に漏らさない)を踏襲する
- `cli.Main.handle(TodoRepository, String)` はパッケージプライベート(barrelkv 本体の `cli.Main` とは異なり `private` にしない)。`cli.MainTest` から直接呼び出して REPL コマンドの挙動をテストするための、意図的な差分
- REPL の1行は `cli.Main.tokenize()` という自前実装の最小トークナイザでコマンド引数に分割する。ダブルクォートで囲んだ範囲は空白を含む1トークンとして扱い、クォート内の `\"` `\\` はエスケープとして解釈する。barrelkv 本体の CLI は単純な空白区切り(`String.split`)だが、barrel-todo の title/description は自然文になりがちで空白を含められないと実用上不便なため、この点は意図的に踏襲していない。閉じクォートがない場合は `IllegalArgumentException` を `handle()` 内で捕捉し、エラーメッセージを表示して処理を中断する(ユーザー入力の構文エラーであり、`BarrelTodoException` の対象である「想定外の異常」ではない)

## テスト方針
- `json`: エンコード/デコードのラウンドトリップ(全フィールドあり・null混在・priority3値)、文字列エスケープ、固定シェイプの回帰テスト、壊れたJSON/不正なpriorityでの例外
- `repository`: `@TempDir` で実際の `BarrelKv` を使う統合テスト。ID採番(初回=1・既存max+1・全削除後の再利用)、`listAll()` が他プレフィックスのキーを無視すること、`close()` → 再オープン後もデータが残ること(barrelkv の keydir 再構築を経由)
- `cli`: `Main.handle(...)` を直接呼び出し、標準出力キャプチャで各コマンドの正常系・異常系を検証

## 既知の制限
- title/description に空白を含めるにはダブルクォートで囲む必要がある(素の空白区切りでは別トークンとして分割されてしまうため)。クォート内でダブルクォート自体やバックスラッシュを使いたい場合は `\"` `\\` でエスケープする
- ID 採番の競合制御なし(単一プロセスの対話ツールという前提)
- 汎用 JSON パーサーではない(Todo の固定シェイプ専用)

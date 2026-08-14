# barrel-todo

[barrelkv](https://github.com/heruoji/barrelkv) をライブラリとして利用したシンプルな ToDo 管理 CLI ツール(個人学習目的)。

barrelkv を Gradle のコンポジットビルド(`includeBuild`)でソースのまま参照しており、Maven リポジトリへの publish は行っていません。詳細な設計は [barrelkv/CLAUDE.md](./barrelkv/CLAUDE.md) を参照してください。

## 特徴

- ToDo の追加 / 一覧表示 / 完了・未完了の切り替え / 削除 / 詳細表示
- title, description, done, dueDate, priority(LOW/MEDIUM/HIGH)を持つ ToDo モデル
- barrelkv の単一 String value に、自前実装の最小限 JSON エンコーダ/デコーダで ToDo の複数フィールドをまとめて保存
- `"todo:"` プレフィックス付きキーを走査して連番 ID を採番(単一プロセスの対話利用が前提。並行アクセスの競合制御はスコープ外)

## 必要環境

- Java 21
- (Gradle Wrapper 同梱、Gradle 自体のインストールは不要)

## 入手方法

barrelkv を git submodule として含んでいるため、`--recurse-submodules` 付きで clone してください。

```bash
git clone --recurse-submodules https://github.com/heruoji/barrel-todo.git
```

既に(submodule なしで)clone してしまった場合は、以下で取得できます。

```bash
git submodule update --init
```

## ビルド・テスト

```bash
./gradlew build
```

## 使い方

`./gradlew run` は毎回 Gradle を起動するため多少時間がかかります。ローカルでさっと使いたい場合は、起動スクリプトを一度ビルドしておくのがおすすめです。

```bash
./gradlew installDist
```

`build/install/barrel-todo/bin/barrel-todo` に実行スクリプトが生成されます(Java 21 があれば Gradle 不要でこのスクリプト単体で動きます)。PATH の通ったディレクトリにシンボリックリンクを張っておけば、以後はターミナルからいつでも `barrel-todo` とだけ打って起動できます。

```bash
ln -s "$(pwd)/build/install/barrel-todo/bin/barrel-todo" /usr/local/bin/barrel-todo
barrel-todo ~/todo-data   # 以後どのディレクトリからでも実行可能
```

barrelkv 側のソースを更新した場合は、`installDist` を実行し直せばスクリプトに反映されます。

Gradle 経由でその場で試す場合は以下の通りです。

```bash
./gradlew run --args="./data"
```

第1引数はデータを保存するディレクトリです(存在しなければ自動作成されます)。省略するとカレントディレクトリの `barrel-todo-data/` が使われます。第2引数(省略可)は barrelkv の1ファイルあたりの最大サイズ(バイト、既定 10MB)です。

起動するとプロンプト `>` が表示され、以下のコマンドを対話的に入力できます。

| コマンド | 説明 |
|---|---|
| `add <title> [description\|-] [dueDate\|-] [priority\|-]` | ToDo を追加。`description`/`dueDate`/`priority` は省略時 `-` で「値なし」を表す。`dueDate` は `yyyy-MM-dd`、`priority` は `LOW`/`MEDIUM`/`HIGH`(省略時 `MEDIUM`) |
| `list` | 全 ToDo を一覧表示(`[x] id [priority] title (due date)`) |
| `show <id>` | 指定 ID の ToDo の全フィールドを表示 |
| `done <id>` / `undone <id>` | 完了/未完了に切り替え |
| `delete <id>` | 削除 |
| `help` | コマンド一覧を表示 |
| `exit` / `quit` | 終了(データは保存済みなので、次回起動時にも残っています) |

title / description に空白を含めたい場合はダブルクォートで囲んでください(例: `add "Buy milk" "2% or whole" - HIGH`)。クォート内でダブルクォートやバックスラッシュ自体を使いたい場合は `\"` `\\` でエスケープします。閉じクォートを書き忘れると `unterminated quote` エラーになります。

## 試してみる

```bash
./gradlew run --args="./data"
```

```
barrel-todo CLI - data dir: /path/to/data
commands: add <title> [description|-] [dueDate|-] [priority|-] | list | done <id> | undone <id> | delete <id> | show <id> | help | exit
tip: wrap title/description in double quotes to include spaces, e.g. add "Buy milk" "2% or whole" - HIGH
> add "Buy milk" "2% or whole" 2026-08-20 HIGH
OK (id=1)
> list
[ ] 1 [HIGH] Buy milk (due 2026-08-20)
> done 1
OK
> show 1
id: 1
title: Buy milk
description: 2% or whole
done: true
dueDate: 2026-08-20
priority: HIGH
createdAt: 1755151200000
updatedAt: 1755151300000
> delete 1
OK
> exit
```

## データの持ち方

- キー: `todo:<id>`(barrelkv 自体はプレフィックスを意識しない)
- 値: ToDo を表す固定シェイプの JSON 文字列

```json
{"id":1,"title":"Buy milk","description":null,"done":false,"dueDate":"2026-08-20","priority":"HIGH","createdAt":1755151200000,"updatedAt":1755151300000}
```

## パッケージ構成

| パッケージ | 責務 |
|---|---|
| `model` | ToDo / Priority のデータ表現 |
| `json` | ToDo ⇔ JSON のシリアライズ/デシリアライズ |
| `exception` | 想定外の異常専用の非チェック例外 |
| `repository` | barrelkv をラップした CRUD・ID 採番・キープレフィックス管理 |
| `cli` | 対話的に試すための簡易 REPL |

## 既知の制限

- title / description に空白を含めるにはダブルクォートで囲む必要があります(素の空白区切りでは別トークンとして分割されてしまうため)
- ID 採番は毎回キーをスキャンして max+1 を採用するため、全件削除後は 1 から再利用されます
- 並行アクセスは考慮していません(単一プロセスの対話ツールという前提のため)

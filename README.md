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

## 試してみる

```bash
./gradlew run --args="./data"
```

```
barrel-todo CLI - data dir: /path/to/data
commands: add <title> [description|-] [dueDate|-] [priority|-] | list | done <id> | undone <id> | delete <id> | show <id> | help | exit
> add Buy_milk - 2026-08-20 HIGH
OK (id=1)
> list
[ ] 1 [HIGH] Buy_milk (due 2026-08-20)
> done 1
OK
> show 1
id: 1
title: Buy_milk
description: (none)
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

- title / description は空白を含められません(barrelkv 本体の CLI と同じ、単純な空白区切りパーサーのため)
- ID 採番は毎回キーをスキャンして max+1 を採用するため、全件削除後は 1 から再利用されます
- 並行アクセスは考慮していません(単一プロセスの対話ツールという前提のため)

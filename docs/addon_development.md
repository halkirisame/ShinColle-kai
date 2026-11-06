# アドオン制作の入口

[English](addon_development.en.md) · 日本語版が正です。

対象: ShinColle-kai **1.20.1-0.13.0**、Minecraft **1.20.1 / Forge**（開発環境: Forge 47.4.0、Java 17）。

## できること

- **標準艦**: 組み込みの AI・攻撃を使う艦を、JSON とモデル・画像で追加できます。Java は不要です。
- **装備**: データパックで既存アイテムを艦の装備として定義できます。
- **Java / KubeJS**: 公開されている装備 API、独自属性、所有者判定や KubeJS 連携を利用できます。

独自の AI・攻撃の登録と、専用 entity が必要な特殊艦には、この版では対応していません。

## 道案内

| 作りたいもの | 文書・見本 |
|---|---|
| 標準艦 | [艦データと見た目の制作](ship_addon_data.md)、[コピーできる見本](../examples/ship_addon/README.md) |
| 装備データパック | [装備の見本と手順](../examples/equipment_datapack/README.md)、[装備 Schema](schemas/shincolle_kai-equipment.schema.json) |
| Java の装備アドオン | [公開 Java API](java_addon_api.md)、[Java の見本](../examples/java_addon/README.md) |
| KubeJS | [KubeJS 連携](kubejs_integration.md) |

艦のデータパックはサーバー、見た目のリソースパックは各クライアントに入れます。シングルプレイでも両方が必要です。

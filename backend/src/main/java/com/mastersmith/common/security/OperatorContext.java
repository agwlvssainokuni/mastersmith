package com.mastersmith.common.security;

import java.util.Optional;

/**
 * リクエストの処理中の操作者を返す、読み取り専用のインタフェース(C15、機能設計BR5.12)。
 *
 * <p>authentication-serviceの業務ロジックに依存しない、中立の共有契約(共通基盤の契約、shared kernel)である。値を設定する側の実装は
 * authentication-service({@code SecurityContextOperatorContext})が提供し、他ユニットは、この型だけを読む(authentication-serviceの コンポーネントを呼ばない)。この契約の変更には、
 * authentication-serviceと、すべての読み取り側のユニットの合意を要する。
 *
 * <p>ヘッダー({@code X-User-Id}・{@code X-Active-Role-Id}など)から操作者を導く実装は、どのプロファイルにも置かない。
 */
public interface OperatorContext {

  /** 現在のリクエストの操作者。認証されていない(解決できない)場合は空。 */
  Optional<Operator> current();
}

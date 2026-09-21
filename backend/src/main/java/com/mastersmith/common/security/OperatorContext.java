/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mastersmith.common.security;

import java.util.Optional;

/**
 * リクエストの処理中の操作者を返す、読み取り専用のインタフェース(C15、機能設計BR5.12)。
 *
 * <p>authentication-serviceの業務ロジックに依存しない、中立の共有契約(共通基盤の契約、shared kernel)である。値を設定する側の実装は
 * authentication-service({@code
 * SecurityContextOperatorContext})が提供し、他ユニットは、この型だけを読む(authentication-serviceの
 * コンポーネントを呼ばない)。この契約の変更には、 authentication-serviceと、すべての読み取り側のユニットの合意を要する。
 *
 * <p>ヘッダー({@code X-User-Id}・{@code X-Active-Role-Id}など)から操作者を導く実装は、どのプロファイルにも置かない。
 */
public interface OperatorContext {

  /** 現在のリクエストの操作者。認証されていない(解決できない)場合は空。 */
  Optional<Operator> current();
}

package com.mastersmith.auth;

import com.mastersmith.auth.exception.SessionExpiredException;
import com.mastersmith.auth.exception.SessionNotFoundException;

/**
 * authentication-service(U5)がlist-engine・record-edit-engineへ提供する内部Javaインタフェース契約(inception/contract-design/contract-summary.md
 * C14とその追補)。C4(frontend-ui向けREST)とは別の境界。
 *
 * <p>list-engine・record-edit-engineが、リクエスト処理時にSessionのアクティブロールを読み取り、permission-engine(C10)呼び出しの引数として渡すために、同一
 * プロセス内で呼び出す。authentication-serviceは、permission-engineを直接呼び出さない。
 */
public interface SessionContextApi {

  /**
   * 指定のSessionのアクティブロールIDを返す(FR4.2、BR5.13)。
   *
   * @return アクティブロールID。<b>未選択の場合はnull</b>(呼び出し元は、nullをそのままC10へ渡し、権限なしとして判定させる。BR5.12)
   * @throws SessionNotFoundException Sessionが存在しない場合
   * @throws SessionExpiredException Sessionが有効でない場合(BR5.11の定義: revoked、またはリフレッシュの有効期限の経過)
   * @throws com.mastersmith.auth.exception.AuthStorageUnavailableException 内部設定DBの障害で、Sessionを確認できない場合(503に変換される)
   */
  String getActiveRoleId(String sessionId);
}

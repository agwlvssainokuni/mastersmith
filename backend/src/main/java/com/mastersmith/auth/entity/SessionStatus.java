package com.mastersmith.auth.entity;

/**
 * Sessionの状態(entities.md)。{@code ACTIVE}は有効、{@code REVOKED}は失効済み(ログアウト・無効になったトークンの再使用・ユーザーの無効化の検知)。
 * 失効したSessionは、再び{@code ACTIVE}に戻らない。リフレッシュの有効期限の経過は、状態を変えず、時刻の比較で判定する。
 */
public enum SessionStatus {
  ACTIVE,
  REVOKED
}

package com.mastersmith.auth.observation;

/**
 * 認証フィルタが401を返した原因の分類(NFR5.1)。値が4つに固定された列挙型で、任意の文字列を、メトリクスのタグに使えない。応答には出さない。
 */
public enum UnauthorizedReason {
  /** {@code Authorization}ヘッダーがない、またはBearerでない。 */
  MISSING("missing"),
  /** トークンの解析・署名・必須の値・{@code sub}の照合の失敗。 */
  INVALID("invalid"),
  /** {@code exp}の経過。 */
  EXPIRED("expired"),
  /** Sessionが存在しない、または有効でない。 */
  SESSION_INACTIVE("session_inactive");

  private final String tag;

  UnauthorizedReason(String tag) {
    this.tag = tag;
  }

  /** メトリクスのタグの値・ログの分類。 */
  public String tag() {
    return tag;
  }
}

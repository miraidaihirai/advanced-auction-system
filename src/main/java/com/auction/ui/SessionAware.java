package com.auction.ui;

import com.auction.session.ClientSession;

/** Interface dành cho các Controller cần sử dụng ClientSession chung của ứng dụng. */
public interface SessionAware {

  void setClientSession(ClientSession clientSession);

  /** Dọn dẹp listener / tài nguyên khi view bị xóa khỏi cache (ví dụ khi đăng xuất). */
  default void dispose() {}
}

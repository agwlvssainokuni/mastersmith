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

package com.mastersmith.usermanagement.testsupport;

import java.util.Collections;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** {@code Content-Length}のない(チャンク転送の)リクエストを、MockMvcで再現するための{@link RequestPostProcessor}。 */
public final class ChunkedRequests {

  private ChunkedRequests() {}

  public static RequestPostProcessor chunked() {
    return request -> {
      MockHttpServletRequest chunked =
          new MockHttpServletRequest(
              request.getServletContext(), request.getMethod(), request.getRequestURI()) {
            @Override
            public int getContentLength() {
              return -1;
            }

            @Override
            public long getContentLengthLong() {
              return -1L;
            }
          };
      chunked.setContent(request.getContentAsByteArray());
      chunked.setContextPath(request.getContextPath());
      chunked.setServletPath(request.getServletPath());
      chunked.setPathInfo(request.getPathInfo());
      chunked.setQueryString(request.getQueryString());
      chunked.setContentType(request.getContentType());
      chunked.setCharacterEncoding(request.getCharacterEncoding());
      request.getParameterMap().forEach(chunked::addParameter);
      for (String name : Collections.list(request.getHeaderNames())) {
        for (String value : Collections.list(request.getHeaders(name))) {
          if (!name.equalsIgnoreCase("Content-Length")) {
            chunked.addHeader(name, value);
          }
        }
      }
      return chunked;
    };
  }
}

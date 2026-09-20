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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.event.UserChangedEventPublisher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** {@link UserChangedEventPublisher}のモックで、発行されたイベントを記録する(複数スレッドから使える)。 */
public final class EventRecorder {

  private final List<UserChangedEvent> events = Collections.synchronizedList(new ArrayList<>());
  private final UserChangedEventPublisher publisher = mock(UserChangedEventPublisher.class);

  public EventRecorder() {
    this(event -> {});
  }

  /** 発行の瞬間に、追加の確認({@code onPublish})を実行する。 */
  public EventRecorder(Consumer<UserChangedEvent> onPublish) {
    doAnswer(
            invocation -> {
              UserChangedEvent event = invocation.getArgument(0);
              onPublish.accept(event);
              events.add(event);
              return null;
            })
        .when(publisher)
        .publish(any(UserChangedEvent.class));
  }

  public UserChangedEventPublisher publisher() {
    return publisher;
  }

  public List<UserChangedEvent> events() {
    synchronized (events) {
      return List.copyOf(events);
    }
  }

  public UserChangedEvent single() {
    List<UserChangedEvent> copy = events();
    if (copy.size() != 1) {
      throw new AssertionError("expected exactly one event but was " + copy.size());
    }
    return copy.get(0);
  }
}

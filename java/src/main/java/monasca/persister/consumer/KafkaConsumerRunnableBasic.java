/*
 * Copyright (c) 2014 Hewlett-Packard Development Company, L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monasca.persister.consumer;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kafka.consumer.ConsumerIterator;
import monasca.persister.pipeline.ManagedPipeline;

public class KafkaConsumerRunnableBasic<T> implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerRunnableBasic.class);

  private final KafkaChannel kafkaChannel;
  private final String threadId;
  private final ManagedPipeline<T> pipeline;
  private volatile boolean stop = false;


  @Inject
  public KafkaConsumerRunnableBasic(
      @Assisted KafkaChannel kafkaChannel,
      @Assisted ManagedPipeline<T> pipeline,
      @Assisted String threadId) {

    this.kafkaChannel = kafkaChannel;
    this.pipeline = pipeline;
    this.threadId = threadId;
  }

  protected void publishHeartbeat() {
    publishEvent(null);
  }

  protected void handleMessage(String msg) {

      publishEvent(msg);

  }

  private void markRead() {

    logger.debug("[{}]: marking read", this.threadId);

    this.kafkaChannel.markRead();
  }

  public void stop() {

    logger.info("[{}]: stop", this.threadId);

    this.stop = true;

    this.pipeline.shutdown();

  }

  public void run() {

    logger.info("[{}]: run", this.threadId);

    final ConsumerIterator<byte[], byte[]> it = kafkaChannel.getKafkaStream().iterator();

    logger.debug("[{}]: KafkaChannel has stream iterator", this.threadId);

    while (!this.stop) {

      try {

        if (it.hasNext()) {

          final String msg = new String(it.next().message());

          logger.debug("[{}]: {}", this.threadId, msg);

          handleMessage(msg);

        }

      } catch (kafka.consumer.ConsumerTimeoutException cte) {

        publishHeartbeat();

      }
    }

    logger.info("[{}]: shutting down", this.threadId);

    this.kafkaChannel.stop();
  }

  protected void publishEvent(final String msg) {

    if (pipeline.publishEvent(msg)) {

      markRead();

    }
  }
}

/*
 * (C) Copyright 2014-2016 Hewlett Packard Enterprise Development Company LP
 * Copyright (c) 2017 FUJITSU LIMITED
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

package monasca.persister.repository.influxdb;

import monasca.common.model.event.AlarmStateTransitionedEvent;

import com.google.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.dropwizard.setup.Environment;
import monasca.persister.repository.RepoException;

public class InfluxV9AlarmRepo extends InfluxAlarmRepo {

  private static final Logger logger = LoggerFactory.getLogger(InfluxV9AlarmRepo.class);

  private final InfluxV9RepoWriter influxV9RepoWriter;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Inject
  public InfluxV9AlarmRepo(
      final Environment env,
      final InfluxV9RepoWriter influxV9RepoWriter) {

    super(env);

    this.influxV9RepoWriter = influxV9RepoWriter;

    this.objectMapper.setPropertyNamingStrategy(
        PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
  }

  @Override
  protected int write(String id) throws RepoException {

    return this.influxV9RepoWriter.write(getInfluxPointArry(id), id);

  }

  private InfluxPoint[] getInfluxPointArry(String id) {

    List<InfluxPoint> influxPointList = new LinkedList<>();

    for (AlarmStateTransitionedEvent event : this.alarmStateTransitionedEventList) {

      Map<String, Object> valueMap = new HashMap<>();

      Map<String, String> tags = new HashMap<>();

      if (event.tenantId == null) {

        logger.error("[{}]: tenant id cannot be null. Dropping alarm state history event.", id);

        continue;

      } else {

          tags.put("tenant_id", event.tenantId);
      }

      if (event.alarmId == null) {

        logger.error("[{}]: alarm id cannot be null. Dropping alarm state history event.", id);

        continue;

      } else {

          tags.put("alarm_id", event.alarmId);
      }

      if (event.metrics == null) {

        logger.error("[{}]: metrics cannot be null. Settings metrics to empty JSON", id);

        valueMap.put("metrics", "\"{}\"");

      } else {

        try {

          valueMap.put("metrics",
                  new StringBuilder().append('"').
                  append(this.objectMapper.writeValueAsString(event.metrics)).
                  append('"').toString());

        } catch (JsonProcessingException e) {

          logger.error("[{}]: failed to serialize metrics {}", id, event.metrics, e);
          logger.error("[{}]: setting metrics to empty JSON", id);

          valueMap.put("metrics", "\"{}\"");

        }
      }

      if (event.oldState == null) {

        logger.error("[{}]: old state cannot be null. Setting old state to empty string.", id);

        valueMap.put("old_state", "\"\"");

      } else {

        valueMap.put("old_state",
                new StringBuilder().append('"').append(event.oldState).append('"').toString());

      }

      if (event.newState == null) {

        logger.error("[{}]: new state cannot be null. Setting new state to empty string.", id);

        valueMap.put("new_state", "\"\"");

      } else {

        valueMap.put("new_state",
                new StringBuilder().append('"').append(event.newState).append('"').toString());

      }

      if (event.link == null) {

        valueMap.put("link", "\"\"");

      } else {

        valueMap.put("link",
                new StringBuilder().append('"').append(event.link).append('"').toString());
      }

      if (event.lifecycleState == null) {

        valueMap.put("lifecycle_state", "\"\"");

      } else {

        valueMap.put("lifecycle_state",
                new StringBuilder().append('"').append(event.lifecycleState).
                append('"').toString());
      }

      if (event.subAlarms == null) {

        logger.debug("[{}]: sub alarms is null. Setting sub alarms to empty JSON", id);

        valueMap.put("sub_alarms", "\"[]\"");

      } else {

        try {

          valueMap.put("sub_alarms",
                  new StringBuilder().append('"').
                  append(this.objectMapper.writeValueAsString(event.subAlarms)).
                  append('"').toString());

        } catch (JsonProcessingException e) {

          logger.error("[{}]: failed to serialize sub alarms {}", id, event.subAlarms, e);
          logger.error("[{}]: Setting sub_alarms to empty JSON", id);

          valueMap.put("sub_alarms", "\"[]\"");

        }

      }

      if (event.stateChangeReason == null) {

        logger.error("[{}]: reason cannot be null. Setting reason to empty string.", id);

        valueMap.put("reason", "\"\"");

      } else {

        valueMap.put("reason",
                new StringBuilder().append('"').append(event.stateChangeReason).
                append('"').toString());
      }

      valueMap.put("reason_data", "\"{}\"");

      Long dateTime = new DateTime(event.timestamp, DateTimeZone.UTC).getMillis();

      InfluxPoint
          influxPoint =
          new InfluxPoint(ALARM_STATE_HISTORY_NAME, tags, dateTime, valueMap);

      influxPointList.add(influxPoint);

    }

    return influxPointList.toArray(new InfluxPoint[influxPointList.size()]);
  }
}

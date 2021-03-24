/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.soul.admin.service.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.dromara.soul.admin.entity.PluginDO;
import org.dromara.soul.admin.entity.SelectorDO;
import org.dromara.soul.admin.listener.DataChangedEvent;
import org.dromara.soul.admin.mapper.PluginMapper;
import org.dromara.soul.admin.mapper.SelectorConditionMapper;
import org.dromara.soul.admin.mapper.SelectorMapper;
import org.dromara.soul.admin.query.SelectorConditionQuery;
import org.dromara.soul.admin.transfer.ConditionTransfer;
import org.dromara.soul.common.concurrent.SoulThreadFactory;
import org.dromara.soul.common.dto.ConditionData;
import org.dromara.soul.common.dto.SelectorData;
import org.dromara.soul.common.dto.convert.DivideUpstream;
import org.dromara.soul.common.dto.convert.ZombieUpstream;
import org.dromara.soul.common.enums.ConfigGroupEnum;
import org.dromara.soul.common.enums.DataEventTypeEnum;
import org.dromara.soul.common.enums.PluginEnum;
import org.dromara.soul.common.utils.GsonUtils;
import org.dromara.soul.common.utils.UpstreamCheckUtils;
import org.dromara.soul.register.common.config.SoulRegisterCenterConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * this is divide  http url upstream.
 *
 * @author xiaoyu
 */
@Slf4j
@Component
public class UpstreamCheckService {

    private static final Map<String, List<DivideUpstream>> UPSTREAM_MAP = Maps.newConcurrentMap();

    private static final Set<ZombieUpstream> ZOMBIE_SET = Sets.newConcurrentHashSet();

    private int zombieCheckTimes;

    private int scheduledTime;

    private String registerType;

    private boolean checked;

    private final SelectorMapper selectorMapper;

    private final ApplicationEventPublisher eventPublisher;

    private final PluginMapper pluginMapper;

    private final SelectorConditionMapper selectorConditionMapper;
    
    /**
     * Instantiates a new Upstream check service.
     *
     * @param selectorMapper the selector mapper
     * @param eventPublisher the event publisher
     * @param pluginMapper the plugin mapper
     * @param selectorConditionMapper the selectorCondition mapper
     * @param soulRegisterCenterConfig the soul register center config
     */
    @Autowired(required = false)
    public UpstreamCheckService(final SelectorMapper selectorMapper, final ApplicationEventPublisher eventPublisher,
                                final PluginMapper pluginMapper, final SelectorConditionMapper selectorConditionMapper,
                                final SoulRegisterCenterConfig soulRegisterCenterConfig) {
        this.selectorMapper = selectorMapper;
        this.eventPublisher = eventPublisher;
        this.pluginMapper = pluginMapper;
        this.selectorConditionMapper = selectorConditionMapper;
        Properties props = soulRegisterCenterConfig.getProps();
        this.checked = Boolean.parseBoolean(props.getProperty("checked", "true"));
        this.zombieCheckTimes = Integer.parseInt(props.getProperty("zombieCheckTimes", "5"));
        this.scheduledTime = Integer.parseInt(props.getProperty("scheduledTime", "10"));
        this.registerType = soulRegisterCenterConfig.getRegisterType();
        if ("http".equalsIgnoreCase(registerType)) {
            setup();
        }
    }
    
    /**
     * Set up.
     */
    public void setup() {
        if (checked) {
            PluginDO pluginDO = pluginMapper.selectByName(PluginEnum.DIVIDE.getName());
            if (pluginDO != null) {
                List<SelectorDO> selectorDOList = selectorMapper.findByPluginId(pluginDO.getId());
                for (SelectorDO selectorDO : selectorDOList) {
                    List<DivideUpstream> divideUpstreams = GsonUtils.getInstance().fromList(selectorDO.getHandle(), DivideUpstream.class);
                    if (CollectionUtils.isNotEmpty(divideUpstreams)) {
                        UPSTREAM_MAP.put(selectorDO.getName(), divideUpstreams);
                    }
                }
            }
            new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), SoulThreadFactory.create("scheduled-upstream-task", false))
                    .scheduleWithFixedDelay(this::scheduled, 10, scheduledTime, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Remove by key.
     *
     * @param selectorName the selector name
     */
    public static void removeByKey(final String selectorName) {
        UPSTREAM_MAP.remove(selectorName);
    }
    
    /**
     * Submit.
     *
     * @param selectorName the selector name
     * @param divideUpstream the divide upstream
     */
    public void submit(final String selectorName, final DivideUpstream divideUpstream) {
        if (!"http".equalsIgnoreCase(registerType)) {
            return;
        }
        if (UPSTREAM_MAP.containsKey(selectorName)) {
            List<DivideUpstream> upstreams = UPSTREAM_MAP.getOrDefault(selectorName, Collections.emptyList());
            Optional<DivideUpstream> exists = upstreams.stream().filter(item -> StringUtils.isNotBlank(item.getUpstreamUrl())
                    && item.getUpstreamUrl().equals(divideUpstream.getUpstreamUrl())).findFirst();
            if (!exists.isPresent()) {
                upstreams.add(divideUpstream);
            } else {
                log.info("upstream host {} is exists.", divideUpstream.getUpstreamHost());
            }
        } else {
            UPSTREAM_MAP.put(selectorName, Lists.newArrayList(divideUpstream));
        }
    }
    
    /**
     * Replace.
     *
     * @param selectorName the selector name
     * @param divideUpstreams the divide upstream list
     */
    public void replace(final String selectorName, final List<DivideUpstream> divideUpstreams) {
        if (!"http".equalsIgnoreCase(registerType)) {
            return;
        }
        UPSTREAM_MAP.put(selectorName, divideUpstreams);
    }

    private void scheduled() {
        try {
            if (ZOMBIE_SET.size() > 0) {
                ZOMBIE_SET.forEach(this::checkZombie);
            }
            if (UPSTREAM_MAP.size() > 0) {
                UPSTREAM_MAP.forEach(this::check);
            }
        } catch (Exception e) {
            log.error("upstream scheduled check error -------- ", e);
        }
    }

    private void checkZombie(final ZombieUpstream zombieUpstream) {
        ZOMBIE_SET.remove(zombieUpstream);
        String selectorName = zombieUpstream.getSelectorName();
        DivideUpstream divideUpstream = zombieUpstream.getDivideUpstream();
        final boolean pass = UpstreamCheckUtils.checkUrl(divideUpstream.getUpstreamUrl());
        if (pass) {
            divideUpstream.setTimestamp(System.currentTimeMillis());
            divideUpstream.setStatus(true);
            log.info("UpstreamCacheManager check zombie upstream success the url: {}, host: {} ", divideUpstream.getUpstreamUrl(), divideUpstream.getUpstreamHost());
            List<DivideUpstream> old = ListUtils.unmodifiableList(UPSTREAM_MAP.getOrDefault(selectorName, Collections.emptyList()));
            this.submit(selectorName, divideUpstream);
            updateHandler(selectorName, old, UPSTREAM_MAP.get(selectorName));
        } else {
            log.error("check zombie upstream the url={} is fail", divideUpstream.getUpstreamUrl());
            if (zombieUpstream.getZombieCheckTimes() > NumberUtils.INTEGER_ZERO) {
                zombieUpstream.setZombieCheckTimes(zombieUpstream.getZombieCheckTimes() - NumberUtils.INTEGER_ONE);
                ZOMBIE_SET.add(zombieUpstream);
            }
        }
    }

    private void check(final String selectorName, final List<DivideUpstream> upstreamList) {
        List<DivideUpstream> successList = Lists.newArrayListWithCapacity(upstreamList.size());
        for (DivideUpstream divideUpstream : upstreamList) {
            final boolean pass = UpstreamCheckUtils.checkUrl(divideUpstream.getUpstreamUrl());
            if (pass) {
                if (!divideUpstream.isStatus()) {
                    divideUpstream.setTimestamp(System.currentTimeMillis());
                    divideUpstream.setStatus(true);
                    log.info("UpstreamCacheManager check success the url: {}, host: {} ", divideUpstream.getUpstreamUrl(), divideUpstream.getUpstreamHost());
                }
                successList.add(divideUpstream);
            } else {
                divideUpstream.setStatus(false);
                ZOMBIE_SET.add(ZombieUpstream.transform(divideUpstream, zombieCheckTimes, selectorName));
                log.error("check the url={} is fail ", divideUpstream.getUpstreamUrl());
            }
        }
        updateHandler(selectorName, upstreamList, successList);
    }

    private void updateHandler(final String selectorName, final List<DivideUpstream> upstreamList, final List<DivideUpstream> successList) {
        //No node changes, including zombie node resurrection and live node death
        if (successList.size() == upstreamList.size()) {
            return;
        }
        if (successList.size() > 0) {
            UPSTREAM_MAP.put(selectorName, successList);
            updateSelectorHandler(selectorName, successList);
        } else {
            UPSTREAM_MAP.remove(selectorName);
            updateSelectorHandler(selectorName, null);
        }
    }

    private void updateSelectorHandler(final String selectorName, final List<DivideUpstream> upstreams) {
        SelectorDO selectorDO = selectorMapper.selectByName(selectorName);
        if (Objects.nonNull(selectorDO)) {
            List<ConditionData> conditionDataList = ConditionTransfer.INSTANCE.mapToSelectorDOS(
                    selectorConditionMapper.selectByQuery(new SelectorConditionQuery(selectorDO.getId())));
            PluginDO pluginDO = pluginMapper.selectById(selectorDO.getPluginId());
            String handler = CollectionUtils.isEmpty(upstreams) ? "" : GsonUtils.getInstance().toJson(upstreams);
            selectorDO.setHandle(handler);
            selectorMapper.updateSelective(selectorDO);
            if (Objects.nonNull(pluginDO)) {
                SelectorData selectorData = SelectorDO.transFrom(selectorDO, pluginDO.getName(), conditionDataList);
                selectorData.setHandle(handler);
                // publish change event.
                eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.SELECTOR, DataEventTypeEnum.UPDATE, Collections.singletonList(selectorData)));
            }
        }
    }
}

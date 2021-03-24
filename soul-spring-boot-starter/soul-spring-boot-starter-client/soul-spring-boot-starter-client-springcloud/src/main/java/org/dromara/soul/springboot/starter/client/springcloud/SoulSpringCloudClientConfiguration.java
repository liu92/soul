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

package org.dromara.soul.springboot.starter.client.springcloud;

import org.dromara.soul.client.springcloud.init.ContextRegisterListener;
import org.dromara.soul.client.springcloud.init.SpringCloudClientBeanPostProcessor;
import org.dromara.soul.register.client.api.SoulClientRegisterRepository;
import org.dromara.soul.register.common.config.SoulRegisterCenterConfig;
import org.dromara.soul.springboot.starter.client.common.config.SoulClientCommonBeanConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The type Soul spring cloud client configuration.
 *
 * @author xiaoyu
 */
@Configuration
@ImportAutoConfiguration(SoulClientCommonBeanConfiguration.class)
public class SoulSpringCloudClientConfiguration {
    
    /**
     * Spring cloud client bean post processor spring cloud client bean post processor.
     *
     * @param config the config
     * @param env    the env
     * @param soulClientRegisterRepository the soulClientRegisterRepository
     * @return the spring cloud client bean post processor
     */
    @Bean
    public SpringCloudClientBeanPostProcessor springCloudClientBeanPostProcessor(final SoulRegisterCenterConfig config, final Environment env,
                                                                                 final SoulClientRegisterRepository soulClientRegisterRepository) {
        return new SpringCloudClientBeanPostProcessor(config, env, soulClientRegisterRepository);
    }
    
    /**
     * Context register listener context register listener.
     *
     * @param config the config
     * @param env    the env
     * @param soulClientRegisterRepository the soulClientRegisterRepository
     * @return the context register listener
     */
    @Bean
    public ContextRegisterListener contextRegisterListener(final SoulRegisterCenterConfig config, final Environment env,
                                                           final SoulClientRegisterRepository soulClientRegisterRepository) {
        return new ContextRegisterListener(config, env, soulClientRegisterRepository);
    }
}

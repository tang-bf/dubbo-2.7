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
package org.apache.dubbo.config.spring.context.annotation;

import org.apache.dubbo.config.AbstractConfig;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Set;

import static com.alibaba.spring.util.AnnotatedBeanDefinitionRegistryUtils.registerBeans;
import static java.util.Collections.singleton;
import static org.apache.dubbo.config.spring.util.DubboBeanUtils.registerCommonBeans;

/**
 * Dubbo {@link AbstractConfig Config} {@link ImportBeanDefinitionRegistrar register}, which order can be configured
 *
 * @see EnableDubboConfig
 * @see DubboConfigConfiguration
 * @see Ordered
 * @since 2.5.8
 */
public class DubboConfigConfigurationRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableDubboConfig.class.getName()));
        // EnableDubboConfig  multiple默认是true
        boolean multiple = attributes.getBoolean("multiple");
    //multiple 能多配置还是单配置  dubbo.protocols.p1.id=dubbo1 还是 dubbo.protocol.id=
        //class Single {  prefix = "dubbo.application", type = ApplicationConfig.class
        //class Multiple {  prefix = "dubbo.registries", type = RegistryConfig.class, multiple = true
        // Single Config Bindings
        //EnableConfigurationBeanBindings （ConfigurationBeanBindingsRegistrar  内部还是利用ConfigurationBeanBindingRegistrar）
        // EnableConfigurationBeanBinding（ConfigurationBeanBindingRegistrar）
//
        // 这个两个注解类需要注意

        /*@Import(ConfigurationBeanBindingRegistrar.class) 实现了importbenadefinitionregister
           会根据single还是multiple注册单例还是多个，同时注册ConfigurationBeanBindingPostProcessor 是一个beanpostprocessor
           multiple  true false 的区别  名字的区别 根据multiple 判断是否从属性配置中获取bean的名字
           如果为false 看有没有配置id属性，
           如果没有则自动生成一个beanname
           以demo-provider-annotation为例，解析到dubbo.application.name 时候判断没id则生成》》》》》
           	id = generatedBeanName + GENERATED_BEAN_NAME_SEPARATOR + counter;
           	如果是dubbo.protocols.p1.name=dubbo 那bean的名字就是取得p1
            org.apache.dubbo.config.ApplicationConfig#0
                 Set<String> beanNames = multiple ? resolveMultipleBeanNames(configurationProperties) :
//                singleton(resolveSingleBeanName(configurationProperties, configClass, registry));
29f分支
         *///先注册一个bean dubboConfigConfiguration.Single
        registerBeans(registry, DubboConfigConfiguration.Single.class);

        if (multiple) { // Since 2.6.6 https://github.com/apache/dubbo/issues/3193
            //调用到spring源码中 注册bdmap中beanDefinitionMap
            registerBeans(registry, DubboConfigConfiguration.Multiple.class);
        }

        // Since 2.7.6
        registerCommonBeans(registry);
    }
}

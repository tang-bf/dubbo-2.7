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

import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.apache.dubbo.config.spring.beans.factory.annotation.ServiceAnnotationBeanPostProcessor;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.apache.dubbo.config.spring.util.DubboBeanUtils.registerCommonBeans;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;

/**
 * Dubbo {@link DubboComponentScan} Bean Registrar
 *
 * @see Service
 * @see DubboComponentScan
 * @see ImportBeanDefinitionRegistrar
 * @see ServiceAnnotationBeanPostProcessor
 * @see ReferenceAnnotationBeanPostProcessor
 * @since 2.5.7
 */

public class DubboComponentScanRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        Set<String> packagesToScan = getPackagesToScan(importingClassMetadata);
        //  注册一个ServiceAnnotationBeanPostProcessor  实现了BeanDefinitionRegistryPostProcessor
        //只处理service dubboservice

        // registerInfrastructureBean(registry, DubboBootstrapApplicationListener.BEAN_NAME, DubboBootstrapApplicationListener.class);
        /*二者都继承了一个OneTimeExecutionApplicationContextEventListener  implments ApplicationListener
        同时会注册一个servicebean   见源码registerServiceBeans(resolvedPackagesToScan, registry);
            servicebean中会监听contextrefershed事件 一旦spring启动完成后就会导出服务
            // registerCommonBeans 中会注册ReferenceAnnotationBeanPostProcessor（BeanPostProcessor）
            //寻找被reference标注的field
            // InjectionMetadata metadata = findInjectionMetadata(beanName, bean.getClass(), pvs);
            //        try {
            //            metadata.inject(bean, beanName, pvs);
        //  会调用element.inject(target, beanName, pvs);  生成referencebean 对象
        // 可以在属性上面加@reference注解  也可以在方法上加此注解  和spring类似
        //那么最终执行的inject 就是对应的Filed.set（）AnnotatedFieldElement (alibaba包下面)或者method.invoke AnnotatedMethodElement  进行属性填充
        Object injectedObject = getInjectedObject(attributes, bean, beanName, injectedType, this);
        》》》》》
      servicebean:org.apache.dubbo.demo.DemoService#source=private org.apache...Demoservice org...DemoServiceComponet.demoservice#attributes={}
        String cacheKey = buildInjectedObjectCacheKey(attributes, bean, beanName, injectedType, injectedElement);
        cachekey有点鸡肋  属性名不一样的时候不能缓存  在一个service中@reference两次同一个服务缓存拿不到

        Object injectedObject = injectedObjectsCache.get(cacheKey);

        if (injectedObject == null) {
                   生成referencebean
            injectedObject = doGetInjectedBean(attributes, bean, beanName, injectedType, injectedElement);

            >>>调用   ReferenceAnnotationBeanPostProcessor  doGetInjectedBean
           The name of bean that is declared by {@link Reference @Reference} annotation injection
          String referenceBeanName = getReferenceBeanName(attributes, injectedType)
          @Reference org.apache.dubbo.demo.DemoService (配置了ID的话就拿id作为名字)
          》》》》》 registerReferenceBean(referencedBeanName, referenceBean, attributes, injectedType);
          区分是要引入的服务是本地提供的一个服务还是远程服务
          调用本地服务的时候是不用属性赋值为servicebean的，拿的是servicebean的ref属性值对应的beanname
            // Customized inject-object if necessary
            injectedObjectsCache.putIfAbsent(cacheKey, injectedObject);
        }
        registerReferenceBean(referencedBeanName, referenceBean, attributes, injectedType);

        cacheInjectedReferenceBean(referenceBean, injectedElement);

        return getOrCreateProxy(referencedBeanName, referenceBeanName, referenceBean, injectedType);
        >>>>>>  referenceBean.get();>>>>> ReferenceConfig.get() 引入服务真正的入口 其中的ref是引入的一个服务的ref invoke代理对象
         */
        registerServiceAnnotationBeanPostProcessor(packagesToScan, registry);

        // @since 2.7.6 Register the common beans
        registerCommonBeans(registry);
    }

    /**
     * Registers {@link ServiceAnnotationBeanPostProcessor}
     *
     * @param packagesToScan packages to scan without resolving placeholders
     * @param registry       {@link BeanDefinitionRegistry}
     * @since 2.5.8
     */
    private void registerServiceAnnotationBeanPostProcessor(Set<String> packagesToScan, BeanDefinitionRegistry registry) {

        BeanDefinitionBuilder builder = rootBeanDefinition(ServiceAnnotationBeanPostProcessor.class);
        builder.addConstructorArgValue(packagesToScan);
        builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
        BeanDefinitionReaderUtils.registerWithGeneratedName(beanDefinition, registry);

    }

    private Set<String> getPackagesToScan(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(DubboComponentScan.class.getName()));
        String[] basePackages = attributes.getStringArray("basePackages");
        Class<?>[] basePackageClasses = attributes.getClassArray("basePackageClasses");
        String[] value = attributes.getStringArray("value");
        // Appends value array attributes
        Set<String> packagesToScan = new LinkedHashSet<String>(Arrays.asList(value));
        packagesToScan.addAll(Arrays.asList(basePackages));
        for (Class<?> basePackageClass : basePackageClasses) {
            packagesToScan.add(ClassUtils.getPackageName(basePackageClass));
        }
        if (packagesToScan.isEmpty()) {
            return Collections.singleton(ClassUtils.getPackageName(metadata.getClassName()));
        }
        return packagesToScan;
    }

}

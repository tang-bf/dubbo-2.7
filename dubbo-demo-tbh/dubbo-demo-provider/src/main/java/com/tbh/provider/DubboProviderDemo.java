package com.tbh.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@EnableAutoConfiguration
public class DubboProviderDemo {
    /**
     * https://www.jianshu.com/p/8639e5e9fba6
     * Spring可扩展的XML Schema机制
     * https://www.jianshu.com/p/bd34e6ae06cf  rpc原理
     * @param args
     */
    public static void main(String[] args) {
        SpringApplication.run(DubboProviderDemo.class,args);
    }

//    @Bean
//    public RegistryConfig registryConfig() {
//        RegistryConfig registryConfig = new RegistryConfig();
//        registryConfig.setAddress("zookeeper://127.0.0.1:2181");
//
//        return registryConfig;
//    }
}

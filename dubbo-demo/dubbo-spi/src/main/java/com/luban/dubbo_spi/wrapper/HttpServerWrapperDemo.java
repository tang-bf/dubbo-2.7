package com.luban.dubbo_spi.wrapper;

import com.luban.dubbo_spi.HttpServer;
import org.apache.dubbo.common.extension.ExtensionLoader;

/**
 * *************书山有路勤为径***************
 * 鲁班学院
 * 往期资料加木兰老师  QQ: 2746251334
 * VIP课程加安其拉老师 QQ: 3164703201
 * 讲师：周瑜老师
 * *************学海无涯苦作舟***************
 */
public class HttpServerWrapperDemo {

    // 有多个wrapper类的执行顺序是？按配置文件的顺序来
    public static void main(String[] args) {
        ExtensionLoader<HttpServer> extensionLoader = ExtensionLoader.getExtensionLoader(HttpServer.class);
        HttpServer httpServer = extensionLoader.getExtension("tomcat");

        httpServer.start(null);
    }
}

package com.luban.dubbo_spi.inject;

import com.luban.dubbo_spi.HttpServer;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;

/**
 * *************书山有路勤为径***************
 * 鲁班学院
 * 往期资料加木兰老师  QQ: 2746251334
 * VIP课程加安其拉老师 QQ: 3164703201
 * 讲师：周瑜老师
 * *************学海无涯苦作舟***************
 */
public class HttpServerInjectDemo {

    public static void main(String[] args) {
        ExtensionLoader<HttpServer> extensionLoader =
                ExtensionLoader.getExtensionLoader(HttpServer.class);
        HttpServer tomcatServer = extensionLoader.getExtension("tomcatInject");
        URL url = new URL("p1", "1.2.3.4", 1010, "path1");
        url = url.addParameters("http.server", "tomcat");
        tomcatServer.start(url);
    }
}

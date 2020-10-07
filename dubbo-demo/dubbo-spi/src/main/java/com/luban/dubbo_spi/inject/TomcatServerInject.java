package com.luban.dubbo_spi.inject;

import com.luban.dubbo_spi.HttpServer;
import org.apache.dubbo.common.URL;

/**
 * *************书山有路勤为径***************
 * 鲁班学院
 * 往期资料加木兰老师  QQ: 2746251334
 * VIP课程加安其拉老师 QQ: 3164703201
 * 讲师：周瑜老师
 * *************学海无涯苦作舟***************
 */
public class TomcatServerInject<T> implements HttpServer {

    private HttpServer httpServer;

    // 注入点
    public void setHttpServer(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    @Override
    public void start(URL url) {
        System.out.println("tomcat2");
        httpServer.start(url);
    }

}

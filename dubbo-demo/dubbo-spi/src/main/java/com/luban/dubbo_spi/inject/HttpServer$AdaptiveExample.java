package com.luban.dubbo_spi.inject;

import org.apache.dubbo.common.extension.ExtensionLoader;


public class HttpServer$AdaptiveExample implements com.luban.dubbo_spi.HttpServer {

    public void start(org.apache.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg0;
        String extName = url.getParameter("http.server");

        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.luban.dubbo_spi.HttpServer) name from url(" + url.toString() + ") use keys([http.server])");
        com.luban.dubbo_spi.HttpServer extension = (com.luban.dubbo_spi.HttpServer) ExtensionLoader.getExtensionLoader(com.luban.dubbo_spi.HttpServer.class).getExtension(extName);
        extension.start(arg0);
    }
}
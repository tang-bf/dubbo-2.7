package com.tbh.provider.service;

import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.rpc.service.GenericException;
import org.apache.dubbo.rpc.service.GenericService;

//@Service(interfaceName = "com.tbh.DemoService")
//泛化实现
public class TestService implements GenericService {
    @Override
    public Object $invoke(String s, String[] strings, Object[] objects) throws GenericException {
        return "执行的方法是"+s;
    }
}

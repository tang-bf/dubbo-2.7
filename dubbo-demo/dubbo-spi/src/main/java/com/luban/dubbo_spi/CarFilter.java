package com.luban.dubbo_spi;

import org.apache.dubbo.common.URL;

/**
 * @ProjectName: dubbo-2.7.x
 * @Package: com.luban.dubbo_spi
 * @ClassName: CarFilter
 * @Description:
 * @Author: tbf
 * @CreateDate: 2020-10-07 15:54
 * @UpdateUser: Administrator
 * @UpdateDate: 2020-10-07 15:54
 * @UpdateRemark:
 * @Version: 1.0
 */

public class CarFilter implements  Car {
//    public  User user;
//
//    public User getUser() {
//        return user;
//    }
//
//    public void setUser(User user) {
//        this.user = user;
//    }

    @Override
    public void test(URL url) {
        System.out.println("test");
       // user.testUser(null);
    }
}

package com.luban.dubbo_spi;

import org.apache.dubbo.common.URL;

/**
 * @ProjectName: dubbo-2.7.x
 * @Package: com.luban.dubbo_spi
 * @ClassName: UserFilter
 * @Description:
 * @Author: tbf
 * @CreateDate: 2020-10-07 17:43
 * @UpdateUser: Administrator
 * @UpdateDate: 2020-10-07 17:43
 * @UpdateRemark:
 * @Version: 1.0
 */

public class UserFilter implements User {
    @Override
    public void testUser(URL url) {
        System.out.println("user test");
    }
}

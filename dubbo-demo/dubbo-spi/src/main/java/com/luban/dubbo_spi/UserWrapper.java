package com.luban.dubbo_spi;

import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import org.apache.dubbo.common.URL;

/**
 * @ProjectName: dubbo-2.7.x
 * @Package: com.luban.dubbo_spi
 * @ClassName: UserWrapper
 * @Description:
 * @Author: tbf
 * @CreateDate: 2020-10-07 17:43
 * @UpdateUser: Administrator
 * @UpdateDate: 2020-10-07 17:43
 * @UpdateRemark:
 * @Version: 1.0
 */

public class UserWrapper implements User {
    public  User user;

    public UserWrapper(User user) {
        this.user = user;
    }

    @Override
    public void testUser(URL url) {
        System.out.println("user wrapper");
        user.testUser(null);
    }
}

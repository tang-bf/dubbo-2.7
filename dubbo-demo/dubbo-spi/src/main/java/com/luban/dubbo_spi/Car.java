package com.luban.dubbo_spi;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.SPI;

/**
 * @ProjectName: dubbo-2.7.x
 * @Package: com.luban.dubbo_spi
 * @ClassName: Car
 * @Description:
 * @Author: tbf
 * @CreateDate: 2020-10-07 15:52
 * @UpdateUser: Administrator
 * @UpdateDate: 2020-10-07 15:52
 * @UpdateRemark:
 * @Version: 1.0
 */
@SPI
public interface Car {

    public void test(URL url);
}
